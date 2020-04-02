package io.zeebe.http.variables;

import java.util.Map;

public interface EnvironmentVariablesProvider {
  Map<String, String> getVariables();
}
