package io.zeebe.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.value.MessageRecordValue;
import io.zeebe.test.ZeebeTestRule;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowTest {

  @Rule public final ZeebeTestRule testRule = new ZeebeTestRule();

  private ZeebeClient client;
  private ZeebeHttpWorker worker;

  @Before
  public void init() {
    client = testRule.getClient();

    worker = new ZeebeHttpWorker(client.getConfiguration().getBrokerContactPoint());
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
        .hasVariables("statusCode", 200)
        .hasVariables("body", expectedResponse);
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
        .hasVariables("statusCode", 201)
        .hasVariables("body", expectedResponse);
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
        .hasVariables("statusCode", 200)
        .hasVariables("body", requestBody);
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

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariables("statusCode", 200);
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

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariables("statusCode", 404);
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

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariables("statusCode", 401);
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
