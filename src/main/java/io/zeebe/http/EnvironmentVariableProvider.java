package io.zeebe.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper to load environment variables from a configured URL as JSON map.
 *
 * <p>This can be e.g. used to hand over cloud worker configurations.
 *
 * <p>Current limitation: The URL needs to be open and accessible, so security needs to be addressed
 * on the network layer (JWT support is in the roadmap)
 */
@Component
public class EnvironmentVariableProvider {
  
  @Autowired
  private ZeebeHttpWorkerConfig config;

  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private Instant lastUpdate = Instant.MIN;
  private Map<String, String> cachedVariables = null;
  
  public Map<String, String> getVariables() {
    // only read if environment variable is set, otherwise return empty map
    if (!config.isEnvironmentVariableUrlSet()) {
      return Map.of();
    }
    // if cached values are there and up-to-date, return them
    if (cachedVariables != null
        && Duration.between(lastUpdate, Instant.now()).toMillis() < config.getEnvironmentVariableReloadInterval().toMillis()) {
      return cachedVariables;
    }
    // otherwise reload cache and return the new values
    try {
      HttpRequest getVariablesRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getEnvironmentVariableUrl()))
              .header("Accept", "application/json")
              .GET()
              .build();

      String jsonResponse = client.send(getVariablesRequest, BodyHandlers.ofString()).body();

      lastUpdate = Instant.now();
      cachedVariables = objectMapper.readValue(jsonResponse, Map.class);

      return cachedVariables;
    } catch (Exception e) {
      throw new RuntimeException(
          "Could not load variables from '" + config.getEnvironmentVariableUrl() + "': " + e.getMessage(), e);
    }
  }
}
