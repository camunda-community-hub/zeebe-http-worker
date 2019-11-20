package io.zeebe.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ConfigurationMaps {

  private final Map<String, String> customHeaders;
  private final Map<String, Object> variables;
  private final Map<String, String> environmentVariables;

  private final Map<String, Object> config;

  public ConfigurationMaps(
      Map<String, String> customHeaders,
      Map<String, Object> variables,
      Map<String, String> environmentVariables) {
    this.customHeaders = customHeaders;
    this.variables = variables;
    this.environmentVariables = environmentVariables;

    config = new HashMap<>(customHeaders);
    config.putAll(variables);
    config.putAll(environmentVariables);
  }

  public Optional<Object> get(String key) {
    return Optional.<Object>ofNullable(customHeaders.get(key))
        .or(() -> Optional.ofNullable(variables.get(key)))
        .or(() -> Optional.ofNullable(environmentVariables.get(key)));
  }

  public Optional<String> getString(String key) {
    return get(key).map(String::valueOf).filter(v -> !v.isEmpty());
  }

  public Map<String, Object> getConfig() {
    return config;
  }
}
