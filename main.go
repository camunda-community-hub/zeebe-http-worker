package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/zeebe-io/zeebe/clients/go/entities"
	"github.com/zeebe-io/zeebe/clients/go/worker"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"os/signal"
	"strings"

	"github.com/zeebe-io/zeebe/clients/go/zbc"
)

func main() {
	brokerAddr := os.Getenv("BROKER")
	if brokerAddr == "" {
		brokerAddr = "0.0.0.0:26500"
	}

	zbClient, err := zbc.NewZBClient(brokerAddr)
	if err != nil {
		panic("Failed to connect to " + brokerAddr)
	}

	jobWorker := zbClient.NewJobWorker().JobType("http").Handler(handleJob).Name("http-go").BufferSize(32).Open()

	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)
	go func() {
		<-c
		fmt.Println("Closing job worker")
		jobWorker.Close()
		jobWorker.AwaitClose()
		fmt.Println("Closed job worker")
		os.Exit(0)
	}()

	jobWorker.AwaitClose()
}

func handleJob(client worker.JobClient, job entities.Job) {
	url, err := getParameter(job, "url")
	if err != nil {
		failJob(client, job, fmt.Sprintf("Failed to recieve required parameter 'url'", err))
		return
	}

	if url == nil {
		failJob(client, job, "Missing required parameter 'url'")
		return
	}

	method, err := getParameter(job, "method")
	if err != nil || method == nil {
		method = "GET"
	}

	var reqBody io.Reader
	body, _ := getParameter(job, "body")
	if body != nil {
		jsonDocument, err := json.Marshal(body)
		if err != nil {
			failJob(client, job, fmt.Sprintf("Failed to marshal body parameter 'body' as json", err))
			return
		}
		reqBody = bytes.NewReader(jsonDocument)
	}

	statusCode, resBody, err := request(url.(string), method.(string), reqBody)
	if err != nil {
		failJob(client, job, fmt.Sprintf("Failed to send request to url", url, err))
		return
	}

	result := make(map[string]interface{})
	result["statusCode"] = statusCode
	result["body"] = resBody

	cmd, err := client.NewCompleteJobCommand().JobKey(job.Key).PayloadFromMap(result)
	if err != nil {
		failJob(client, job, fmt.Sprintf("Failed to set payload for complete job command", err))
		return
	}

	cmd.Send()
	fmt.Println("Completed job with key", job.Key)
}

func getParameter(job entities.Job, param string) (interface{}, error) {
	headers, err := job.GetCustomHeadersAsMap()
	if err != nil {
		return nil, err
	}

	value := headers[param]
	if value != nil {
		return value, nil
	}

	payload, err := job.GetPayloadAsMap()
	if err != nil {
		return nil, err
	}

	return payload[param], nil
}

func request(url string, method string, body io.Reader) (int, interface{}, error) {
	req, _ := http.NewRequest(method, url, body)
	req.Header.Set("Content-Type", "application/json")

	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return 0, nil, err
	}

	statusCode := res.StatusCode

	defer res.Body.Close()
	resBody, err := ioutil.ReadAll(res.Body)
	if err != nil {
		return 0, nil, err
	}

	if hasContentType(*res, "application/json") {
		var jsonDocument interface{}
		err := json.Unmarshal(resBody, &jsonDocument)
		if err != nil {
			return statusCode, resBody, err
		}

		return statusCode, jsonDocument, nil
	}

	return statusCode, string(resBody), nil
}

func hasContentType(res http.Response, cType string) bool {
	contentType := res.Header["Content-Type"]

	for _, c := range contentType {
		if strings.Contains(c, cType) {
			return true
		}
	}
	return false
}

func failJob(client worker.JobClient, job entities.Job, error string) {
	fmt.Println("Failed to complete job", job.Key, ":", error)
	client.NewFailJobCommand().JobKey(job.Key).Retries(job.Retries - 1).ErrorMessage(error).Send()
}
