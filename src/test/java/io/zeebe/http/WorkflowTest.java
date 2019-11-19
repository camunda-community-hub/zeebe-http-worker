package io.zeebe.http;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.ZeebeTestRule;

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
                        .zeebeTaskHeader("url", "${myUrl}")
                        .zeebeTaskHeader("method", "${myMethod}"))
            .done();

    HashMap<String, Object> variables = new HashMap<>();
    variables.put("myUrl", "https://jsonplaceholder.typicode.com/todos/1");
    variables.put("myMethod", "GET");
    final WorkflowInstanceEvent workflowInstance =
        deployAndCreateInstance(workflow, variables);

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
  
  public static class ConfigServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().println("{ \"baseUrl\": \"https://jsonplaceholder.typicode.com\"}");
    }
  }

  @Test
  public void testGetRequestWithExpressionAndConfigJson() throws Exception {
    Server server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(9988);
    server.setConnectors(new Connector[] { connector });
    ServletHandler servletHandler = new ServletHandler();
    server.setHandler(servletHandler);
    servletHandler.addServletWithMapping(ConfigServlet.class, "/config");    
    server.start();
    EnvironmentVariableLoader.MOCK_ENV_VARS_URL = "http://localhost:9988/config";
    try {
      final BpmnModelInstance workflow =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .serviceTask(
                  "task",
                  t ->
                      t.zeebeTaskType("http")
                          .zeebeTaskHeader("url", "${baseUrl}/todos/${someNumber}")
                          .zeebeTaskHeader("method", "${myMethod}"))
              .done();
  
      HashMap<String, Object> variables = new HashMap<>();
      variables.put("someNumber", "1");
      variables.put("myMethod", "GET");
      final WorkflowInstanceEvent workflowInstance =
          deployAndCreateInstance(workflow, variables);
  
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
    finally {
      server.stop();
    }
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
