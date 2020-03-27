package io.zeebe.http;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeHttpWorkerConfig {
 
  @Value( "${ENV_VARS_URL:#{null}}" )
  private String environmentVariablesUrl;

  @Value( "${ENV_VARS_RELOAD_RATE:15000}" )  
  private Integer environmentVariablesReloadIntervalMs;
  
  @Value( "${ENV_VARS_M2M_BASE_URL:#{null}}" )
  private String environmentVariablesM2mBaseUrl;
  
  @Value( "${ENV_VARS_M2M_CLIENT_ID:#{null}}" )
  private String environmentVariablesM2mClientId;
  
  @Value( "${ENV_VARS_M2M_CLIENT_SECRET:@null}" )
  private String environmentVariablesM2mClientSecret;
  
  @Value( "${ENV_VARS_M2M_AUDIENCE:#{null}}")
  private String environmentVariablesM2mAudience;  

  @Value( "${LOCAL_ENV_VARS_PREFIX:ZEEBE_ENV_}")
  private String localEnvironmentVariablesPrefix;

  @Value( "${LOCAL_ENV_VARS_REMOVE_PREFIX:true}")
  private Boolean removeLocalEnvironmentVariablesPrefix;

  public boolean isEnvironmentVariableUrlSet() {
    return (getEnvironmentVariablesUrl() != null && getEnvironmentVariablesUrl().length() > 0);
  }

  public String getEnvironmentVariablesUrl() {
    return environmentVariablesUrl;
  }

  public Duration getEnvironmentVariablesReloadInterval() {
    return Duration.ofMillis( environmentVariablesReloadIntervalMs );
  }
    
  public boolean isEnvironmentVariablesM2mBaseUrlSet() {
    return (getEnvironmentVariablesM2mBaseUrl() != null && getEnvironmentVariablesM2mBaseUrl().length() > 0);
  }

  public String getEnvironmentVariablesM2mBaseUrl() {
    return environmentVariablesM2mBaseUrl;
  }

  public String getEnvironmentVariablesM2mClientId() {
    return environmentVariablesM2mClientId;
  }

  public String getEnvironmentVariablesM2mClientSecret() {
    return environmentVariablesM2mClientSecret;
  }

  public String getEnvironmentVariablesM2mAudience() {
    return environmentVariablesM2mAudience;
  }

  public String getLocalEnvironmentVariablesPrefix() {
    if (localEnvironmentVariablesPrefix != null) {
      return localEnvironmentVariablesPrefix;
    } else {
      return "";
    }
  }

  public Boolean removeLocalEnvironmentVariablesPrefix() { return removeLocalEnvironmentVariablesPrefix; }

}
