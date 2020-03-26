package io.zeebe.http.variables;

import io.zeebe.http.ZeebeHttpWorkerConfig;

import java.util.Map;
import java.util.stream.Collectors;

public abstract class EnvironmentVariablesProvider {
  protected final ZeebeHttpWorkerConfig config;

  protected EnvironmentVariablesProvider(ZeebeHttpWorkerConfig config) {
    this.config = config;
  }

  abstract protected Map<String, String> getRawVariables();

  /**
   * @return Only return environment variables starting with the configured
   * prefix and (optionally) removing it.
   */
  public Map<String, String> getVariables() {
    return getRawVariables().entrySet().stream()
      .filter(this::startsWithPrefix)
      .collect(Collectors.toMap(this::extractVariableName, Map.Entry::getValue));
  }

  private Boolean startsWithPrefix(Map.Entry<String, String> entry) {
    String prefix = config.getEnvironmentVariablesPrefix();
    return entry.getKey().toUpperCase().startsWith(prefix.toUpperCase());
  }

  /**
   * @return If configured to remove prefix, and the variable starts with it (case insensitive)
   * removes the prefix from the name.
   */
  private String extractVariableName(Map.Entry<String, String> entry) {
    String prefix = config.getEnvironmentVariablesPrefix();
    String key = entry.getKey();

    if (config.removeEnvironmentVariablesPrefix() && startsWithPrefix(entry)) {
      return key.substring(prefix.length());
    } else {
      return key;
    }
  }
}
