package com.ttq.process;

import com.ttq.ChatbotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads and validates process definitions from YAML files at startup.
 */
@Component
public class ProcessRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProcessRegistry.class);

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final Map<String, ProcessDefinition> processes;

    public ProcessRegistry(ChatbotProperties properties) throws IOException {
        this.processes = load(properties.processesLocation());
        String defaultProcess = properties.defaultProcess();
        if (defaultProcess != null && !defaultProcess.isBlank() && !processes.containsKey(defaultProcess)) {
            throw new IllegalStateException("chatbot.default-process '%s' is not a loaded process"
                    .formatted(defaultProcess));
        }
    }

    public Collection<ProcessDefinition> all() {
        return processes.values();
    }

    public Optional<ProcessDefinition> find(String id) {
        return Optional.ofNullable(processes.get(id));
    }

    public ProcessDefinition get(String id) {
        return find(id).orElseThrow(() -> new ProcessNotFoundException(id));
    }

    private Map<String, ProcessDefinition> load(String location) throws IOException {
        Map<String, ProcessDefinition> result = new LinkedHashMap<>();
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(location);
        for (Resource resource : resources) {
            ProcessDefinition process = parse(resource);
            List<String> errors = validate(process);
            if (!errors.isEmpty()) {
                throw new InvalidProcessDefinitionException(resource.getDescription(), errors);
            }
            if (result.putIfAbsent(process.id(), process) != null) {
                throw new InvalidProcessDefinitionException(resource.getDescription(),
                        List.of("duplicate process id '" + process.id() + "'"));
            }
            log.info("Loaded process '{}' with {} states from {}", process.id(), process.states().size(),
                    resource.getFilename());
        }
        if (result.isEmpty()) {
            log.warn("No process definitions found at {}", location);
        }
        // Checked once all are loaded, since a process may name one defined in a later file.
        for (ProcessDefinition process : result.values()) {
            List<String> unknown = process.switchTo().stream().filter(id -> !result.containsKey(id)).toList();
            if (!unknown.isEmpty()) {
                throw new InvalidProcessDefinitionException("process '" + process.id() + "'",
                        List.of("unknown process in 'switchTo': " + unknown));
            }
        }
        return result;
    }

    ProcessDefinition parse(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            Object yaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            return mapper.convertValue(yaml, ProcessDefinition.class);
        } catch (RuntimeException e) {
            throw new InvalidProcessDefinitionException(resource.getDescription(), List.of(e.getMessage()));
        }
    }

    static List<String> validate(ProcessDefinition process) {
        List<String> errors = new ArrayList<>();
        if (isBlank(process.id())) errors.add("'id' is required");
        if (isBlank(process.objective())) errors.add("'objective' is required");
        if (process.states().isEmpty()) errors.add("at least one state is required");

        Set<String> fieldNames = new HashSet<>();
        for (FieldDefinition field : process.fields()) {
            if (isBlank(field.name())) {
                errors.add("every field needs a 'name'");
            } else if (!fieldNames.add(field.name())) {
                errors.add("duplicate field '" + field.name() + "'");
            }
            // An enum or tags field either lists its values here or names an api that returns them.
            if (field.hasListedValues() && field.values().isEmpty() && field.valuesFrom() == null) {
                errors.add("%s field '%s' needs 'values' or 'valuesFrom'"
                        .formatted(field.type().name().toLowerCase(), field.name()));
            }
            if (field.valuesFrom() != null && !field.values().isEmpty()) {
                errors.add("field '" + field.name() + "' cannot have both 'values' and 'valuesFrom'");
            }
            if (field.valuesFrom() != null && !field.hasListedValues()) {
                errors.add("field '" + field.name() + "' uses 'valuesFrom' but is not an enum or tags");
            }
        }

        if (process.switchTo().contains(process.id())) {
            errors.add("'switchTo' cannot name the process itself");
        }

        Set<String> stateIds = new HashSet<>();
        for (StateDefinition state : process.states()) {
            if (isBlank(state.id()) || !stateIds.add(state.id())) {
                errors.add("state ids must be present and unique (got '" + state.id() + "')");
            }
        }
        if (isBlank(process.initialState()) || !stateIds.contains(process.initialState())) {
            errors.add("'initialState' must reference an existing state");
        }

        for (StateDefinition state : process.states()) {
            String prefix = "state '" + state.id() + "': ";
            if (isBlank(state.objective())) errors.add(prefix + "'objective' is required");
            if (state.terminal() && !state.next().isEmpty()) errors.add(prefix + "terminal states cannot have 'next'");
            // A process that can switch may have a state it only leaves by handing over, such as one
            // that works out what the user wants.
            if (!state.terminal() && state.next().isEmpty() && process.switchTo().isEmpty()) {
                errors.add(prefix + "non-terminal states need 'next'");
            }

            Set<String> targets = new HashSet<>();
            for (Transition transition : state.next()) {
                if (!stateIds.contains(transition.state())) {
                    errors.add(prefix + "unknown next state '" + transition.state() + "'");
                } else if (!targets.add(transition.state())) {
                    errors.add(prefix + "duplicate next state '" + transition.state() + "'");
                }
                errors.addAll(validateCondition(process, prefix + "transition to '" + transition.state() + "': ",
                        transition.when()));
            }

            for (FollowUp followUp : state.followUp()) {
                if (isBlank(followUp.message())) {
                    errors.add(prefix + "every 'followUp' needs a 'message'");
                }
                errors.addAll(validateCondition(process, prefix + "followUp: ", followUp.when()));
            }

            state.requiredFields().stream()
                    .filter(f -> !fieldNames.contains(f))
                    .forEach(f -> errors.add(prefix + "unknown required field '" + f + "'"));
            state.optionalFields().stream()
                    .filter(f -> !fieldNames.contains(f))
                    .forEach(f -> errors.add(prefix + "unknown optional field '" + f + "'"));
        }
        return errors;
    }

    /** Checks that a 'when' names known fields and holds values those fields accept. */
    private static List<String> validateCondition(ProcessDefinition process, String where,
                                                  Map<String, Object> when) {
        List<String> errors = new ArrayList<>();
        when.forEach((fieldName, expected) -> {
            Optional<FieldDefinition> field = process.findField(fieldName);
            if (field.isEmpty()) {
                errors.add(where + "unknown field '" + fieldName + "' in 'when'");
                return;
            }
            List<?> values = expected instanceof Collection<?> c ? List.copyOf(c) : Collections.singletonList(expected);
            if (values.isEmpty()) {
                errors.add(where + "'when' condition on '" + fieldName + "' has no value");
            }
            values.stream()
                    .filter(v -> !field.get().isValidConditionValue(v))
                    .forEach(v -> errors.add(
                            where + "invalid value '" + v + "' for field '" + fieldName + "' in 'when'"));
        });
        return errors;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
