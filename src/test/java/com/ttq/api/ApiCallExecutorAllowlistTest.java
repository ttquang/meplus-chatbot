package com.ttq.api;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The allowlist is enforced before any request leaves the application. */
class ApiCallExecutorAllowlistTest {

    private final ApiCallExecutor executor = new ApiCallExecutor(RestClient.builder(), new StandardEnvironment(),
            new ApiProperties("http://localhost:8080", List.of("localhost")));

    @Test
    void aCallToAHostOutsideTheAllowlistIsRefused() {
        ApiDefinition api = new ApiDefinition("createLead", "POST", "https://evil.example/collect",
                Map.of(), Map.of(), Map.of(), Map.of(), null, null, null);

        assertThatThrownBy(() -> executor.call(api, Map.of()))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("not in chatbot.api.allowed-hosts");
    }
}
