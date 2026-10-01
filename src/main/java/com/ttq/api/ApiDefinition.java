package com.ttq.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One entry of the API catalog: an HTTP call the application can make on a process's behalf,
 * described in configuration rather than written in Java.
 *
 * @param url      may contain {@code ${property}} placeholders, resolved when the call is made
 * @param headers  values may contain {@code {{field}}} templates
 * @param query    query parameters built from collected data
 * @param body     JSON body built from collected data; ignored for GET
 * @param capture  fields to read out of the response, as {@code fieldName -> dotted.path}
 * @param message  line appended to the assistant's reply, with {{field}} templates
 * @param options  set when this API returns a list of allowed values for a field
 * @param rejectedMessage  shown to the user instead of the generic failure message when the API
 *                         refuses the request with a 4xx, e.g. a wrong one-time code
 * @param clearOnRejection collected fields to forget when the API refuses the request, so the
 *                         assistant asks for them again instead of resending a bad value
 */
public record ApiDefinition(
        String id,
        String method,
        String url,
        Map<String, String> headers,
        Map<String, ValueMapping> query,
        Map<String, ValueMapping> body,
        Map<String, String> capture,
        String message,
        OptionsMapping options,
        Integer cacheSeconds,
        String rejectedMessage,
        List<String> clearOnRejection) {

    public ApiDefinition {
        method = method == null || method.isBlank() ? "POST" : method.strip().toUpperCase();
        headers = copy(headers);
        query = copy(query);
        body = copy(body);
        capture = copy(capture);
        clearOnRejection = clearOnRejection == null ? List.of() : List.copyOf(clearOnRejection);
    }

    /** An API whose refusals are reported like any other failure. */
    public ApiDefinition(String id, String method, String url, Map<String, String> headers,
                         Map<String, ValueMapping> query, Map<String, ValueMapping> body,
                         Map<String, String> capture, String message, OptionsMapping options,
                         Integer cacheSeconds) {
        this(id, method, url, headers, query, body, capture, message, options, cacheSeconds, null, null);
    }

    private static <V> Map<String, V> copy(Map<String, V> map) {
        return map == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(map));
    }

    public boolean hasBody() {
        return !"GET".equals(method) && !"DELETE".equals(method);
    }

    /**
     * Where one request value comes from.
     *
     * @param from     name of a collected field, or a context value such as {@code $conversationId}
     * @param value    a constant, used instead of {@code from}
     * @param required fail the call when the field has not been collected
     */
    public record ValueMapping(String from, Object value, boolean required) {
    }

    /**
     * How to read a list of allowed field values out of a response.
     *
     * @param path        dotted path to the array, empty when the response is the array itself
     * @param code        property holding the value to store, e.g. {@code code}
     * @param label       property holding the text shown to the user, e.g. {@code label}
     * @param group       property holding the group a value belongs to, for a tags field; not read when null
     * @param count       property holding how many results the value would leave; not read when null
     * @param description property holding other words for the value; not read when null
     * @param guidance    property holding how to choose between the values of the value's group; not read when null
     */
    public record OptionsMapping(String path, String code, String label, String group, String count,
                                 String description, String guidance) {

        public OptionsMapping {
            path = path == null ? "" : path;
            code = code == null || code.isBlank() ? "code" : code;
            label = label == null || label.isBlank() ? "label" : label;
        }

        public OptionsMapping(String path, String code, String label) {
            this(path, code, label, null, null, null, null);
        }

        public OptionsMapping(String path, String code, String label, String group, String count,
                              String description) {
            this(path, code, label, group, count, description, null);
        }
    }
}
