package com.ttq.api;

import com.ttq.api.ApiDefinition.ValueMapping;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** {{field}} in a url fills exactly one path segment, and each value is encoded once. */
class ApiUrlTemplateTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ApiCallExecutor executor = new ApiCallExecutor(builder, new StandardEnvironment(),
            new ApiProperties("http://localhost:8080", List.of("localhost")));

    @Test
    void aPathValueIsEncodedAsOneSegment() {
        // '/' and '?' would change the url's structure, so they are encoded; '=' is legal in a segment.
        server.expect(requestTo(URI.create("http://localhost:8080/leads/LD-IND-1%2F..%2Fadmin%3Fx=1/notes")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        executor.call(api("http://localhost:8080/leads/{{ticketNumber}}/notes", Map.of()),
                Map.of("ticketNumber", "LD-IND-1/../admin?x=1"));

        server.verify();
    }

    @Test
    void aQueryValueCannotAddParameters() {
        server.expect(requestTo(URI.create("http://localhost:8080/purposes?customerType=A%26admin%3Dtrue")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        executor.call(api("http://localhost:8080/purposes",
                Map.of("customerType", new ValueMapping("customerType", null, false))),
                Map.of("customerType", "A&admin=true"));

        server.verify();
    }

    @Test
    void aMissingPathValueFailsBeforeAnyCall() {
        assertThatThrownBy(() -> executor.call(api("http://localhost:8080/leads/{{ticketNumber}}/notes", Map.of()),
                Map.of()))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("needs 'ticketNumber' in its url");
        server.verify();
    }

    @Test
    void templatesOutsideThePathAreRejectedWhenTheCatalogLoads() {
        assertThat(ApiCatalog.validate(api("${chatbot.api.base-url}/leads/{{ticketNumber}}/notes", Map.of())))
                .isEmpty();
        assertThat(ApiCatalog.validate(api("https://{{tenant}}.bank.example/leads", Map.of())))
                .singleElement().asString().contains("only allowed in the url path");
        assertThat(ApiCatalog.validate(api("https://bank.example/leads?ref={{ticketNumber}}", Map.of())))
                .singleElement().asString().contains("only allowed in the url path");
    }

    private static ApiDefinition api(String url, Map<String, ValueMapping> query) {
        return new ApiDefinition("test", "GET", url, Map.of(), query, Map.of(), Map.of(), null, null, null);
    }
}
