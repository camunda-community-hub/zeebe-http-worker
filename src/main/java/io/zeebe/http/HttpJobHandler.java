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
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HttpJobHandler implements JobHandler {

  private static Logger _log = LoggerFactory.getLogger(HttpJobHandler.class);

  private static final String PARAMETER_URL = "url";
  private static final String PARAMETER_METHOD = "method";
  private static final String PARAMETER_BODY = "body";
  private static final String RESULT_VARIABLE_NAME = "resultVariableName";
  private static final String POST_BODY_VARIABLE_NAME ="postBodyVariableName";
  private static final String PARAMETER_AUTHORIZATION = "authorization";

  final HttpClient client = HttpClient.newHttpClient();
  final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void handle(JobClient jobClient, ActivatedJob job)
      throws IOException, InterruptedException {

    final HttpRequest request = buildRequest(job);

    _log.trace("created request : " + request);

    final HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());

    Map<String, String> customHeaders = job.getCustomHeaders();
    String resultVariableName = getVariable(customHeaders, RESULT_VARIABLE_NAME, PARAMETER_BODY);

    _log.trace("HTTP result variable name is:" + resultVariableName);

    final Map<String, Object> result = processResponse(resultVariableName, response);

    _log.trace("response is : " + response);

    jobClient.newCompleteCommand(job.getKey()).variables(result).send().join();
  }

  private HttpRequest buildRequest(ActivatedJob job) {
    final Map<String, String> customHeaders = job.getCustomHeaders();
    final Map<String, Object> variables = job.getVariablesAsMap();

    final String url = getUrl(customHeaders, variables);
    final String method = getMethod(customHeaders);
    String postBodyVariableName = getVariable(customHeaders, POST_BODY_VARIABLE_NAME, PARAMETER_BODY);

    final HttpRequest.BodyPublisher bodyPublisher = getBodyPublisher(postBodyVariableName, variables);

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

    String urlToUse = Optional.ofNullable(variables.get(PARAMETER_URL))
            .map(String::valueOf)
            .or(() -> Optional.ofNullable(customHeaders.get(PARAMETER_URL)))
            .filter(url -> !url.isEmpty())
            .map(url  -> { return (String)resolveQueryParameters(url, variables);})
        .orElseThrow(() -> new RuntimeException("Missing required parameter: " + PARAMETER_URL));

    return urlToUse;

  }

  private String resolveQueryParameters(String url, Map<String, Object> variables)  {

    String resolvedUrl = url;

    try {

      List<NameValuePair> tokenizedQueryParams = URLEncodedUtils.parse(new URI(url), Charset.forName("UTF-8"));

      for (NameValuePair tokenizedQueryParam : tokenizedQueryParams) {

        String queryParamName = tokenizedQueryParam.getName();
        String queryParamTokenizedValue = tokenizedQueryParam.getValue().replaceFirst("\\$", "");
        String queryParamResolvedValue = resolveContextVariable(queryParamTokenizedValue, variables).toString();

        TokenizedQueryParameterNameValuePair resolvedQueryParamNameValuePair = new TokenizedQueryParameterNameValuePair(
                queryParamName,
                queryParamTokenizedValue,
                queryParamResolvedValue);

        resolvedUrl = url.replaceFirst("\\$" + resolvedQueryParamNameValuePair.getTokenizedValue(), resolvedQueryParamNameValuePair.getValue());
        System.out.println(resolvedUrl);
      }
    }
    catch (Exception ex){
      _log.warn("There was an error resolving query parameters in the URL. ", ex);
    }

    return resolvedUrl;
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

  private String getVariable(Map<String, String> customHeaders, String key, String defaultValue) {
    return Optional.ofNullable(customHeaders.get(key))
            .orElse(defaultValue);
  }

  private HttpRequest.BodyPublisher getBodyPublisher(String postBodyVariableName, Map<String, Object> variables) {

    _log.info("Variables are : " + variables);

    return Optional.ofNullable(resolveContextVariable(postBodyVariableName, variables))
        .map(this::bodyToJson)
        .map(HttpRequest.BodyPublishers::ofString)
        .orElse(HttpRequest.BodyPublishers.noBody());
  }

  private Object resolveContextVariable(String path, Map<String, Object> variables){
    Object value = null;
    String[] paths = path.split("\\.");

    if (paths.length > 1){

      // we have navigate a path such as property1.property2.....
      Map<String, Object> currentObject = variables;
      int i = 0;
      for (; i < paths.length - 1; i++){
        currentObject = ( Map<String, Object>)currentObject.get(paths[i]);
      }

      value = currentObject.get(paths[i]);
    }
    else {
      value = variables.get(path);
    }

    return value;
  }

  private String bodyToJson(Object body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize request body to JSON: " + body);
    }
  }

  private Map<String, Object> processResponse(String resultVariableName, HttpResponse<String> response) {
    final Map<String, Object> result = new java.util.HashMap<>();

    result.put("statusCode", response.statusCode());

    Optional.ofNullable(response.body())
        .filter(body -> !body.isEmpty())
        .map(this::bodyToObject)
        .ifPresent(body -> result.put(resultVariableName, body));

    return result;
  }

  private Object bodyToObject(String body) {
    try {
      return objectMapper.readValue(body, Object.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to deserialize response body from JSON: " + body);
    }
  }

  public static class TokenizedQueryParameterNameValuePair implements NameValuePair{

    private final String name;
    private final String value;
    private final String tokenizedValue;

    TokenizedQueryParameterNameValuePair(String name, String tokenizedValue, String resolvedValue){
      this.name = name;
      this.value = resolvedValue;
      this.tokenizedValue = tokenizedValue;

    }

    public String getTokenizedValue(){
      return this.tokenizedValue;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.format("%s=%s", getName(), getValue());
    }
  }
}
