package io.zeebe.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.process.test.api.RecordStreamSource;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.extension.testcontainer.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;
import io.camunda.zeebe.process.test.filters.StreamFilter;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import io.camunda.zeebe.spring.test.ZeebeTestThreadSupport;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@WireMockTest(httpPort = 8089)
@SpringBootTest(
    properties = {
        "ENV_VARS_URL=http://localhost:8089/config",
        "ENV_VARS_RELOAD_RATE=0",
        "ENV_VARS_M2M_BASE_URL:http://localhost:8089/token",
        "ENV_VARS_M2M_CLIENT_ID:someClientId",
        "ENV_VARS_M2M_CLIENT_SECRET:someSecret",
        "ENV_VARS_M2M_AUDIENCE:someAudience"
    })
@ZeebeSpringTest
public class ProcessIntegrationTest {

  @Autowired
  private ZeebeClient client;

  @Autowired
  private ZeebeTestEngine zeebeTestEngine;

  @BeforeEach
  public void configureApiMock(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        get(urlEqualTo("/config"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("[]")));
    stubFor(
        post(urlEqualTo("/token"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{ \"token_type\": \"Bearer\", \"access_token\": \"TOKEN_123_42\" }")));

  }

  @Test
  public void testGetRequest(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(
        get(urlEqualTo("/api"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody("{\"x\":1}")));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "GET"),
            Collections.emptyMap());

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance)
        .isCompleted()
        .hasVariableWithValue("statusCode", 200)
        .hasVariableWithValue("body", Map.of("x", 1));

    verify(getRequestedFor(urlEqualTo("/api")));
  }

  @Test
  public void testGetAcceptPlainTextResponse(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(
        get(urlEqualTo("/api"))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/plain")
                    .withBody("This is text")));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "GET")
                    .zeebeTaskHeader("accept", "text/plain"),
            Collections.emptyMap());

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance)
        .isCompleted()
        .hasVariableWithValue("statusCode", 200)
        .hasVariableWithValue("body", "This is text");

    verify(getRequestedFor(urlEqualTo("/api"))
        .withHeader("Accept", equalTo("text/plain")));
  }

  @Test
  public void testPostContentTypePlainText(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(aResponse().withStatus(200)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("contentType", "text/plain"),
            Map.of("body", "This is text"));

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance)
        .isCompleted()
        .hasVariableWithValue("statusCode", 200);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("text/plain"))
            .withRequestBody(equalTo("This is text")));
  }

  @Test
  public void testPostRequest(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(201)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "POST"),
            Map.of("body", Map.of("x", 1)));

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 201);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"x\":1}")));
  }

  @Test
  public void testPutRequest(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(put(urlEqualTo("/api")).willReturn(aResponse().withStatus(200)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "PUT"),
            Map.of("body", Map.of("x", 1)));

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 200);

    verify(
        putRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"x\":1}")));
  }

  @Test
  public void testHeaderNamesCanBeUpperAsWell(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(put(urlEqualTo("/api")).willReturn(aResponse().withStatus(200)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("URL", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("METHOD", "PUT")
                    .zeebeTaskHeader("ACCEPT", "text/plain")
                    .zeebeTaskHeader("CONTENTTYPE", "application/json")
                    .zeebeTaskHeader("AUTHORIZATION", "secret"),
            Map.of("body", Map.of("x", 1)));

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 200);

    verify(
        putRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Authorization", equalTo("secret"))
            .withHeader("Accept", equalTo("text/plain"))
            .withRequestBody(equalToJson("{\"x\":1}")));
  }

  @Test
  public void testDeleteRequest(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(delete(urlEqualTo("/api")).willReturn(aResponse().withStatus(200)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "DELETE"),
            Collections.emptyMap());

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 200);

    verify(deleteRequestedFor(urlEqualTo("/api")));
  }

  @Test
  public void testGetNotFoundResponse(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(get(urlEqualTo("/api")).willReturn(aResponse().withStatus(404)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("statusCodeFailure", "400,401,402,403,405")
                    .zeebeTaskHeader("statusCodeCompletion", "404")
                    .zeebeTaskHeader("method", "GET"),
            Map.of());

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 404);
  }

  @Test
  public void testGetRequestDelayedResponse(WireMockRuntimeInfo wmRuntimeInfo)
      throws InterruptedException {
    long originalResponseTimeout = HttpJobHandler.RESPONSE_TIMEOUT_VALUE;
    try {
      HttpJobHandler.RESPONSE_TIMEOUT_VALUE = 2; // 2 seconds

      stubFor(
          get(urlEqualTo("/api"))
              .inScenario("DelayedResponseLeadToTimeoutScenario")
              .whenScenarioStateIs(Scenario.STARTED)
              .willReturn(
                  aResponse()
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"x\":1}")
                      .withFixedDelay(3 * 1000))
              .willSetStateTo("WORKS_NOW"));
      // second time it works
      stubFor(
          get(urlEqualTo("/api"))
              .inScenario("DelayedResponseLeadToTimeoutScenario")
              .whenScenarioStateIs("WORKS_NOW")
              .willReturn(
                  aResponse()
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"x\":1}")));

      final var processInstance =
          createInstance(
              serviceTask ->
                  serviceTask
                      .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                      .zeebeTaskHeader("method", "GET")
                      .zeebeJobRetries("3"),
              Collections.emptyMap());

      // TODO: Think about a better way of doing this :-)
      Thread.sleep(3 * 1000);

      ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

      BpmnAssert.assertThat(processInstance).isCompleted();

      verify(2, getRequestedFor(urlEqualTo("/api")));
    } finally {
      HttpJobHandler.RESPONSE_TIMEOUT_VALUE = originalResponseTimeout;
    }
  }

  @Test
  public void testAuthorizationHeader(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(get(urlEqualTo("/api")).willReturn(aResponse()));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "GET"),
            Map.of("authorization", "token 123"));

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 200);

    verify(
        getRequestedFor(urlEqualTo("/api")).withHeader("Authorization", equalTo("token 123")));
  }

  @Test
  public void testCustomHeader(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(get(urlEqualTo("/api")).willReturn(aResponse()));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "GET"),
            Map.of("header-X-API-Key", "top-secret"));

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 200);

    verify(
        getRequestedFor(urlEqualTo("/api")).withHeader("X-API-Key", equalTo("top-secret")));
  }

  @Test
  public void shouldExposeJobKeyIfStatusCode202(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(202)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("statusCodeCompletion", "200,201,203,204,205,206") // NO 202!!
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"jobKey\":\"{{jobKey}}\"}"));

    Awaitility.await().ignoreExceptions().untilAsserted(() ->
        Assertions.assertThat(StreamFilter.jobRecords(
                RecordStream.of(zeebeTestEngine.getRecordStreamSource()))
            .withIntent(JobIntent.CREATED)
            .stream().filter(
                r -> r.getValue().getProcessInstanceKey()
                    == processInstance.getProcessInstanceKey())
            .findFirst()).isPresent()
    );

    final var recorderJob = StreamFilter.jobRecords(
            RecordStream.of(zeebeTestEngine.getRecordStreamSource()))
        .withIntent(JobIntent.CREATED)
        .stream().filter(
            r -> r.getValue().getProcessInstanceKey() == processInstance.getProcessInstanceKey())
        .findFirst()
        .orElseThrow();

    Awaitility.await().untilAsserted(() -> verify(
        postRequestedFor(urlEqualTo("/api"))
            .withRequestBody(equalToJson("{\"jobKey\":\"" + recorderJob.getKey() + "\"}"))));

    // simulate an async callback
    client.newCompleteCommand(recorderJob.getKey()).send().join();

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted();
  }

  @Test
  public void failOnHttpStatus400(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(400)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "POST"));

    Awaitility.await().ignoreExceptions().untilAsserted(() -> {
      final var recorderJob =
          StreamFilter.jobRecords(RecordStream.of(zeebeTestEngine.getRecordStreamSource()))
              .withIntent(JobIntent.FAILED)
              .stream().filter(
                  r -> r.getValue().getProcessInstanceKey() == processInstance.getProcessInstanceKey())
              .findFirst().orElseThrow();

      Assertions.assertThat(recorderJob.getValue().getErrorMessage()).isNotNull()
          .contains("failed with 400");
    });
  }

  @Test
  public void failOnHttpStatus500(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(500)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "POST"));

    Awaitility.await().ignoreExceptions().untilAsserted(() -> {
      final var recorderJob =
          StreamFilter.jobRecords(RecordStream.of(zeebeTestEngine.getRecordStreamSource()))
              .withIntent(JobIntent.FAILED)
              .stream().filter(
                  r -> r.getValue().getProcessInstanceKey() == processInstance.getProcessInstanceKey())
              .findFirst().orElseThrow();

      Assertions.assertThat(recorderJob.getValue().getErrorMessage()).isNotNull()
          .contains("failed with 500");
    });
  }

  @Test
  public void throwErrorCodeWithMessageOnFailure(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody(
                        "{\"error\":{\"code\":\"some-code\",\"message\":\"some message\"}}")));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("errorCodePath", "error.code")
                    .zeebeTaskHeader("errorMessagePath", "error.message")
                    .zeebeTaskHeader("method", "POST"));

    Awaitility.await().ignoreExceptions().untilAsserted(() -> {
      final var recorderJob =
          StreamFilter.jobRecords(RecordStream.of(zeebeTestEngine.getRecordStreamSource()))
              .withIntent(JobIntent.ERROR_THROWN)
              .stream().filter(
                  r -> r.getValue().getProcessInstanceKey() == processInstance.getProcessInstanceKey())
              .findFirst().orElseThrow();

      Assertions.assertThat(recorderJob.getValue().getErrorCode()).isNotNull()
          .isEqualTo("some-code");
      Assertions.assertThat(recorderJob.getValue().getErrorMessage()).isNotNull()
          .isEqualTo("some message");
    });
  }

  @Test
  public void throwErrorCodeWithEmptyMessageOnFailure(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse().withStatus(400).withBody("{\"error\":{\"code\":\"some-code\"}}")));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("errorCodePath", "error.code")
                    .zeebeTaskHeader("errorMessagePath", "error.message")
                    .zeebeTaskHeader("method", "POST"));

    Awaitility.await().ignoreExceptions().untilAsserted(() -> {
      final var recorderJob =
          StreamFilter.jobRecords(RecordStream.of(zeebeTestEngine.getRecordStreamSource()))
              .withIntent(JobIntent.ERROR_THROWN)
              .stream().filter(
                  r -> r.getValue().getProcessInstanceKey() == processInstance.getProcessInstanceKey())
              .findFirst().orElseThrow();

      Assertions.assertThat(recorderJob.getValue().getErrorCode()).isNotNull()
          .isEqualTo("some-code");
      Assertions.assertThat(recorderJob.getValue().getErrorMessage()).isNotNull()
          .contains("failed with 400");
    });
  }

  @Test
  public void failIfErrorCodeIsNotPresent(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody("{\"error\":{\"message\":\"some message\"}}")));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("errorCodePath", "error.code")
                    .zeebeTaskHeader("errorMessagePath", "error.message")
                    .zeebeTaskHeader("method", "POST"));

    Awaitility.await().ignoreExceptions().untilAsserted(() -> {
      final var recorderJob =
          StreamFilter.jobRecords(RecordStream.of(zeebeTestEngine.getRecordStreamSource()))
              .withIntent(JobIntent.FAILED)
              .stream().filter(
                  r -> r.getValue().getProcessInstanceKey() == processInstance.getProcessInstanceKey())
              .findFirst().orElseThrow();

      Assertions.assertThat(recorderJob.getValue().getErrorMessage()).isNotNull()
          .contains("some message");
    });
  }

  @Test
  public void failIfBodyIsNotValidJson(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(aResponse().withStatus(400).withBody("{error.code = some code}")));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("errorCodePath", "error.code")
                    .zeebeTaskHeader("errorMessagePath", "error.message")
                    .zeebeTaskHeader("method", "POST"));

    Awaitility.await().ignoreExceptions().untilAsserted(() -> {
      final var recorderJob =
          StreamFilter.jobRecords(RecordStream.of(zeebeTestEngine.getRecordStreamSource()))
              .withIntent(JobIntent.FAILED)
              .stream().filter(
                  r -> r.getValue().getProcessInstanceKey() == processInstance.getProcessInstanceKey())
              .findFirst().orElseThrow();

      Assertions.assertThat(recorderJob.getValue().getErrorMessage()).isNotNull()
          .contains("failed with 400");
    });
  }

  @Test
  public void shouldReplacePlaceholdersWithVariables(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api/{{x}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"y\":{{y}}}"),
            Map.of("x", 1, "y", 2));

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 201);

    verify(
        postRequestedFor(urlEqualTo("/api/1"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"y\":2}")));
  }

  @Test
  public void shouldReplaceLegacyPlaceholders(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api/{{x}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"y\":${y}}"),
            Map.of("x", 1, "y", 2));

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 201);
    verify(
        postRequestedFor(urlEqualTo("/api/1")).withRequestBody(equalToJson("{\"y\":2}")));
  }

  @Test
  public void shouldReplacePlaceholdersWithContext(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api/{{jobKey}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"instanceKey\":{{processInstanceKey}}}"),
            Map.of());

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 201);

    final var job =
        StreamFilter.jobRecords(RecordStream.of(zeebeTestEngine.getRecordStreamSource()))
            .withIntent(JobIntent.CREATED)
            .stream().filter(
                r -> r.getValue().getProcessInstanceKey() == processInstance.getProcessInstanceKey())
            .findFirst().orElseThrow();

    verify(
        postRequestedFor(urlEqualTo("/api/" + job.getKey()))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(
                equalToJson(
                    "{\"instanceKey\":" + processInstance.getProcessInstanceKey() + "}")));
  }

  @Test
  public void shouldReplacePlaceholdersWithConfigVariablesWithM2MToken(
      WireMockRuntimeInfo wmRuntimeInfo) {
    //    WIRE_MOCK_RULE.resetAll(); // forget the defaults set in the static initializer for this
    // test
    //    stubFor(post(urlEqualTo("/token")).willReturn(aResponse()
    //                    .withHeader("Content-Type", "application/json")
    //                    .withBody("{ \"token_type\": \"Bearer\", \"access_token\":
    // \"TOKEN_123_42\" }")));
    stubFor(
        get(urlEqualTo("/config"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("[ {\"key\": \"x\", \"value\":1},  {\"key\":\"y\", \"value\":2} ]")));
    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api/{{x}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"y\":{{y}}}"),
            Map.of());

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 201);

    // I can't check if the /token was called here, as the token is typically already cached from
    // other test runs
    // and I can't move this to a seperate test class because of limitations in the test support
    // from zeebe at the moment
    // So I check if the Authorization Header is set correctly instead
    verify(
        getRequestedFor(urlEqualTo("/config"))
            .withHeader("Authorization", equalTo("Bearer TOKEN_123_42")));
    verify(
        postRequestedFor(urlEqualTo("/api/1"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"y\":2}")));
  }

  @Test
  public void shouldReplacePlaceholdersWithConfigVariablesWithM2MTokenRefresh(
      WireMockRuntimeInfo wmRuntimeInfo) {
    // forbidden, which happens because of outdated tokens
    stubFor(
        get(urlMatching("/config"))
            .inScenario("RefreshTokenScenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(403))
            .willSetStateTo("ALLOWED"));
    // so a refresh should happen (already configured in the setup)
    // then it works
    stubFor(
        get(urlEqualTo("/config"))
            .inScenario("RefreshTokenScenario")
            .whenScenarioStateIs("ALLOWED")
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("[ {\"key\": \"x\", \"value\":1},  {\"key\":\"y\", \"value\":2} ]")));

    stubFor(post(urlMatching("/api/.*")).willReturn(aResponse().withStatus(201)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api/{{x}}")
                    .zeebeTaskHeader("method", "POST")
                    .zeebeTaskHeader("body", "{\"y\":{{y}}}"),
            Map.of());

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 201);

    verify(
        postRequestedFor(urlEqualTo("/token"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(
                equalToJson(
                    "{\"client_id\":\"someClientId\", \"client_secret\": \"someSecret\",  \"audience\": \"someAudience\",  \"grant_type\": \"client_credentials\"}")));

    verify(
        postRequestedFor(urlEqualTo("/api/1"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("{\"y\":2}")));
  }

  private ProcessInstanceEvent createInstance(final Consumer<ServiceTaskBuilder> taskCustomizer) {
    return createInstance(taskCustomizer, new HashMap<>());
  }

  private ProcessInstanceEvent createInstance(
      final Consumer<ServiceTaskBuilder> taskCustomizer, Map<String, Object> variables) {

    ServiceTaskBuilder processBuilder =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("http"));

    taskCustomizer.accept(processBuilder);
    processBuilder.endEvent();

    client
        .newDeployCommand()
        .addProcessModel(processBuilder.done(), "process.bpmn")
        .requestTimeout(Duration.ofSeconds(10))
        .send()
        .join();

    return
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .variables(variables)
            .requestTimeout(Duration.ofSeconds(10))
            .send()
            .join();
  }

  @Test
  public void testCustomHeadersCanBeProvided(WireMockRuntimeInfo wmRuntimeInfo) {

    stubFor(put(urlEqualTo("/api")).willReturn(aResponse().withStatus(200)));

    final var processInstance =
        createInstance(
            serviceTask ->
                serviceTask
                    .zeebeTaskHeader("url", wmRuntimeInfo.getHttpBaseUrl() + "/api")
                    .zeebeTaskHeader("method", "PUT")
                    .zeebeTaskHeader("header-x-lower", "case")
                    .zeebeTaskHeader("HEADER-x-upper", "case"));

    ZeebeTestThreadSupport.waitForProcessInstanceCompleted(processInstance);

    BpmnAssert.assertThat(processInstance).isCompleted().hasVariableWithValue("statusCode", 200);

    verify(
        putRequestedFor(urlEqualTo("/api"))
            .withHeader("x-lower", equalTo("case"))
            .withHeader("x-upper", equalTo("case")));
  }

}
