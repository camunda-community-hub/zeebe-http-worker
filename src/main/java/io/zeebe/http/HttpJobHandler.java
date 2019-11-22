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
import io.zeebe.http.config.ConfigProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class HttpJobHandler implements JobHandler {

  private static final String PARAMETER_URL = "url";
  private static final String PARAMETER_METHOD = "method";
  private static final String PARAMETER_BODY = "body";
  private static final String PARAMETER_AUTHORIZATION = "authorization";

  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PlaceholderProcessor placeholderProcessor = new PlaceholderProcessor();

  private final ConfigProvider configProvider;

  public HttpJobHandler(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

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
    final ConfigurationMaps configurationMaps =
        new ConfigurationMaps(job, configProvider.getVariables());

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
