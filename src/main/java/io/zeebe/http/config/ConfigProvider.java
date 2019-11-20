package io.zeebe.http.config;

import java.util.Map;

public interface ConfigProvider {

    Map<String, String> getVariables();

}
