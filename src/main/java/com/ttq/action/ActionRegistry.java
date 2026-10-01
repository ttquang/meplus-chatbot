package com.ttq.action;

import com.ttq.api.ApiCallExecutor;
import com.ttq.api.ApiCatalog;
import com.ttq.process.FieldDefinition;
import com.ttq.process.ProcessDefinition;
import com.ttq.process.ProcessRegistry;
import com.ttq.process.StateDefinition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the {@code action:} of a state: a Java {@link ProcessAction} bean when one is
 * registered under that name, otherwise an API from the catalog. Java wins, so a catalog entry can
 * be replaced by real code without touching the process.
 *
 * <p>At startup it checks that every action and every dynamic field source a process refers to
 * actually exists, so a typo stops the application instead of a conversation.
 */
@Component
public class ActionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActionRegistry.class);

    private final Map<String, ProcessAction> beans;
    private final ApiCatalog apis;
    private final ApiCallExecutor executor;
    private final ProcessRegistry processes;

    public ActionRegistry(List<ProcessAction> beans, ApiCatalog apis, ApiCallExecutor executor,
                          ProcessRegistry processes) {
        this.beans = beans.stream().collect(Collectors.toMap(ProcessAction::name, Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Duplicate action name '" + a.name() + "'");
                }, TreeMap::new));
        this.apis = apis;
        this.executor = executor;
        this.processes = processes;
    }

    @PostConstruct
    void verifyProcessReferences() {
        List<String> problems = new ArrayList<>();
        for (ProcessDefinition process : processes.all()) {
            for (StateDefinition state : process.states()) {
                for (String action : state.actions()) {
                    if (find(action).isEmpty()) {
                        problems.add("unknown action '%s' (process '%s', state '%s')"
                                .formatted(action, process.id(), state.id()));
                    }
                }
            }
            for (FieldDefinition field : process.fields()) {
                if (field.valuesFrom() != null && apis.find(field.valuesFrom()).isEmpty()) {
                    problems.add("unknown api '%s' in 'valuesFrom' (process '%s', field '%s')"
                            .formatted(field.valuesFrom(), process.id(), field.name()));
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Process definitions refer to things that do not exist: " + problems
                    + ". Known actions: " + beans.keySet() + ", known apis: "
                    + apis.all().stream().map(a -> a.id()).toList());
        }
        log.info("Actions available: {} java, {} from the api catalog", beans.size(), apis.all().size());
    }

    public Optional<ProcessAction> find(String name) {
        if (beans.containsKey(name)) {
            return Optional.of(beans.get(name));
        }
        return apis.find(name).map(api -> new CatalogApiAction(api, executor));
    }

    public ActionResult run(String name, ActionContext context) {
        return find(name)
                .orElseThrow(() -> new ActionFailedException("No action or api named '" + name + "'", null))
                .execute(context);
    }
}
