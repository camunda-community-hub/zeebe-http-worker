package io.zeebe.http.variables;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.http.ZeebeHttpWorkerConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper to load environment variables from a configured URL as JSON map.
 * 
 * Environment variables might need a M2M / JWT Token config to authorize to load the variables
 *
 * <p>This can be e.g. used to hand over cloud worker configurations.
 *
 */
public class RemoteEnvironmentVariablesProvider implements EnvironmentVariablesProvider {

  private final ZeebeHttpWorkerConfig config;
  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();
  
  // M2M Token / JWT used for Auth0 in Cloud
  private String cachedM2mToken = null;

  private Instant lastUpdate = Instant.MIN;
  private Map<String, String> cachedVariables = null;
  
  private static class WorkerVariable {
    public String key;
    public String value;
  }

  protected RemoteEnvironmentVariablesProvider(ZeebeHttpWorkerConfig config) {
    this.config = config;
    // Make sure that variable URL is already checked during worker startup
    getVariables();
  }

  public Map<String, String> getVariables() {
    // if cached values are there and up-to-date, return them
    if (cachedVariables != null
        && Duration.between(lastUpdate, Instant.now()).toMillis() < config.getEnvironmentVariablesReloadInterval().toMillis()) {
      return cachedVariables;
    }
    
    // otherwise reload cache and return the new values
    try {
      HttpRequest httpRequest = createHttpRequest();
      HttpResponse<String> httpResponse = client.send(httpRequest, BodyHandlers.ofString());
      if (httpResponse.statusCode()==401 || httpResponse.statusCode()==403) {
        // Token expired - request a new one on the next request
        cachedM2mToken = null;
        
        // and issue a new request
        httpRequest = createHttpRequest();
        httpResponse = client.send(httpRequest, BodyHandlers.ofString());
      }
      if (httpResponse.statusCode()!=200) {
        throw new RuntimeException("Could not load environment variables successfully, see HTTP response with status " + httpResponse.statusCode() + ": " + httpResponse);
      }
      String jsonResponse = httpResponse.body();

      lastUpdate = Instant.now();
      cachedVariables = new HashMap<String, String>();

      List<WorkerVariable> variables = new ArrayList<>();
      if (jsonResponse!=null && !jsonResponse.isBlank()) {
        variables = objectMapper.readValue(jsonResponse, new TypeReference<List<WorkerVariable>>(){});
      }
      for (WorkerVariable workerVariable : variables) {
        cachedVariables.put(workerVariable.key, workerVariable.value);
      }
      
      return cachedVariables;
    } catch (Exception e) {
      throw new RuntimeException(
          "Could not load variables from '" + config.getEnvironmentVariablesUrl() + "': " + e.getMessage(), e);
    }
  }

  private HttpRequest createHttpRequest() throws IOException, InterruptedException {
    ArrayList<String> headers = new ArrayList<String>();
    headers.add("Accept");
    headers.add("application/json");
    
    if (config.isEnvironmentVariablesM2mBaseUrlSet()) {
      if (cachedM2mToken==null) {
        refreshM2mToken();
      }
      headers.add("Authorization");
      headers.add(cachedM2mToken);        
    }
    
    HttpRequest getVariablesRequest = HttpRequest.newBuilder()
        .uri(URI.create(config.getEnvironmentVariablesUrl()))
        .headers(headers.toArray(new String[0]))
        .GET()
        .build();
    return getVariablesRequest;
  }
  
  @SuppressWarnings("unused")
  private static class RefreshM2mTokenRequest {
    public String client_id;
    public String client_secret;
    public String audience;
    public String grant_type = "client_credentials";
  }

  private void refreshM2mToken() throws IOException, InterruptedException {
    RefreshM2mTokenRequest request = new RefreshM2mTokenRequest();
    request.client_id = config.getEnvironmentVariablesM2mClientId();
    request.client_secret = config.getEnvironmentVariablesM2mClientSecret();
    request.audience = config.getEnvironmentVariablesM2mAudience();
    
    HttpRequest getTokenRequest = 
        HttpRequest.newBuilder()
            .uri(URI.create(config.getEnvironmentVariablesM2mBaseUrl()))
            .POST(BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
            .headers("Accept", "application/json", "Content-Type", "application/json")
            .build();
    String response = client.send(getTokenRequest, BodyHandlers.ofString()).body();
    
    JsonNode responseJson = objectMapper.readTree(response);
    cachedM2mToken = responseJson.get("token_type").asText() + " " + responseJson.get("access_token").asText();
  }  
}
