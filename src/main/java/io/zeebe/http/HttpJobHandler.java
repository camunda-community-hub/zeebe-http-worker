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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.client.api.worker.JobHandler;

@Component
public class HttpJobHandler implements JobHandler {

  private static final String PARAMETER_URL = "url";
  private static final String PARAMETER_METHOD = "method";
  private static final String PARAMETER_BODY = "body";
  private static final String PARAMETER_AUTHORIZATION = "authorization";
  private static final String PARAMETER_HTTP_STATUS_CODE_FAILURE = "statusCodeFailure";
  private static final String PARAMETER_HTTP_STATUS_CODE_COMPLETION = "statusCodeCompletion";

  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PlaceholderProcessor placeholderProcessor = new PlaceholderProcessor();

  @Autowired
  private EnvironmentVariableProvider environmentVariableProvider;

  @Override
  public void handle(JobClient jobClient, ActivatedJob job) throws IOException, InterruptedException {

    final ConfigurationMaps configurationMaps = new ConfigurationMaps(job, environmentVariableProvider.getVariables());
    final HttpRequest request = buildRequest(configurationMaps);

    final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    
    if (hasFailingStatusCode(response, configurationMaps)) {
      jobClient.newFailCommand(job.getKey()); 
    } else if (hasCompletingStatusCode(response, configurationMaps)) {
      final Map<String, Object> result = processResponse(job, response);
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

  private HttpRequest buildRequest(ConfigurationMaps configurationMaps) {
    final String url = getUrl(configurationMaps);

    final String method = getMethod(configurationMaps);
    final HttpRequest.BodyPublisher bodyPublisher = getBodyPublisher(configurationMaps);

    final HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(1))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .method(method, bodyPublisher);

    getAuthentication(configurationMaps).ifPresent(auth -> builder.header("Authorization", auth));

    return builder.build();
  }

  private String getUrl(ConfigurationMaps configMaps) {
    return configMaps
        .getString(PARAMETER_URL)
        .map(url -> placeholderProcessor.process(url, configMaps.getConfig()))
        .orElseThrow(() -> new RuntimeException("Missing required parameter: " + PARAMETER_URL));
  }

  private Optional<String> getAuthentication(ConfigurationMaps configMaps) {
    return configMaps
        .getString(PARAMETER_AUTHORIZATION)
        .map(auth -> placeholderProcessor.process(auth, configMaps.getConfig()));
  }

  private String getMethod(ConfigurationMaps configMaps) {
    return configMaps
        .getString(PARAMETER_METHOD)
        .map(method -> placeholderProcessor.process(method, configMaps.getConfig()))
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
      || (statusCode.startsWith("4") && matchCodePattern.contains("4xx"));
  }

  private Map<String, Object> processResponse(ActivatedJob job, HttpResponse<String> response) {
    final Map<String, Object> result = new java.util.HashMap<>();

    int statusCode = response.statusCode();
    result.put("statusCode", statusCode);

    Optional.ofNullable(response.body())
        .filter(body -> !body.isEmpty())
        .map(this::bodyToObject)
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
}
