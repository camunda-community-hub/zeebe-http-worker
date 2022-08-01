package io.zeebe.http.variables;

import io.zeebe.http.ZeebeHttpWorkerConfig;
import java.util.Map;
import java.util.stream.Collectors;

public class LocalVariablesProvider implements EnvironmentVariablesProvider {
	private final ZeebeHttpWorkerConfig config;

	protected LocalVariablesProvider(ZeebeHttpWorkerConfig config) {
		this.config = config;
	}

	protected Map<String, String> getRawVariables() {
		return System.getenv();
	}

	/**
	 * @return Only return environment variables starting with the configured prefix
	 *         and (optionally) removing it.
	 */
	public Map<String, String> getVariables() {
		return getRawVariables().entrySet().stream().filter(this::startsWithPrefix)
				.collect(Collectors.toMap(this::extractVariableName, Map.Entry::getValue));
	}

	private Boolean startsWithPrefix(Map.Entry<String, String> entry) {
		String prefix = config.getLocalEnvironmentVariablesPrefix();
		return entry.getKey().toUpperCase().startsWith(prefix.toUpperCase());
	}

	/**
	 * @return If configured to remove prefix, and the variable starts with it (case
	 *         insensitive) removes the prefix from the name.
	 */
	private String extractVariableName(Map.Entry<String, String> entry) {
		String prefix = config.getLocalEnvironmentVariablesPrefix();
		String key = entry.getKey();

		if (config.removeLocalEnvironmentVariablesPrefix() && startsWithPrefix(entry)) {
			return key.substring(prefix.length());
		} else {
			return key;
		}
	}
}
