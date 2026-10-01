package com.ttq.api;

import com.ttq.ChatbotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The APIs a process may call, loaded from YAML at startup. Keeping them here rather than in the
 * process definitions means one place owns endpoints, payload mapping and credentials, and several
 * processes can share the same call.
 */
@Component
public class ApiCatalog {

    private static final Logger log = LoggerFactory.getLogger(ApiCatalog.class);
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final Map<String, ApiDefinition> apis;

    public ApiCatalog(ChatbotProperties properties, ApiProperties apiProperties, Environment environment)
            throws IOException {
        this.apis = load(properties.apisLocation());
        checkHostsAllowed(apiProperties, environment);
    }

    /**
     * Reports a url pointing at a host we may not call as early as possible. Urls whose
     * placeholders cannot be resolved yet are left to the allowlist check made at call time.
     */
    private void checkHostsAllowed(ApiProperties apiProperties, Environment environment) {
        List<String> problems = new ArrayList<>();
        for (ApiDefinition api : apis.values()) {
            String url;
            try {
                url = ApiCallExecutor.withSampleSegments(environment.resolveRequiredPlaceholders(api.url()));
            } catch (RuntimeException e) {
                log.debug("Api '{}' url is resolved at call time: {}", api.id(), e.getMessage());
                continue;
            }
            try {
                apiProperties.verifyAllowed(api.id(), URI.create(url));
            } catch (RuntimeException e) {
                problems.add(e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidApiDefinitionException("api catalog", problems);
        }
        log.info("Loaded {} api definitions; allowed hosts: {}", apis.size(), apiProperties.allowedHosts());
    }

    public Collection<ApiDefinition> all() {
        return apis.values();
    }

    public Optional<ApiDefinition> find(String id) {
        return Optional.ofNullable(apis.get(id));
    }

    public ApiDefinition get(String id) {
        return find(id).orElseThrow(() -> new ApiNotFoundException(id));
    }

    private Map<String, ApiDefinition> load(String location) throws IOException {
        Map<String, ApiDefinition> result = new LinkedHashMap<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources(location)) {
            for (ApiDefinition api : parse(resource)) {
                List<String> errors = validate(api);
                if (!errors.isEmpty()) {
                    throw new InvalidApiDefinitionException(resource.getDescription(), errors);
                }
                if (result.putIfAbsent(api.id(), api) != null) {
                    throw new InvalidApiDefinitionException(resource.getDescription(),
                            List.of("duplicate api id '" + api.id() + "'"));
                }
            }
            log.info("Loaded api definitions from {}", resource.getFilename());
        }
        if (result.isEmpty()) {
            log.warn("No api definitions found at {}", location);
        }
        return result;
    }

    private List<ApiDefinition> parse(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            Object yaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            ApiFile file = mapper.convertValue(yaml, ApiFile.class);
            return file.apis() == null ? List.of() : file.apis();
        } catch (RuntimeException e) {
            throw new InvalidApiDefinitionException(resource.getDescription(), List.of(e.getMessage()));
        }
    }

    static List<String> validate(ApiDefinition api) {
        List<String> errors = new ArrayList<>();
        String prefix = "api '" + api.id() + "': ";
        if (api.id() == null || api.id().isBlank()) {
            errors.add("'id' is required");
        }
        if (api.url() == null || api.url().isBlank()) {
            errors.add(prefix + "'url' is required");
        } else if (!api.url().startsWith("http://") && !api.url().startsWith("https://")
                && !api.url().startsWith("${")) {
            errors.add(prefix + "'url' must be http(s), got '" + api.url() + "'");
        }
        if (api.url() != null && hasTemplateOutsidePath(api.url())) {
            errors.add(prefix + "{{field}} is only allowed in the url path; use 'query' for query parameters");
        }
        if (!METHODS.contains(api.method())) {
            errors.add(prefix + "unsupported method '" + api.method() + "'");
        }
        api.body().forEach((name, mapping) -> {
            if (mapping.from() == null && mapping.value() == null) {
                errors.add(prefix + "body field '" + name + "' needs 'from' or 'value'");
            }
        });
        api.query().forEach((name, mapping) -> {
            if (mapping.from() == null && mapping.value() == null) {
                errors.add(prefix + "query parameter '" + name + "' needs 'from' or 'value'");
            }
        });
        return errors;
    }

    /** True when a {{field}} sits in the host or query, where encoding a path segment is not enough. */
    private static boolean hasTemplateOutsidePath(String url) {
        int template = url.indexOf("{{");
        if (template < 0) {
            return false;
        }
        int query = url.indexOf('?');
        if (query >= 0 && query < url.lastIndexOf("{{")) {
            return true;
        }
        // Host part: everything before the first single slash after the scheme or placeholder.
        String afterScheme = url.replaceFirst("^(https?://|\\$\\{[^}]*})", "");
        int pathStart = afterScheme.indexOf('/');
        return pathStart < 0 || afterScheme.substring(0, pathStart).contains("{{");
    }

    /** Wrapper so a file can hold several definitions under a single {@code apis:} key. */
    record ApiFile(List<ApiDefinition> apis) {
    }
}
