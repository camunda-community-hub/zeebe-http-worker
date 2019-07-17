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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.client.api.worker.JobHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HttpJobHandler implements JobHandler {

  private static final String PARAMETER_URL = "url";
  private static final String PARAMETER_METHOD = "method";
  private static final String PARAMETER_BODY = "body";
  private static final String PARAMETER_AUTHORIZATION = "authorization";

  public static final List<String> VARIABLE_NAMES = Arrays.asList(PARAMETER_URL, PARAMETER_BODY, PARAMETER_AUTHORIZATION);

  final HttpClient client = HttpClient.newHttpClient();

  final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void handle(JobClient jobClient, ActivatedJob job)
      throws IOException, InterruptedException {

    final HttpRequest request = buildRequest(job);

    final HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());

    final Map<String, Object> result = processResponse(response);

    jobClient.newCompleteCommand(job.getKey()).variables(result).send().join();
  }

  private HttpRequest buildRequest(ActivatedJob job) {
    final Map<String, String> customHeaders = job.getCustomHeaders();
    final Map<String, Object> variables = job.getVariablesAsMap();

    final String url = getUrl(customHeaders, variables);
    final String method = getMethod(customHeaders);
    final HttpRequest.BodyPublisher bodyPublisher = getBodyPublisher(variables);

    final HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(1))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .method(method, bodyPublisher);

    getAuthentication(customHeaders, variables)
        .ifPresent(auth -> builder.header("Authorization", auth));

    return builder.build();
  }

  private String getUrl(Map<String, String> customHeaders, Map<String, Object> variables) {
    return Optional.ofNullable(variables.get(PARAMETER_URL))
        .map(String::valueOf)
        .or(() -> Optional.ofNullable(customHeaders.get(PARAMETER_URL)))
        .filter(url -> !url.isEmpty())
        .orElseThrow(() -> new RuntimeException("Missing required parameter: " + PARAMETER_URL));
  }

  private Optional<String> getAuthentication(
      Map<String, String> customHeaders, Map<String, Object> variables) {
    return Optional.ofNullable(variables.get(PARAMETER_AUTHORIZATION))
        .map(String::valueOf)
        .or(() -> Optional.ofNullable(customHeaders.get(PARAMETER_AUTHORIZATION)));
  }

  private String getMethod(Map<String, String> customHeaders) {
    return Optional.ofNullable(customHeaders.get(PARAMETER_METHOD))
        .map(String::toUpperCase)
        .orElse("GET");
  }

  private HttpRequest.BodyPublisher getBodyPublisher(Map<String, Object> variables) {
    return Optional.ofNullable(variables.get(PARAMETER_BODY))
        .map(this::bodyToJson)
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

  private Map<String, Object> processResponse(HttpResponse<String> response) {
    final Map<String, Object> result = new java.util.HashMap<>();

    result.put("statusCode", response.statusCode());

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
