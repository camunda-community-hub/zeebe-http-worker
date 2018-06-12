package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"os/signal"
	"strings"

	"github.com/zeebe-io/zbc-go/zbc"
	"github.com/zeebe-io/zbc-go/zbc/models/zbmsgpack"
	"github.com/zeebe-io/zbc-go/zbc/models/zbsubscriptions"
	"github.com/zeebe-io/zbc-go/zbc/services/zbsubscribe"
)

func main() {
	brokerAddr := os.Getenv("BROKER")
	if brokerAddr == "" {
		brokerAddr = "0.0.0.0:51015"
	}

	topicName := os.Getenv("TOPIC")
	if topicName == "" {
		topicName = "default-topic"
	}

	zbClient, err := zbc.NewClient(brokerAddr)
	if err != nil {
		panic("Failed to connect to " + brokerAddr)
	}

	subscription, err := zbClient.JobSubscription(topicName, "http-go", "http", 1000, 32, handleJob)
	if err != nil {
		panic("Unable to open subscription")
	}

	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)
	go func() {
		<-c
		err := subscription.Close()
		if err != nil {
			panic("Failed to close subscription")
		}

		fmt.Println("Closed subscription")
		os.Exit(0)
	}()

	subscription.Start()
}

func handleJob(client zbsubscribe.ZeebeAPI, event *zbsubscriptions.SubscriptionEvent) {
	job, _ := event.GetJob()

	url := getParameter(job, "url")
	if url == nil {
		fmt.Println("Missing required parameter 'URL'")
		job.Retries--
		client.FailJob(event)
		return
	}

	method := getParameter(job, "method")
	if method == nil {
		method = "GET"
	}

	var reqBody io.Reader
	body := getParameter(job, "body")
	if body != nil {
		jsonDocument, err := json.Marshal(body)
		if err != nil {
			fmt.Println(err)
			job.Retries--
			client.FailJob(event)
			return
		}
		reqBody = bytes.NewReader(jsonDocument)
	}

	statusCode, resBody, err := request(url.(string), method.(string), reqBody)
	if err != nil {
		fmt.Println(err)
		job.Retries--
		client.FailJob(event)
		return
	}

	result := make(map[string]interface{})
	result["statusCode"] = statusCode
	result["body"] = resBody

	job.SetPayload(result)
	client.CompleteJob(event)
}

func getParameter(job *zbmsgpack.Job, param string) interface{} {
	value := job.CustomHeader[param]
	if value != nil {
		return value
	}

	payload, _ := job.GetPayload()
	value = (*payload)[param]
	return value
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
