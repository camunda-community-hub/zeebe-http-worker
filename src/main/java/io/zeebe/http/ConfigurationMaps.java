package io.zeebe.http;

import io.zeebe.client.api.response.ActivatedJob;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Map that overlays different sources for objects used in expressions, namely
 * 
 * <ul>
 *   <li>Custom Headers of a Task in a BPMN workflow</li>
 *   <li>Workflow variables of the current instance</li>
 *   <li>Environment variables loaded from a given URL</li>
 * 
 * Additionally some constants can also be resolved, namely
 * 
 * <ul>
 *  <li>jobKey</li>
 *  <li>workflowInstanceKey</li>
 * </ul>
 */
public class ConfigurationMaps {

  private final Map<String, String> customHeaders;
  private final Map<String, Object> variables;
  private final Map<String, String> environmentVariables;

  private final Map<String, Object> config;

  public ConfigurationMaps(ActivatedJob job, Map<String, String> environmentVariables) {

    this.customHeaders = job.getCustomHeaders();
    this.variables = job.getVariablesAsMap();
    this.environmentVariables = environmentVariables;

    config = new HashMap<>();
    config.putAll(customHeaders);
    config.putAll(variables);
    config.putAll(environmentVariables);

    config.put("jobKey", job.getKey());
    config.put("workflowInstanceKey", job.getWorkflowInstanceKey());
  }

  public Optional<Object> get(String key) {
    return Optional.<Object>ofNullable(config.get(key));
  }

  public Optional<String> getString(String key) {
    return get(key).map(String::valueOf).filter(v -> !v.isEmpty());
  }

  public Map<String, Object> getConfig() {
    return config;
  }
}
