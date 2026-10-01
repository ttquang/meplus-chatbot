package com.ttq.api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiPropertiesTest {

    private static ApiProperties allowing(String... hosts) {
        return new ApiProperties("http://localhost:8080", List.of(hosts));
    }

    @Test
    void allowsExactHostsAndWildcardDomains() {
        ApiProperties properties = allowing("localhost", "*.bank.example");

        assertThat(properties.allows("localhost", 8080)).isTrue();
        assertThat(properties.allows("api.bank.example", -1)).isTrue();
        assertThat(properties.allows("bank.example", -1)).isTrue();
        assertThat(properties.allows("API.BANK.EXAMPLE", 443)).isTrue();

        assertThat(properties.allows("evil.example", -1)).isFalse();
        assertThat(properties.allows("bank.example.evil.com", -1)).isFalse();
        assertThat(properties.allows("notbank.example", -1)).isFalse();
    }

    @Test
    void anEntryWithAPortPinsThatPort() {
        ApiProperties properties = allowing("api.bank.example:8443");

        assertThat(properties.allows("api.bank.example", 8443)).isTrue();
        assertThat(properties.allows("api.bank.example", 443)).isFalse();
        assertThat(properties.allows("api.bank.example", -1)).isFalse();
    }

    @Test
    void refusesHostsOutsideTheListAndNonHttpSchemes() {
        ApiProperties properties = allowing("localhost");

        assertThatThrownBy(() -> properties.verifyAllowed("createLead", URI.create("https://evil.example/leads")))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("evil.example")
                .hasMessageContaining("chatbot.api.allowed-hosts");

        assertThatThrownBy(() -> properties.verifyAllowed("readFile", URI.create("file:///etc/passwd")))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("not http(s)");
    }

    @Test
    void allowsAHostThatIsListed() {
        allowing("localhost").verifyAllowed("createLead", URI.create("http://localhost:62345/api/leads/individual"));
    }
}
