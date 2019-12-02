package io.zeebe.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.ZeebeTestRule;
import io.zeebe.test.util.record.RecordingExporter;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
    "ENV_VARS_URL=http://localhost:8089/config", "ENV_VARS_RELOAD_RATE=0",    
    "ENV_VARS_M2M_BASE_URL:http://localhost:8089/token", "ENV_VARS_M2M_CLIENT_ID:someClientId", "ENV_VARS_M2M_CLIENT_SECRET:someSecret", "ENV_VARS_M2M_AUDIENCE:someAudience"})
public class WorkflowTest {

  @ClassRule 
  public static final ZeebeTestRule TEST_RULE = new ZeebeTestRule();

  @ClassRule 
  public static WireMockRule WIRE_MOCK_RULE = new WireMockRule(8089);

  @BeforeClass
  public static void init() {
    stubFor(get(urlEqualTo("/config")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{}")));
    stubFor(post(urlEqualTo("/token")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{ \"token_type\": \"Bearer\", \"access_token\": \"TOKEN_123_42\" }")));                

    System.setProperty("zeebe.client.broker.contactPoint", TEST_RULE.getClient().getConfiguration().getBrokerContactPoint());
  }

  @Before
  public void resetMock() {
    WIRE_MOCK_RULE.resetRequests();
  }

  @Test
  public void testGetRequest() {

    stubFor(
        get(urlEqualTo("/api"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody("{\"x\":1}")));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api")
                    .zeebeTaskHeader("method", "GET"),
            Collections.emptyMap());

    ZeebeTestRule.assertThat(workflowInstance)
        .isEnded()
        .hasVariable("statusCode", 200)
        .hasVariable("body", Map.of("x", 1));

    WIRE_MOCK_RULE.verify(getRequestedFor(urlEqualTo("/api")));
  }

  @Test
  public void testPostRequest() {

    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(201)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api")
                    .zeebeTaskHeader("method", "POST"),
            Map.of("body", Map.of("x", 1)));

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 201);

    WIRE_MOCK_RULE.verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"x\":1}")));
  }

  @Test
  public void testPutRequest() {

    stubFor(put(urlEqualTo("/api")).willReturn(aResponse().withStatus(200)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api")
                    .zeebeTaskHeader("method", "PUT"),
            Map.of("body", Map.of("x", 1)));

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 200);

    WIRE_MOCK_RULE.verify(
        putRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"x\":1}")));
  }

  @Test
  public void testDeleteRequest() {

    stubFor(delete(urlEqualTo("/api")).willReturn(aResponse().withStatus(200)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api")
                    .zeebeTaskHeader("method", "DELETE"),
            Collections.emptyMap());

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 200);

    WIRE_MOCK_RULE.verify(deleteRequestedFor(urlEqualTo("/api")));
  }

  @Test
  public void testGetNotFoundResponse() {

    stubFor(get(urlEqualTo("/api")).willReturn(aResponse().withStatus(404)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api")
                    .zeebeTaskHeader("statusCodeFailure", "400,401,402,403,405")
                    .zeebeTaskHeader("statusCodeCompletion", "404")
                    .zeebeTaskHeader("method", "GET"),
            Map.of());

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 404);
  }

  @Test
  public void testAuthorizationHeader() {

    stubFor(get(urlEqualTo("/api")).willReturn(aResponse()));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api")
                    .zeebeTaskHeader("method", "GET"),
            Map.of("authorization", "token 123"));

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 200);

    WIRE_MOCK_RULE.verify(
        getRequestedFor(urlEqualTo("/api")).withHeader("Authorization", equalTo("token 123")));
  }  

  @Test
  public void shouldExposeJobKeyIfStatusCode202() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(202)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api")
                    .zeebeTaskHeader("statusCodeCompletion", "200,201,203,204,205,206") // NO 202!!
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"jobKey\":\"{{jobKey}}\"}"));
    
    Record<JobRecordValue> recorderJob = RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
        .getFirst();

    // simulate an async callback
    TEST_RULE.getClient().newCompleteCommand( recorderJob.getKey() ).send().join();

    ZeebeTestRule.assertThat(workflowInstance).isEnded();

    WIRE_MOCK_RULE.verify(
        postRequestedFor(urlEqualTo("/api"))
        .withRequestBody(equalToJson("{\"jobKey\":\"" + recorderJob.getKey() + "\"}")));

  }

  @Test
  public void failOnHttpStatus400() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(400)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api")
                    .zeebeTaskHeader("method", "POST"));
    
    Record<JobRecordValue> recorderJob = RecordingExporter.jobRecords(JobIntent.FAILED)
        .withWorkflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
        .getFirst();
    
    assertNotNull(recorderJob.getValue().getErrorMessage());
    assertTrue("Error message contains status code 400: " + recorderJob.getValue().getErrorMessage(), recorderJob.getValue().getErrorMessage().contains("failed with 400"));     
  }

  @Test
  public void shouldReplacePlaceholdersWithVariables() {

    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api/{{x}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"y\":{{y}}}"),
            Map.of("x", 1, "y", 2));

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 201);

    WIRE_MOCK_RULE.verify(
        postRequestedFor(urlEqualTo("/api/1"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"y\":2}")));
  }
  
  @Test
  public void shouldReplaceLegacyPlaceholders() {
    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api/{{x}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"y\":${y}}"),
            Map.of("x", 1, "y", 2));

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 201);
    WIRE_MOCK_RULE.verify(postRequestedFor(urlEqualTo("/api/1"))
            .withRequestBody(equalToJson("{\"y\":2}")));
  }  

  @Test
  public void shouldReplacePlaceholdersWithContext() {

    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api/{{jobKey}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"instanceKey\":{{workflowInstanceKey}}}"),
            Map.of());

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 201);

    Record<JobRecordValue> job = RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
        .getFirst();

    WIRE_MOCK_RULE.verify(
        postRequestedFor(urlEqualTo("/api/" + job.getKey()))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(
                equalToJson(
                    "{\"instanceKey\":" + workflowInstance.getWorkflowInstanceKey() + "}")));
  }

  
  // Can't currently test this, as we can't change properties after the spring container once started, so not for a single test
  // and if moved to a seperate Test the current dynamics of the ZeebeRule do not work properly and I could not investigate that further at the moment
//  @Test
//  public void shouldReplacePlaceholdersWithConfigVariablesWithoutM2MToken() {
//
//    stubFor(
//        get(urlEqualTo("/config"))
//            .willReturn(
//                aResponse()
//                    .withHeader("Content-Type", "application/json")
//                    .withBody("{ \"x\":1,\"y\":2}")));
//
//    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));
//
//    final WorkflowInstanceEvent workflowInstance =
//        createInstance(
//            serviceTask ->
//                serviceTask
//                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api/{{x}}")
//                    .zeebeTaskHeader("method", "POST")
//                    .zeebeTaskHeader("body", "{\"y\":{{y}}}"),
//            Map.of());
//
//    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 201);
//
//    WIRE_MOCK_RULE.verify(
//        postRequestedFor(urlEqualTo("/api/1"))
//            .withHeader("Content-Type", equalTo("application/json"))
//            .withRequestBody(equalToJson("{\"y\":2}")));
//  }

  @Test
  public void shouldReplacePlaceholdersWithConfigVariablesWithM2MToken() {
//    WIRE_MOCK_RULE.resetAll(); // forget the defaults set in the static initializer for this test
//    stubFor(post(urlEqualTo("/token")).willReturn(aResponse()
//                    .withHeader("Content-Type", "application/json")
//                    .withBody("{ \"token_type\": \"Bearer\", \"access_token\": \"TOKEN_123_42\" }")));                
    stubFor(get(urlEqualTo("/config")).willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"x\":1,\"y\":2}")));    
    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api/{{x}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"y\":{{y}}}"),
            Map.of());

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 201);
    
    // I can't check if the /token was called here, as the token is typically already cached from other test runs
    // and I can't move this to a seperate test class because of limitations in the test support from zeebe at the moment
    // So I check if the Authorization Header is set correctly instead
    WIRE_MOCK_RULE.verify(getRequestedFor(urlEqualTo("/config")).withHeader("Authorization", equalTo("Bearer TOKEN_123_42")));
    WIRE_MOCK_RULE.verify(postRequestedFor(urlEqualTo("/api/1"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"y\":2}")));
  }  
  
  @Test
  public void shouldReplacePlaceholdersWithConfigVariablesWithM2MTokenRefresh() {
    // forbidden, which happens because of outdated tokens
    stubFor(get(urlMatching("/config")).inScenario("RefreshTokenScenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withStatus(403))
        .willSetStateTo("ALLOWED"));
    // so a refresh should happen (already configured in the setup)
    // then it works
    stubFor(get(urlEqualTo("/config")).inScenario("RefreshTokenScenario")
        .whenScenarioStateIs("ALLOWED")
        .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{ \"x\":1,\"y\":2}")));
    
    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final WorkflowInstanceEvent workflowInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", WIRE_MOCK_RULE.baseUrl() + "/api/{{x}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"y\":{{y}}}"),
            Map.of());

    ZeebeTestRule.assertThat(workflowInstance).isEnded().hasVariable("statusCode", 201);
    
    WIRE_MOCK_RULE.verify(postRequestedFor(urlEqualTo("/token"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"client_id\":\"someClientId\", \"client_secret\": \"someSecret\",  \"audience\": \"someAudience\",  \"grant_type\": \"client_credentials\"}")));

    WIRE_MOCK_RULE.verify(postRequestedFor(urlEqualTo("/api/1"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"y\":2}")));
  }    

  private WorkflowInstanceEvent createInstance(final Consumer<ServiceTaskBuilder> taskCustomizer) {
    return createInstance(taskCustomizer, new HashMap<>());
  }

  private WorkflowInstanceEvent createInstance(
      final Consumer<ServiceTaskBuilder> taskCustomizer, Map<String, Object> variables) {

    ServiceTaskBuilder processBuilder = Bpmn.createExecutableProcess("process")
        .startEvent()
        .serviceTask("task", t -> t.zeebeTaskType("http"));

    taskCustomizer.accept(processBuilder);
    processBuilder.endEvent();

    TEST_RULE
        .getClient()
        .newDeployCommand()
        .addWorkflowModel(processBuilder.done(), "process.bpmn")
        .send()
        .join();

    final WorkflowInstanceEvent workflowInstance =
        TEST_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    return workflowInstance;
  }
}
