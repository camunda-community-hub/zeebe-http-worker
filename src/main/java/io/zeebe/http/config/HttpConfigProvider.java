package io.zeebe.http.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Helper to load environment variables from a configured URL as JSON map.
 *
 * <p>This can be e.g. used to hand over cloud worker configurations.
 *
 * <p>Current limitation: The URL needs to be open and accessible, so security needs to be addressed
 * on the network layer (JWT support is in the roadmap)
 */
public class HttpConfigProvider implements ConfigProvider {

  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final Duration reloadInterval;
  private final String url;

  private Instant lastUpdate = Instant.MIN;
  private Map<String, String> cachedVariables = null;

  public HttpConfigProvider(String url, Duration reloadInterval) {
    this.url = url;
    this.reloadInterval = reloadInterval;
  }

  @Override
  public Map<String, String> getVariables() {

    // only read if environment variable is set, otherwise return empty map
    if (url == null || url.length() == 0) {
      return Map.of();
    }
    // if cached values are there and up-to-date, return them
    if (cachedVariables != null
        && Duration.between(lastUpdate, Instant.now()).toMillis() < reloadInterval.toMillis()) {
      return cachedVariables;
    }
    // otherwise reload cache and return the new values
    try {
      HttpRequest getVariablesRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Accept", "application/json")
              .GET()
              .build();

      String jsonResponse = client.send(getVariablesRequest, BodyHandlers.ofString()).body();

      lastUpdate = Instant.now();
      cachedVariables = objectMapper.readValue(jsonResponse, Map.class);

      return cachedVariables;
    } catch (Exception e) {
      throw new RuntimeException(
          "Could not load variables from '" + url + "': " + e.getMessage(), e);
    }
  }
}
