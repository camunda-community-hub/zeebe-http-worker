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

//  public static final List<String> VARIABLE_NAMES = Arrays.asList(PARAMETER_URL, PARAMETER_BODY, PARAMETER_AUTHORIZATION);

  final HttpClient client = HttpClient.newHttpClient();
  final ObjectMapper objectMapper = new ObjectMapper();
  final EnvironmentVariableLoader environmentVariableLoader = new EnvironmentVariableLoader();

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
    ConfigurationMaps configurationMaps = new ConfigurationMaps();
    configurationMaps.customHeaders = job.getCustomHeaders();
    configurationMaps.variables = job.getVariablesAsMap();
    configurationMaps.environmentVariables = environmentVariableLoader.loadVariables();

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

    getAuthentication(configurationMaps)
        .ifPresent(auth -> builder.header("Authorization", auth));

    return builder.build();
  }

  /**
   * The URL can be configured either in the custom headers, the variables or the environmentVariables.
   * An ${expressions} can be used to be filled with variables or environmentVariables
   */
  private String getUrl(ConfigurationMaps configMaps) {
    return configMaps.replaceVariables( //
        Optional.ofNullable(configMaps.customHeaders.get(PARAMETER_URL)) //
        .or(() -> Optional.ofNullable(configMaps.variables.get(PARAMETER_URL)).map(String::valueOf)) //
        .or(() -> Optional.ofNullable(configMaps.environmentVariables.get(PARAMETER_URL))) //
        .filter(url -> !url.isEmpty()) //
        .orElseThrow(() -> new RuntimeException("Missing required parameter: " + PARAMETER_URL)));
  }

  /**
   * The Authentication can be configured either in the custom headers, the variables or the environmentVariables.
   * An ${expressions} can be used to be filled with variables or environmentVariables
   */
  private Optional<String> getAuthentication(ConfigurationMaps configMaps) {
    return Optional.ofNullable(configMaps.customHeaders.get(PARAMETER_AUTHORIZATION)) //
        .or(() -> Optional.ofNullable(configMaps.variables.get(PARAMETER_AUTHORIZATION)).map(String::valueOf)) //
        .or(() -> Optional.ofNullable(configMaps.environmentVariables.get(PARAMETER_AUTHORIZATION)))
        .map(configMaps::replaceVariables);
  }

  /**
   * The Method can be configured either in the custom headers, the variables or the environmentVariables.
   * An ${expressions} can be used to be filled with variables or environmentVariables
   */
  private String getMethod(ConfigurationMaps configMaps) {
    return Optional.ofNullable(configMaps.customHeaders.get(PARAMETER_METHOD)) //
        .or(() -> Optional.ofNullable(configMaps.variables.get(PARAMETER_METHOD)).map(String::valueOf)) //
        .or(() -> Optional.ofNullable(configMaps.environmentVariables.get(PARAMETER_METHOD)))
        .map(configMaps::replaceVariables)
        .map(String::toUpperCase)
        .orElse("GET");
  }

  /**
   * The Body is typically configured via the variable 'body', but can also be configured via 
   * custom headers or environmentVariables.
   * An ${expressions} can be used to be filled with variables or environmentVariables
   */
  private HttpRequest.BodyPublisher getBodyPublisher(ConfigurationMaps configMaps) {
    return Optional.ofNullable(configMaps.variables.get(PARAMETER_BODY)) //
        .or(() -> Optional.ofNullable(configMaps.customHeaders.get(PARAMETER_BODY))) //
        .or(() -> Optional.ofNullable(configMaps.environmentVariables.get(PARAMETER_BODY))) //
        .map(this::bodyToJson) //
        .map(configMaps::replaceVariables)
        .map(HttpRequest.BodyPublishers::ofString) //
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
