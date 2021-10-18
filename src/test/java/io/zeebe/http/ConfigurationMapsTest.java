package io.zeebe.http;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigurationMapsTest {

  private HashMap<String, String> customHeaders;
  private ConfigurationMaps configurationMaps;
  private ActivatedJob job;
  private Map<String, String> environmentVariables;

  @BeforeEach
  void setUp() {
    job = mock(ActivatedJob.class);
    environmentVariables = emptyMap();
    customHeaders = new HashMap<>();
    when(job.getCustomHeaders()).thenReturn(customHeaders);
  }

  @Test
  void shouldIgnoreCaseForKey() {
    // given
    customHeaders.put("KEY", "value");
    configurationMaps = new ConfigurationMaps(job, environmentVariables);

    // when
    final Optional<String> valueLower = configurationMaps.getStringIgnoreCase("key");

    // then
    assertThat(valueLower.get()).isEqualTo("value");
  }
}
