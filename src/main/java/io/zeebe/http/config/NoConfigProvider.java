package io.zeebe.http.config;

import java.util.Map;

public class NoConfigProvider implements ConfigProvider {

  @Override
  public Map<String, String> getVariables() {
    return Map.of();
  }
}
