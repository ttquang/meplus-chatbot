package com.ttq.process;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A predefined conversation process: an overall objective plus a graph of states,
 * each with its own objective, the fields it must collect and the states it may move to.
 *
 * @param switchTo ids of other processes the conversation may be handed over to when the user
 *                 turns out to want one of them instead, such as checking a status while applying
 */
public record ProcessDefinition(
        String id,
        String name,
        String description,
        String objective,
        String instructions,
        String greeting,
        List<FieldDefinition> fields,
        String initialState,
        List<StateDefinition> states,
        List<String> switchTo) {

    public ProcessDefinition {
        fields = fields == null ? List.of() : List.copyOf(fields);
        states = states == null ? List.of() : List.copyOf(states);
        switchTo = switchTo == null ? List.of() : List.copyOf(switchTo);
    }

    /** A process that cannot hand the conversation over to another one. */
    public ProcessDefinition(String id, String name, String description, String objective, String instructions,
                             String greeting, List<FieldDefinition> fields, String initialState,
                             List<StateDefinition> states) {
        this(id, name, description, objective, instructions, greeting, fields, initialState, states, null);
    }

    public Optional<StateDefinition> findState(String stateId) {
        return states.stream().filter(s -> s.id().equals(stateId)).findFirst();
    }

    public StateDefinition state(String stateId) {
        return findState(stateId).orElseThrow(() -> new IllegalStateException(
                "Process '%s' has no state '%s'".formatted(id, stateId)));
    }

    public Optional<FieldDefinition> findField(String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst();
    }

    /**
     * True when every condition of the transition matches the collected data. A field that has
     * not been collected never matches, and a condition value may be a list of accepted values.
     */
    public boolean conditionMet(Transition transition, Map<String, Object> collectedData) {
        return conditionMet(transition.when(), collectedData);
    }

    /** True when every entry of a {@code when} map matches the collected data, as for a transition. */
    public boolean conditionMet(Map<String, Object> when, Map<String, Object> collectedData) {
        return when.entrySet().stream().allMatch(e -> conditionMet(e.getKey(), e.getValue(), collectedData));
    }

    /** The follow-up lines of {@code state} whose conditions the collected data satisfies, in order. */
    public List<String> followUpMessages(StateDefinition state, Map<String, Object> collectedData) {
        return state.followUp().stream()
                .filter(f -> conditionMet(f.when(), collectedData))
                .map(f -> f.message().strip())
                .toList();
    }

    /** The transitions of {@code state} whose conditions the collected data satisfies. */
    public List<Transition> eligibleTransitions(StateDefinition state, Map<String, Object> collectedData) {
        return state.next().stream().filter(t -> conditionMet(t, collectedData)).toList();
    }

    private boolean conditionMet(String fieldName, Object expected, Map<String, Object> collectedData) {
        Object actual = collectedData.get(fieldName);
        if (actual == null) {
            return false;
        }
        return findField(fieldName)
                .filter(field -> accepted(expected).stream().anyMatch(v -> field.valuesMatch(v, actual)))
                .isPresent();
    }

    private static Collection<?> accepted(Object expected) {
        if (expected == null) {
            return List.of();
        }
        return expected instanceof Collection<?> values ? values : List.of(expected);
    }
}
