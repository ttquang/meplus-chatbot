package com.ttq.process;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One step of a process.
 *
 * @param objective      what the assistant must achieve while in this state
 * @param requiredFields fields that must be collected before leaving this state
 * @param next           transitions this state may take, each optionally guarded by conditions
 * @param terminal       reaching this state completes the conversation
 * @param actions        names of {@code ProcessAction}s to run, in order, when the conversation
 *                       enters this state, such as verifying a one-time code and then creating a
 *                       lead; empty when the state only talks. Written {@code action: name} or
 *                       {@code action: [first, second]} in YAML.
 * @param followUp       lines added to the reply on entering this state, after its actions, each
 *                       only when its conditions hold
 * @param optionalFields fields this state may ask for without needing them to move on, such as
 *                       tags that narrow down a list; asking for one can offer its values as buttons
 * @param secondLook     when a turn in this state collects a value a lookup depends on, such as a
 *                       search, the model is asked again with the new options in view, so its reply
 *                       can name what the lookup found rather than asking blind; costs a second call
 * @param autoAdvance    when this state's own actions collect every field it requires, there is
 *                       nothing to ask, so the conversation moves on through its one forward
 *                       transition at once, such as past a choice that has only one option
 */
public record StateDefinition(
        String id,
        String objective,
        List<String> requiredFields,
        List<Transition> next,
        boolean terminal,
        @JsonProperty("action") @JsonAlias("actions")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> actions,
        List<FollowUp> followUp,
        List<String> optionalFields,
        boolean secondLook,
        boolean autoAdvance) {

    /** A state that only talks, with no action on entry. */
    public StateDefinition(String id, String objective, List<String> requiredFields, List<Transition> next,
                           boolean terminal) {
        this(id, objective, requiredFields, next, terminal, null, null);
    }

    /** A state with no optional fields. */
    public StateDefinition(String id, String objective, List<String> requiredFields, List<Transition> next,
                           boolean terminal, List<String> actions, List<FollowUp> followUp) {
        this(id, objective, requiredFields, next, terminal, actions, followUp, null, false);
    }

    /** A state that does not move on by itself. */
    public StateDefinition(String id, String objective, List<String> requiredFields, List<Transition> next,
                           boolean terminal, List<String> actions, List<FollowUp> followUp,
                           List<String> optionalFields, boolean secondLook) {
        this(id, objective, requiredFields, next, terminal, actions, followUp, optionalFields, secondLook, false);
    }

    public StateDefinition {
        requiredFields = requiredFields == null ? List.of() : List.copyOf(requiredFields);
        next = next == null ? List.of() : List.copyOf(next);
        actions = actions == null ? List.of() : List.copyOf(actions);
        followUp = followUp == null ? List.of() : List.copyOf(followUp);
        optionalFields = optionalFields == null ? List.of() : List.copyOf(optionalFields);
    }

    public boolean hasActions() {
        return !actions.isEmpty();
    }

    public List<String> nextStateIds() {
        return next.stream().map(Transition::state).toList();
    }

    public Optional<Transition> findTransition(String stateId) {
        return next.stream().filter(t -> t.state().equals(stateId)).findFirst();
    }

    public List<String> missingFields(Map<String, Object> collectedData) {
        return requiredFields.stream().filter(f -> collectedData.get(f) == null).toList();
    }
}
