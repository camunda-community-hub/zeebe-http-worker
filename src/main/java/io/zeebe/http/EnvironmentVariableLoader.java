package io.zeebe.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper to load environment variables from a configured URL as JSON map.
 * 
 * This can be e.g. used to hand over cloud worker configurations.
 * 
 * Current limitation: The URL needs to be open and accessible, so security needs
 * to be addressed on the network layer (JWT support is in the roadmap)
 */
public class EnvironmentVariableLoader {
  
  public static long RELOAD_RATE_MS = 15000; // check for changes every 15 seconds
  protected final HttpClient client = HttpClient.newHttpClient();
  protected final ObjectMapper objectMapper = new ObjectMapper();
  
  private long cacheLoaded = 0;
  private Map<String, String> cachedValues = null;
  
  /**
   * You can set this to use this value instead of the environment variable, e.g. in testing
   */
  public static String MOCK_ENV_VARS_URL = null; 
  
  public String getEnvironmentVariableUrl() {
    if (MOCK_ENV_VARS_URL!=null) {
      return MOCK_ENV_VARS_URL;
    } else {
      return System.getenv("ENV_VARS_URL");
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> loadVariables() {
    final String environmentVariableUrl = getEnvironmentVariableUrl();
    // only read if environment variable is set, otherwise return empty map
    if (environmentVariableUrl == null || environmentVariableUrl.length()==0) {
      return new HashMap<>();
    }
    // if cached values are there and up-to-date, return them
    if (cachedValues!=null && (System.currentTimeMillis() - cacheLoaded) < RELOAD_RATE_MS) {
      return cachedValues;
    }
    // otherwise reload cache and return the new values
    try {
      HttpRequest getVariablesRequest = HttpRequest.newBuilder() //
          .uri(URI.create(environmentVariableUrl)) //
          .header("Accept", "application/json") //
          .GET() //
          .build();      
      String jsonResponse = client.send(getVariablesRequest, BodyHandlers.ofString()).body();
      
      cacheLoaded = System.currentTimeMillis();          
      cachedValues = objectMapper.readValue(jsonResponse, Map.class);
      
      return cachedValues;      
    } catch (Exception e) {
      throw new RuntimeException("Could not load variables from '" + environmentVariableUrl + "': " + e.getMessage(), e);
    }
  }
}
