package io.zeebe.http.variables;

import io.zeebe.http.ZeebeHttpWorkerConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LocalVariablesProviderTest {

  private static final String PREFIX = "SOME_PREFIX_";
  private static final String PREFIX_MIXED_CASE = "some_Prefix_";
  private ZeebeHttpWorkerConfig config;
  private EnvironmentVariablesProvider provider;

  @Before
  public void setUp() {
    config = mock(ZeebeHttpWorkerConfig.class);

    provider = new LocalVariablesProvider(config) {
      protected Map<String, String> getRawVariables() {
        return Map.of(
          PREFIX + "prefixed", "1",
          PREFIX_MIXED_CASE + "mixed", "2",
          "not-prefixed", "3"
        );
      }
    };
  }

  @Test
  public void returnsOnlyVariablesWithPrefix() {
    when(config.getLocalEnvironmentVariablesPrefix()).thenReturn(PREFIX);
    when(config.removeLocalEnvironmentVariablesPrefix()).thenReturn(false);

    assertThat(provider.getVariables())
      .containsExactlyInAnyOrderEntriesOf(Map.of(
        PREFIX + "prefixed", "1",
        PREFIX_MIXED_CASE + "mixed", "2"
      ));
  }

  @Test
  public void returnsOnlyVariablesWithPrefixAndRemovesPrefix() {
    when(config.getLocalEnvironmentVariablesPrefix()).thenReturn(PREFIX);
    when(config.removeLocalEnvironmentVariablesPrefix()).thenReturn(true);

    assertThat(provider.getVariables())
      .containsExactlyInAnyOrderEntriesOf(Map.of(
        "prefixed", "1",
        "mixed", "2"
      ));
  }

  @Test
  public void returnsAllVariablesIfPrefixIsEmpty() {
    when(config.getLocalEnvironmentVariablesPrefix()).thenReturn("");
    when(config.removeLocalEnvironmentVariablesPrefix()).thenReturn(false);

    assertThat(provider.getVariables())
      .containsExactlyInAnyOrderEntriesOf(Map.of(
        PREFIX + "prefixed", "1",
        PREFIX_MIXED_CASE + "mixed", "2",
        "not-prefixed", "3"
      ));
  }

  @Test
  public void removePrefixDoesNothingIfEmpty() {
    when(config.getLocalEnvironmentVariablesPrefix()).thenReturn("");
    when(config.removeLocalEnvironmentVariablesPrefix()).thenReturn(true);

    assertThat(provider.getVariables())
      .containsExactlyInAnyOrderEntriesOf(Map.of(
        PREFIX + "prefixed", "1",
        PREFIX_MIXED_CASE + "mixed", "2",
        "not-prefixed", "3"
      ));
  }

}