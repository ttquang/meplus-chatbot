package com.ttq.process;

import com.ttq.api.ApiCallExecutor;
import com.ttq.api.ApiCatalog;
import com.ttq.api.ApiDefinition;
import com.ttq.api.ApiDefinition.OptionsMapping;
import com.ttq.api.ApiDefinition.ValueMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Works out which values a field currently accepts. Fields with fixed {@code values} answer from
 * the process definition; fields with {@code valuesFrom} call the catalog API, whose answer is
 * cached briefly so a lookup does not run on every turn.
 *
 * <p>When the lookup fails the last cached answer is used if there is one; otherwise the field is
 * reported as having no options, which keeps the value uncollected rather than accepting anything.
 */
@Component
public class FieldOptionsResolver {

    private static final Logger log = LoggerFactory.getLogger(FieldOptionsResolver.class);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final ApiCatalog apis;
    private final ApiCallExecutor executor;
    private final Map<String, CachedOptions> cache = new ConcurrentHashMap<>();

    public FieldOptionsResolver(ApiCatalog apis, ApiCallExecutor executor) {
        this.apis = apis;
        this.executor = executor;
    }

    public FieldOptions options(FieldDefinition field, Map<String, Object> collectedData) {
        if (field.valuesFrom() == null) {
            return FieldOptions.fixed(field.values());
        }
        ApiDefinition api = apis.get(field.valuesFrom());
        // Not asked yet: a lookup that narrows by an uncollected field, such as the variants of a
        // product not chosen yet, has no options rather than failing on every turn.
        if (!hasRequiredQuery(api, collectedData)) {
            return new FieldOptions(List.of(), true);
        }
        String key = cacheKey(api, collectedData);
        CachedOptions cached = cache.get(key);
        if (cached != null && cached.isFresh(ttl(api))) {
            return cached.options();
        }
        try {
            FieldOptions fresh = new FieldOptions(read(api, executor.call(api, collectedData)), true);
            cache.put(key, new CachedOptions(fresh, Instant.now()));
            return fresh;
        } catch (RuntimeException e) {
            if (cached != null) {
                log.warn("Lookup '{}' for field '{}' failed, using cached options: {}", api.id(), field.name(),
                        e.getMessage());
                return cached.options();
            }
            log.error("Lookup '{}' for field '{}' failed and nothing is cached", api.id(), field.name(), e);
            return new FieldOptions(List.of(), true);
        }
    }

    /** True when the field's options are looked up with the value of one of {@code fieldNames}. */
    public boolean dependsOn(FieldDefinition field, Collection<String> fieldNames) {
        if (field.valuesFrom() == null) {
            return false;
        }
        return requestValues(apis.get(field.valuesFrom()))
                .anyMatch(m -> m.from() != null && fieldNames.contains(m.from()));
    }

    private static boolean hasRequiredQuery(ApiDefinition api, Map<String, Object> collectedData) {
        return requestValues(api)
                .allMatch(m -> !m.required() || m.value() != null || collectedData.get(m.from()) != null);
    }

    /** Where the request's values come from: its query parameters and, for a POST, its body. */
    private static Stream<ValueMapping> requestValues(ApiDefinition api) {
        return Stream.concat(api.query().values().stream(), api.body().values().stream());
    }

    /** Values that the API's query parameters and body depend on, so each variant is cached separately. */
    private static String cacheKey(ApiDefinition api, Map<String, Object> collectedData) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        api.query().forEach((name, mapping) -> parameters.put(name, valueOf(mapping, collectedData)));
        api.body().forEach((name, mapping) -> parameters.put("body." + name, valueOf(mapping, collectedData)));
        return api.id() + parameters;
    }

    private static Object valueOf(ValueMapping mapping, Map<String, Object> collectedData) {
        return mapping.value() != null ? mapping.value() : collectedData.get(mapping.from());
    }

    private static Duration ttl(ApiDefinition api) {
        return api.cacheSeconds() == null ? DEFAULT_TTL : Duration.ofSeconds(api.cacheSeconds());
    }

    private static List<FieldOptions.Option> read(ApiDefinition api, Object response) {
        OptionsMapping mapping = api.options() == null ? new OptionsMapping("", "code", "label") : api.options();
        Object list = ApiCallExecutor.path(response, mapping.path());
        if (!(list instanceof List<?> items)) {
            throw new IllegalStateException("Api '%s' did not return a list of options".formatted(api.id()));
        }
        List<FieldOptions.Option> options = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                Object code = map.get(mapping.code());
                if (code != null) {
                    options.add(new FieldOptions.Option(code.toString(), text(map, mapping.label()),
                            text(map, mapping.group()), count(map, mapping.count()),
                            text(map, mapping.description()), text(map, mapping.guidance())));
                }
            } else if (item != null) {
                options.add(new FieldOptions.Option(item.toString(), null));
            }
        }
        return options;
    }

    private static String text(Map<?, ?> item, String property) {
        Object value = property == null ? null : ApiCallExecutor.path(item, property);
        return value == null ? null : value.toString();
    }

    private static Long count(Map<?, ?> item, String property) {
        Object value = property == null ? null : ApiCallExecutor.path(item, property);
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(value.toString().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record CachedOptions(FieldOptions options, Instant loadedAt) {

        boolean isFresh(Duration ttl) {
            return Instant.now().isBefore(loadedAt.plus(ttl));
        }
    }
}
