package io.zeebe.http;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.http.config.ConfigProvider;
import io.zeebe.http.config.HttpConfigProvider;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.ZeebeTestRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class WorkflowTest {

  @Rule public final ZeebeTestRule testRule = new ZeebeTestRule();

  @Rule public WireMockRule wireMockRule = new WireMockRule(8089);

  private ZeebeClient client;
  private ZeebeHttpWorker worker;

  @Before
  public void init() {
    client = testRule.getClient();

    final ConfigProvider configProvider =
        new HttpConfigProvider(wireMockRule.baseUrl() + "/config", Duration.ofSeconds(15));

    stubFor(
        get(urlEqualTo("/config"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{}")));

    worker = new ZeebeHttpWorker(client.getConfiguration().getBrokerContactPoint(), configProvider);
    worker.start();
  }

  @After
  public void cleanUp() {
    worker.stop();
  }

  @Test
  public void testGetRequest() {

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("http")
                        .zeebeTaskHeader("url", "https://jsonplaceholder.typicode.com/todos/1")
                        .zeebeTaskHeader("method", "GET"))
            .done();

    final WorkflowInstanceEvent workflowInstance =
        deployAndCreateInstance(workflow, Collections.emptyMap());

    final Map<String, Object> expectedResponse = new HashMap<>();
    expectedResponse.put("userId", 1);
    expectedResponse.put("id", 1);
    expectedResponse.put("title", "delectus aut autem");
    expectedResponse.put("completed", false);

    ZeebeTestRule.assertThat(workflowInstance)
        .isEnded()
        .hasVariable("statusCode", 200)
        .hasVariable("body", expectedResponse);
  }

  @Test
  public void testGetRequestWithExpressions() throws InterruptedException {

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("http")
                        .zeebeTaskHeader("url", "https://jsonplaceholder.typicode.com/todos/{{id}}")
                        .zeebeTaskHeader("method", "PUT")
                        .zeebeTaskHeader(
                            "body",
                            "{\"id\":{{id}}, \"userId\":1, \"title\":\"{{title}}\", \"completed\":true}"))
            .done();

    HashMap<String, Object> variables = new HashMap<>();
    variables.put("id", "1");
    variables.put("title", "find happiness");

    final Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("userId", 1);
    requestBody.put("id", 1);
    requestBody.put("title", "find happiness");
    requestBody.put("completed", true);

    final WorkflowInstanceEvent workflowInstance = deployAndCreateInstance(workflow, variables);

    ZeebeTestRule.assertThat(workflowInstance)
        .isEnded()
        .hasVariable("statusCode", 200)
        .hasVariable("body", requestBody);
  }

  @Test
  public void testGetRequestWithExpressionAndConfigJson() throws Exception {

    stubFor(
        get(urlEqualTo("/config"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"baseUrl\": \"https://jsonplaceholder.typicode.com\"}")));

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("http")
                        .zeebeTaskHeader("url", "{{baseUrl}}/todos/{{someNumber}}")
                        .zeebeTaskHeader("method", "{{myMethod}}"))
            .done();

    HashMap<String, Object> variables = new HashMap<>();
    variables.put("someNumber", "1");
    variables.put("myMethod", "GET");
    final WorkflowInstanceEvent workflowInstance = deployAndCreateInstance(workflow, variables);

    final Map<String, Object> expectedResponse = new HashMap<>();
    expectedResponse.put("userId", 1);
    expectedResponse.put("id", 1);
    expectedResponse.put("title", "delectus aut autem");
    expectedResponse.put("completed", false);

    ZeebeTestRule.assertThat(workflowInstance)
        .isEnded()
        .hasVariable("statusCode", 200)
        .hasVariable("body", expectedResponse);
  }

  @Test
  public void testPostRequest() {

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("http")
                        .zeebeTaskHeader("url", "https://jsonplaceholder.typicode.com/todos/")
                        .zeebeTaskHeader("method", "POST"))
            .done();

    final Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("userId", 99);
    requestBody.put("title", "write good code");
    requestBody.put("completed", false);

    final WorkflowInstanceEvent workflowInstance =
        deployAndCreateInstance(workflow, Collections.singletonMap("body", requestBody));

    final Map<String, Object> expectedResponse = new HashMap<>(requestBody);
    expectedResponse.put("id", 201);

    ZeebeTestRule.assertThat(workflowInstance)
        .isEnded()
        .hasVariable("statusCode", 201)
        .hasVariable("body", expectedResponse);
  }

  @Test
  public void testPutRequest() {

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("http")
                        .zeebeTaskHeader("url", "https://jsonplaceholder.typicode.com/todos/1")
                        .zeebeTaskHeader("method", "PUT"))
            .done();

    final Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("userId", 1);
    requestBody.put("id", 1);
    requestBody.put("title", "find happiness");
    requestBody.put("completed", true);

    final WorkflowInstanceEvent workflowInstance =
        deployAndCreateInstance(workflow, Collections.singletonMap("body", requestBody));

    ZeebeTestRule.assertThat(workflowInstance)
        .isEnded()
        .hasVariable("statusCode", 200)
        .hasVariable("body", requestBody);
  }

  @Test
  public void testDeleteRequest() {

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("http")
                        .zeebeTaskHeader("url", "https://jsonplaceholder.typicode.com/todos/1")
                        .zeebeTaskHeader("method", "DELETE"))
            .done();

    final WorkflowInstanceEvent workflowInstance =
        deployAndCreateInstance(workflow, Collections.emptyMap());

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 200);
  }

  @Test
  public void testGetNotFoundResponse() {

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("http")
                        .zeebeTaskHeader("url", "https://jsonplaceholder.typicode.com/todos/999")
                        .zeebeTaskHeader("method", "GET"))
            .done();

    final WorkflowInstanceEvent workflowInstance =
        deployAndCreateInstance(workflow, Collections.emptyMap());

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 404);
  }

  @Test
  public void testAuthorization() {

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "stargazers-check",
                t ->
                    t.zeebeTaskType("http")
                        .zeebeTaskHeader(
                            "url",
                            "https://api.github.com/user/starred/zeebe-io/zeebe-http-worker"))
            .exclusiveGateway()
            .defaultFlow()
            .endEvent()
            .moveToLastGateway()
            .condition("statusCode == 404")
            .serviceTask(
                "give-a-start",
                t ->
                    t.zeebeTaskType("http")
                        .zeebeTaskHeader(
                            "url", "https://api.github.com/user/starred/zeebe-io/zeebe-http-worker")
                        .zeebeTaskHeader("method", "PUT"))
            .endEvent()
            .done();

    final WorkflowInstanceEvent workflowInstance =
        deployAndCreateInstance(workflow, Collections.singletonMap("authorization", "token ABC"));

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 401);
  }

  private WorkflowInstanceEvent deployAndCreateInstance(
      final BpmnModelInstance workflow, Map<String, Object> variables) {
    client.newDeployCommand().addWorkflowModel(workflow, "process.bpmn").send().join();

    final WorkflowInstanceEvent workflowInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .variables(variables)
            .send()
            .join();
    return workflowInstance;
  }
}
