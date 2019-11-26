package io.zeebe.http;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeHttpWorkerConfig {
 
  @Value( "${ENV_VARS_URL:null}" )
  private String environmentVariableUrl;

  @Value( "${ENV_VARS_RELOAD_RATE:15000}" )  
  private long environmentVariableReloadIntervalMs;

  public boolean isEnvironmentVariableUrlSet() {
    return (getEnvironmentVariableUrl() != null && getEnvironmentVariableUrl().length() > 0);
  }

  public String getEnvironmentVariableUrl() {
    return environmentVariableUrl;
  }

  public Duration getEnvironmentVariableReloadInterval() {
    return Duration.ofMillis( environmentVariableReloadIntervalMs );
  }
  
  
}
