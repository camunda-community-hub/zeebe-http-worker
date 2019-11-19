package io.zeebe.http;

import java.util.Map;
import java.util.Map.Entry;

class ConfigurationMaps {
  
  public Map<String, String> customHeaders;
  public Map<String, Object> variables;
  public Map<String, String> environmentVariables;   
  
  /**
   * Replace variables in 
   **/
  public String replaceVariables(String input) {
    if (input==null) {
      return null;
    }
    // RegEx: .*(\$\{hallo\}).* 
    for (Entry<String, String> entry : environmentVariables.entrySet()) {
      input = input.replaceAll("\\$\\{" + entry.getKey() + "\\}", entry.getValue());        
    }
    for (Entry<String, Object> entry : variables.entrySet()) {
      input = input.replaceAll("\\$\\{" + entry.getKey() + "\\}", String.valueOf(entry.getValue()));        
    }
    return input;
  }
  
}