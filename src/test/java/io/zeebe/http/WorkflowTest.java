package io.zeebe.http;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.ZeebeTestRule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
  public void shouldExecuteGetRequest() {

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
  public void shouldExecuteGetRequestWithQueryParametersFromContext() {

    final BpmnModelInstance workflow =
            Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask(
                            "task",
                            t ->
                                    t.zeebeTaskType("http")
                                            .zeebeTaskHeader("url", "https://jsonplaceholder.typicode.com/comments?postId=$context.postQueryParams.postId")
                                            .zeebeTaskHeader("method", "GET"))
                    .done();

    final Map<String, Object> postQueryParams = new HashMap<>();
    postQueryParams.put("postId", 1);
    postQueryParams.put("id", 1);

    Map<String, Object> context = new HashMap<>();
    context.put("postQueryParams", postQueryParams);

    final WorkflowInstanceEvent workflowInstance =
            deployAndCreateInstance(workflow, Collections.singletonMap("context", context));

    List<Map<String, Object>> posts = new ArrayList<>();

    posts.add(createPost(1,1,
            "id labore ex et quam laborum",
            "Eliseo@gardner.biz",
            "laudantium enim quasi est quidem magnam voluptate ipsam eos\ntempora quo necessitatibus\ndolor quam autem quasi\nreiciendis et nam sapiente accusantium"));

     posts.add(createPost(2,1,
            "quo vero reiciendis velit similique earum",
            "Jayne_Kuhic@sydney.com",
            "est natus enim nihil est dolore omnis voluptatem numquam\net omnis occaecati quod ullam at\nvoluptatem error expedita pariatur\nnihil sint nostrum voluptatem reiciendis et"));

    posts.add(createPost(3,1,
            "odio adipisci rerum aut animi",
            "Nikita@garfield.biz",
            "quia molestiae reprehenderit quasi aspernatur\naut expedita occaecati aliquam eveniet laudantium\nomnis quibusdam delectus saepe quia accusamus maiores nam est\ncum et ducimus et vero voluptates excepturi deleniti ratione"));

     posts.add(createPost(4,1,
            "alias odio sit",
            "Lew@alysha.tv",
            "non et atque\noccaecati deserunt quas accusantium unde odit nobis qui voluptatem\nquia voluptas consequuntur itaque dolor\net qui rerum deleniti ut occaecati"));

    posts.add(createPost(5,1,
            "vero eaque aliquid doloribus et culpa",
            "Hayden@althea.biz",
            "harum non quasi et ratione\ntempore iure ex voluptates in ratione\nharum architecto fugit inventore cupiditate\nvoluptates magni quo et"));

    ZeebeTestRule.assertThat(workflowInstance)
            .isEnded()
            .hasVariable("statusCode", 200)
            .hasVariable("body", posts);
  }

  @Test
  public void shouldExecuteGetRequestWithQueryParameters() {

    final BpmnModelInstance workflow =
            Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask(
                            "task",
                            t ->
                                    t.zeebeTaskType("http")
                                            .zeebeTaskHeader("url", "https://jsonplaceholder.typicode.com/comments?postId=1")
                                            .zeebeTaskHeader("method", "GET"))
                    .done();

    final WorkflowInstanceEvent workflowInstance =
            deployAndCreateInstance(workflow, Collections.emptyMap());

    List<Map<String, Object>> posts = new ArrayList<>();

    posts.add(createPost(1,1,
            "id labore ex et quam laborum",
            "Eliseo@gardner.biz",
            "laudantium enim quasi est quidem magnam voluptate ipsam eos\ntempora quo necessitatibus\ndolor quam autem quasi\nreiciendis et nam sapiente accusantium"));

    posts.add(createPost(2,1,
            "quo vero reiciendis velit similique earum",
            "Jayne_Kuhic@sydney.com",
            "est natus enim nihil est dolore omnis voluptatem numquam\net omnis occaecati quod ullam at\nvoluptatem error expedita pariatur\nnihil sint nostrum voluptatem reiciendis et"));

    posts.add(createPost(3,1,
            "odio adipisci rerum aut animi",
            "Nikita@garfield.biz",
            "quia molestiae reprehenderit quasi aspernatur\naut expedita occaecati aliquam eveniet laudantium\nomnis quibusdam delectus saepe quia accusamus maiores nam est\ncum et ducimus et vero voluptates excepturi deleniti ratione"));

    posts.add(createPost(4,1,
            "alias odio sit",
            "Lew@alysha.tv",
            "non et atque\noccaecati deserunt quas accusantium unde odit nobis qui voluptatem\nquia voluptas consequuntur itaque dolor\net qui rerum deleniti ut occaecati"));

    posts.add(createPost(5,1,
            "vero eaque aliquid doloribus et culpa",
            "Hayden@althea.biz",
            "harum non quasi et ratione\ntempore iure ex voluptates in ratione\nharum architecto fugit inventore cupiditate\nvoluptates magni quo et"));

    ZeebeTestRule.assertThat(workflowInstance)
            .isEnded()
            .hasVariable("statusCode", 200)
            .hasVariable("body", posts);
  }

  @Test
  public void shouldExecutePostRequest() {

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
  public void shouldExecutePostRequestUsingContextVariableAsBody() {

    final BpmnModelInstance workflow =
            Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask(
                            "task",
                            t ->
                                    t.zeebeTaskType("http")
                                            .zeebeTaskHeader("url", "https://jsonplaceholder.typicode.com/todos/")
                                            .zeebeTaskHeader("method", "POST")
                                            .zeebeTaskHeader("postBodyVariableName", "context.todo"))

                    .done();

    final Map<String, Object> todo = new HashMap<>();
    todo.put("userId", 99);
    todo.put("title", "write good code");
    todo.put("completed", false);

    Map<String, Object> context = new HashMap<>();
    context.put("todo", todo);

    final WorkflowInstanceEvent workflowInstance =
            deployAndCreateInstance(workflow, Collections.singletonMap("context", context));

    final Map<String, Object> expectedResponse = new HashMap<>(todo);
    expectedResponse.put("id", 201);

    ZeebeTestRule.assertThat(workflowInstance)
            .isEnded()
            .hasVariable("statusCode", 201)
            .hasVariable("body", expectedResponse);
  }



  @Test
  public void shouldExecutePutRequest() {

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
  public void shouldExecuteDeleteRequest() {

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
  public void shouldThrowNotFoundResponseOnGetIfResourceNotFound() {

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
  public void shouldUseAuthorization() {

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

  private HashMap<String, Object> createPost(int id, int postId, String name, String email, String body){

    HashMap<String, Object> post = new HashMap<>();

    post.put("postId", postId);
    post.put("id", id);
    post.put("name", name);
    post.put("email", email);
    post.put("body", body);

    return post;
  }
}
