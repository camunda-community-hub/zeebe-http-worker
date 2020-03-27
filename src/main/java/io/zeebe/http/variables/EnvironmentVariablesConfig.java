package io.zeebe.http.variables;

import io.zeebe.http.ZeebeHttpWorkerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnvironmentVariablesConfig {

  @Bean
  public EnvironmentVariablesProvider getProvider(ZeebeHttpWorkerConfig config) {
    if (config.isEnvironmentVariableUrlSet()) {
      return new RemoteEnvironmentVariablesProvider(config);
    } else {
      return new LocalVariablesProvider(config);
    }
  }

}
