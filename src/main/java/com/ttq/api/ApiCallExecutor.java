package com.ttq.api;

import com.ttq.api.ApiDefinition.ValueMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes the call described by an {@link ApiDefinition}, filling request values from the
 * conversation's collected data.
 *
 * <p>Placeholders are resolved late so that {@code ${...}} in a url picks up the environment at
 * call time, and {@code {{field}}} in headers or messages picks up the conversation's data.
 * {@code {{field}}} in a url path fills one path segment, encoded so a value can never add
 * segments, a query or another host.
 */
@Component
public class ApiCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(ApiCallExecutor.class);
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([^}\\s]+)\\s*}}");

    private final RestClient restClient;
    private final Environment environment;
    private final ApiProperties properties;

    public ApiCallExecutor(RestClient.Builder restClientBuilder, Environment environment,
                           ApiProperties properties) {
        this.restClient = restClientBuilder.build();
        this.environment = environment;
        this.properties = properties;
    }

    /**
     * @param values collected data plus context values such as {@code $conversationId}
     * @return the parsed response body, a Map or a List depending on the API
     */
    public Object call(ApiDefinition api, Map<String, Object> values) {
        URI url = buildUrl(api, values);
        Map<String, Object> body = api.hasBody() ? buildBody(api, values) : null;
        try {
            RestClient.RequestBodySpec request = restClient.method(
                            org.springframework.http.HttpMethod.valueOf(api.method()))
                    .uri(url);
            api.headers().forEach((name, value) -> request.header(name, template(value, values)));
            if (body != null) {
                request.body(body);
            }
            Object response = request.retrieve().body(Object.class);
            log.debug("Called api '{}' {} {}", api.id(), api.method(), url);
            return response;
        } catch (ApiCallException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiCallException("Call to api '%s' (%s %s) failed: %s"
                    .formatted(api.id(), api.method(), url, e.getMessage()), e);
        }
    }

    /**
     * Every value is encoded here and the result handed to the client as a {@link URI}, so nothing
     * is encoded twice and a value cannot change the url's structure.
     */
    private URI buildUrl(ApiDefinition api, Map<String, Object> values) {
        String resolved = pathTemplate(api, environment.resolveRequiredPlaceholders(api.url()), values);
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(resolved);
        api.query().forEach((name, mapping) -> {
            Object value = resolve(api, name, mapping, values);
            // A list, such as the tags a customer picked, is sent as the parameter repeated.
            Collection<?> items = value instanceof Collection<?> c ? c : value == null ? List.of() : List.of(value);
            for (Object item : items) {
                if (item == null) {
                    continue;
                }
                uri.queryParam(UriUtils.encodeQueryParam(name, StandardCharsets.UTF_8),
                        UriUtils.encodeQueryParam(item.toString(), StandardCharsets.UTF_8));
            }
        });
        URI url = uri.build(true).toUri();
        // Checked after placeholders are filled in, since that is what actually gets called.
        properties.verifyAllowed(api.id(), url);
        return url;
    }

    /** Fills {{field}} in the url path with the encoded value; every such field is required. */
    private static String pathTemplate(ApiDefinition api, String url, Map<String, Object> values) {
        Matcher matcher = TEMPLATE.matcher(url);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            if (value == null || value.toString().isBlank()) {
                throw new ApiCallException("Api '%s' needs '%s' in its url but it was not collected"
                        .formatted(api.id(), matcher.group(1)), null);
            }
            String segment = UriUtils.encodePathSegment(value.toString().strip(), StandardCharsets.UTF_8);
            matcher.appendReplacement(result, Matcher.quoteReplacement(segment));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** The url with each {{field}} replaced by a sample segment, for checks made before any call. */
    static String withSampleSegments(String url) {
        return TEMPLATE.matcher(url).replaceAll("sample");
    }

    private Map<String, Object> buildBody(ApiDefinition api, Map<String, Object> values) {
        Map<String, Object> body = new LinkedHashMap<>();
        api.body().forEach((name, mapping) -> {
            Object value = resolve(api, name, mapping, values);
            if (value != null) {
                body.put(name, value);
            }
        });
        return body;
    }

    private Object resolve(ApiDefinition api, String name, ValueMapping mapping, Map<String, Object> values) {
        Object value = mapping.value() != null ? mapping.value() : values.get(mapping.from());
        if (value == null && mapping.required()) {
            throw new ApiCallException("Api '%s' needs '%s' but field '%s' was not collected"
                    .formatted(api.id(), name, mapping.from()), null);
        }
        return value;
    }

    /** Replaces {{field}} with the collected value, leaving unknown fields blank. */
    public static String template(String text, Map<String, Object> values) {
        if (text == null || !text.contains("{{")) {
            return text;
        }
        Matcher matcher = TEMPLATE.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Reads a dotted path such as {@code data.ticketNumber} out of a parsed response. */
    public static Object path(Object response, String path) {
        Object current = response;
        if (path == null || path.isBlank()) {
            return current;
        }
        for (String segment : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else if (current instanceof List<?> list && segment.matches("\\d+")) {
                int index = Integer.parseInt(segment);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
        }
        return current;
    }
}
