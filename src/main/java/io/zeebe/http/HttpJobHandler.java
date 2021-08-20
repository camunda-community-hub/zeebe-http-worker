/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.http;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.zeebe.http.variables.EnvironmentVariablesProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class HttpJobHandler implements JobHandler {

  public static Duration CONNECTION_TIMEOUT = Duration.ofMinutes(1);
  public static long RESPONSE_TIMEOUT_VALUE = 60;
  public static TimeUnit RESPONSE_TIMEOUT_TIME_UNIT = TimeUnit.SECONDS;
  
  private static final String PARAMETER_URL = "url";
  private static final String PARAMETER_METHOD = "method";
  private static final String PARAMETER_BODY = "body";
  private static final String PARAMETER_AUTHORIZATION = "authorization";
  private static final String PARAMETER_CONTENT_TYPE = "contentType";
  private static final String PARAMETER_ACCEPT = "accept";
  private static final String PARAMETER_HTTP_STATUS_CODE_FAILURE = "statusCodeFailure";
  private static final String PARAMETER_HTTP_STATUS_CODE_COMPLETION = "statusCodeCompletion";
  private static final String PARAMETER_HTTP_ERROR_CODE_PATH = "errorCodePath";
  private static final String PARAMETER_HTTP_ERROR_MESSAGE_PATH = "errorMessagePath";

  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PlaceholderProcessor placeholderProcessor = new PlaceholderProcessor();

  @Autowired
  private EnvironmentVariablesProvider environmentVariablesProvider;

  @Override
  public void handle(JobClient jobClient, ActivatedJob job) throws IOException, InterruptedException, ExecutionException, TimeoutException {

    final ConfigurationMaps configurationMaps = new ConfigurationMaps(job, environmentVariablesProvider.getVariables());
    final HttpRequest request = buildRequest(configurationMaps);

    CompletableFuture<HttpResponse<String>> requestFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    final HttpResponse<String> response = requestFuture.get(RESPONSE_TIMEOUT_VALUE, RESPONSE_TIMEOUT_TIME_UNIT);
    
    if (hasFailingStatusCode(response, configurationMaps)) {
      processFailure(configurationMaps, jobClient, job, response);
    } else if (hasCompletingStatusCode(response, configurationMaps)) {
      final Map<String, Object> result = processResponse(job, response, request);
      jobClient.newCompleteCommand(job.getKey()).variables(result).send().join();
    } else {
      // do nothing
      // TODO: Would be great to extend the locking time now 
      // as this might be used for HTTP 202 to asynchronously complete the task
      // but not yet supported in Zeebe
      // TODO: Also would be great to be able to add the status code here as well
      // but currently no Zeebe API available to do this
    }
  }

  /**
   * Send a Fail command or throw a Zeebe error
   */
  private void processFailure(ConfigurationMaps configurationMaps, JobClient jobClient, ActivatedJob job, HttpResponse<String> response) {
    Optional<String> errorCode = extractFromBody(configurationMaps, response.body(), PARAMETER_HTTP_ERROR_CODE_PATH);
    String errorMessage = extractFromBody(configurationMaps, response.body(), PARAMETER_HTTP_ERROR_MESSAGE_PATH)
      .orElse("Http request failed with " + response.statusCode() + ": " + response.body());

    // if the error code is configured and was found on the response, throw a Zeebe error command
    errorCode.ifPresentOrElse(code ->
        jobClient.newThrowErrorCommand(job.getKey())
          .errorCode(code)
          // extracted message or empty string if not found
          .errorMessage(errorMessage)
          .send().join(),
      () ->
        // if no error was configured or extracted, fail the job
        jobClient.newFailCommand(job.getKey())
          .retries(job.getRetries() - 1) // simply decrement retries for now, but we should think about it: https://github.com/zeebe-io/zeebe-http-worker/issues/22
          .errorMessage(errorMessage)
          .send().join()
    );
  }

  private Optional<String> extractFromBody(ConfigurationMaps configurationMaps, String body, String pathParameterName) {
    return configurationMaps.getString(pathParameterName)
            .map(p -> "/" + p.replace('.', '/'))
            .map(JsonPointer::compile)
            .flatMap(pointer -> extractPath(body, pointer));
  }

  private HttpRequest buildRequest(ConfigurationMaps configurationMaps) {
    final String url = getUrl(configurationMaps);

    final String method = getMethod(configurationMaps);
    final HttpRequest.BodyPublisher bodyPublisher = getBodyPublisher(configurationMaps);

    final var contentType = getContentType(configurationMaps).orElse("application/json");
    final var accept = getAccept(configurationMaps).orElse("application/json");

    final HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(CONNECTION_TIMEOUT)
            .header("Content-Type", contentType)
            .header("Accept", accept)
            .method(method, bodyPublisher);

    getAuthorization(configurationMaps).ifPresent(auth -> builder.header("Authorization", auth));

    return builder.build();
  }

  private Optional<String> getConfig(final ConfigurationMaps configMaps,
      final String parameterUrl) {
    return configMaps
        .getString(parameterUrl)
        .map(url -> placeholderProcessor.process(url, configMaps.getConfig()));
  }

  private String getUrl(ConfigurationMaps configMaps) {
    return getConfig(configMaps, PARAMETER_URL)
        .orElseThrow(() -> new RuntimeException("Missing required parameter: " + PARAMETER_URL));
  }

  private Optional<String> getAuthorization(ConfigurationMaps configMaps) {
    return getConfig(configMaps, PARAMETER_AUTHORIZATION);
  }

  private Optional<String> getContentType(ConfigurationMaps configMaps) {
    return getConfig(configMaps, PARAMETER_CONTENT_TYPE);
  }

  private Optional<String> getAccept(ConfigurationMaps configMaps) {
    return getConfig(configMaps, PARAMETER_ACCEPT);
  }

  private String getMethod(ConfigurationMaps configMaps) {
    return getConfig(configMaps, PARAMETER_METHOD)
        .map(String::toUpperCase)
        .orElse("GET");
  }

  private HttpRequest.BodyPublisher getBodyPublisher(ConfigurationMaps configMaps) {
    return configMaps
        .get(PARAMETER_BODY)
        .map(
            body -> {
              if (body instanceof String) {
                return placeholderProcessor.process((String) body, configMaps.getConfig());
              } else {
                return bodyToJson(body);
              }
            })
        .map(HttpRequest.BodyPublishers::ofString)
        .orElse(HttpRequest.BodyPublishers.noBody());
  }

  private String bodyToJson(Object body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize request body to JSON: " + body);
    }
  }

  private boolean hasFailingStatusCode(HttpResponse<String> response, ConfigurationMaps configurationMaps) {
    String statusCode = String.valueOf(response.statusCode()).toLowerCase();
    String statusCodePattern = configurationMaps.getString(PARAMETER_HTTP_STATUS_CODE_FAILURE).orElse("3xx, 4xx, 5xx");
    return checkIfCodeMatches(statusCode, statusCodePattern);
  }
  private boolean hasCompletingStatusCode(HttpResponse<String> response, ConfigurationMaps configurationMaps) {
    String statusCode = String.valueOf(response.statusCode()).toLowerCase();
    String statusCodePattern = configurationMaps.getString(PARAMETER_HTTP_STATUS_CODE_COMPLETION).orElse("1xx, 2xx");
    return checkIfCodeMatches(statusCode, statusCodePattern);
  }
  
  private boolean checkIfCodeMatches(String statusCode, String matchCodePattern) {
    return matchCodePattern.contains(statusCode)
      || (statusCode.startsWith("1") && matchCodePattern.contains("1xx"))
      || (statusCode.startsWith("2") && matchCodePattern.contains("2xx"))
      || (statusCode.startsWith("3") && matchCodePattern.contains("3xx"))
      || (statusCode.startsWith("4") && matchCodePattern.contains("4xx"))
      || (statusCode.startsWith("5") && matchCodePattern.contains("5xx"));
  }

  private Map<String, Object> processResponse(ActivatedJob job,
      HttpResponse<String> response, HttpRequest request) {

    final Map<String, Object> result = new java.util.HashMap<>();
    int statusCode = response.statusCode();
    result.put("statusCode", statusCode);

    Optional.ofNullable(response.body())
        .filter(body -> !body.isEmpty())
        .map(body -> {
          if (request.headers().allValues("Accept").contains("text/plain") &&
              response.headers().allValues("Content-Type").contains("text/plain")
          ) {
            return body;
          } else {
            return bodyToObject(body);
          }
        })
        .ifPresent(body -> result.put("body", body));

    return result;
  }

  private Object bodyToObject(String body) {
    try {
      return objectMapper.readValue(body, Object.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to deserialize response body from JSON: " + body);
    }
  }

  private Optional<String> extractPath(String body, JsonPointer pointer) {
    try {
      JsonNode valueNode = objectMapper.readTree(body).at(pointer);
      return Optional.ofNullable(valueNode.textValue());
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
