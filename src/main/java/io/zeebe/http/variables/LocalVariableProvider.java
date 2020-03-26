package io.zeebe.http.variables;

import io.zeebe.http.ZeebeHttpWorkerConfig;

import java.util.Map;

public class LocalVariableProvider extends EnvironmentVariablesProvider {

  protected LocalVariableProvider(ZeebeHttpWorkerConfig config) {
    super(config);
  }

  @Override
  protected Map<String, String> getRawVariables() {
    return System.getenv();
  }

}
