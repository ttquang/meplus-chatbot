package com.ttq.engine;

import com.ttq.action.ActionResult;
import com.ttq.llm.TurnDecision;
import com.ttq.process.FieldDefinition;
import com.ttq.process.FieldOptionsResolver;
import com.ttq.process.ProcessDefinition;
import com.ttq.process.StateDefinition;
import com.ttq.process.Transition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates the model's proposed field values and state transition against the process definition.
 * The model proposes; this policy decides.
 */
@Component
public class TransitionPolicy {

    private final FieldOptionsResolver fieldOptions;

    public TransitionPolicy(FieldOptionsResolver fieldOptions) {
        this.fieldOptions = fieldOptions;
    }

    public TurnOutcome apply(ProcessDefinition process, String currentStateId,
                             Map<String, Object> collectedData, TurnDecision decision) {
        StateDefinition current = process.state(currentStateId);

        Map<String, Object> accepted = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();
        Map<String, Object> merged = new LinkedHashMap<>(collectedData);
        // Checked in the order the process lists its fields, each against options that see the values
        // accepted before it, so a search and a pick from its results can arrive in one message.
        List<String> names = decision.extractedFields().keySet().stream()
                .sorted(Comparator.comparingInt(name -> fieldPosition(process, name)))
                .toList();
        for (String name : names) {
            Object raw = decision.extractedFields().get(name);
            // Enum options may come from an API, so values are checked against what it returned.
            // Read-only fields belong to actions; the model may not set them.
            Optional<Object> value = process.findField(name)
                    .filter(f -> !f.readOnly())
                    .flatMap(f -> f.normalize(raw, fieldOptions.options(f, merged).values()));
            if (value.isPresent()) {
                accepted.put(name, value.get());
                merged.put(name, value.get());
            } else if (raw != null) {
                ignored.add(name);
            }
        }
        List<String> missing = current.missingFields(merged);

        String requested = blankToNull(decision.nextState());
        if (current.id().equals(requested)) {
            requested = null;
        }

        String target = current.id();
        String note = null;
        if (current.terminal()) {
            // Completed conversations never move again.
        } else if (requested != null) {
            Optional<Transition> transition = current.findTransition(requested);
            if (transition.isEmpty()) {
                note = "Rejected transition to '%s': not an allowed next state".formatted(requested);
            } else if (!missing.isEmpty() && !transition.get().back()) {
                note = "Rejected transition to '%s': missing required fields %s".formatted(requested, missing);
            } else if (!process.conditionMet(transition.get(), merged)) {
                // The model picked a real branch, but the data points elsewhere. When only one
                // branch is eligible the routing is unambiguous, so take it instead of stalling.
                List<Transition> eligible = process.eligibleTransitions(current, merged);
                if (eligible.size() == 1) {
                    target = eligible.getFirst().state();
                    note = "Redirected transition from '%s' to '%s': condition %s not met"
                            .formatted(requested, target, transition.get().when());
                } else {
                    note = "Rejected transition to '%s': condition %s not met"
                            .formatted(requested, transition.get().when());
                }
            } else {
                target = requested;
            }
        } else if (decision.objectiveMet() && missing.isEmpty()) {
            // Objective met but the model didn't name a state; move when only one branch is eligible.
            List<Transition> eligible = process.eligibleTransitions(current, merged);
            if (eligible.size() == 1) {
                target = eligible.getFirst().state();
            }
        }

        StateDefinition resulting = process.state(target);
        return new TurnOutcome(current.id(), resulting, accepted, ignored, resulting.missingFields(merged), note);
    }

    /** Where the field sits in the process's list; a field the process does not have goes last. */
    private static int fieldPosition(ProcessDefinition process, String name) {
        List<FieldDefinition> fields = process.fields();
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).name().equals(name)) {
                return i;
            }
        }
        return fields.size();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("null") ? null : s.strip();
    }

    /**
     * @param missingFields required fields of the resulting state that are still uncollected
     * @param note          explanation when the model's proposed transition was rejected
     * @param actionMessage line the state's actions asked to append to the reply, if any; when an
     *                      action failed, what to tell the user instead of the generic message
     * @param actionFailed  true when a state's action failed and the transition was cancelled
     */
    public record TurnOutcome(
            String previousState,
            StateDefinition state,
            Map<String, Object> acceptedFields,
            List<String> ignoredFields,
            List<String> missingFields,
            String note,
            String actionMessage,
            boolean actionFailed) {

        public TurnOutcome(String previousState, StateDefinition state, Map<String, Object> acceptedFields,
                           List<String> ignoredFields, List<String> missingFields, String note) {
            this(previousState, state, acceptedFields, ignoredFields, missingFields, note, null, false);
        }

        public boolean transitioned() {
            return !previousState.equals(state.id());
        }

        /**
         * This outcome followed by {@code next}, judged on the data this one left: the fields of both,
         * and the state {@code next} ends in, counted as a move from where this one started.
         */
        public TurnOutcome followedBy(TurnOutcome next) {
            Map<String, Object> fields = new LinkedHashMap<>(acceptedFields);
            fields.putAll(next.acceptedFields());
            List<String> ignored = new ArrayList<>(ignoredFields);
            next.ignoredFields().stream().filter(f -> !ignored.contains(f)).forEach(ignored::add);
            // A field ignored in the first look but accepted in the second is not reported as ignored.
            ignored.removeIf(fields::containsKey);
            String combined = note == null ? next.note() : next.note() == null ? note : note + "; " + next.note();
            return new TurnOutcome(previousState, next.state(), fields, ignored, next.missingFields(), combined);
        }

        /** The same outcome, ending in {@code next} instead, which the conversation moved on to by itself. */
        public TurnOutcome advancedTo(StateDefinition next, Map<String, Object> collectedData) {
            return new TurnOutcome(previousState, next, acceptedFields, ignoredFields,
                    next.missingFields(collectedData), note, actionMessage, false);
        }

        /** The same outcome, plus whatever one of the state's actions produced. */
        public TurnOutcome with(ActionResult result) {
            Map<String, Object> fields = new LinkedHashMap<>(acceptedFields);
            fields.putAll(result.fields());
            String message = actionMessage == null ? result.message()
                    : result.message() == null ? actionMessage : actionMessage + "\n\n" + result.message();
            return new TurnOutcome(previousState, state, fields, ignoredFields, missingFields, note, message, false);
        }

        /** The transition undone because the state's action failed; collected fields are kept. */
        public TurnOutcome cancelled(StateDefinition stayIn, Map<String, Object> collectedData, String reason) {
            return cancelled(stayIn, collectedData, reason, null, List.of());
        }

        /**
         * The transition undone because the state's action failed. Collected fields are kept except
         * {@code fieldsToClear}, which are stored as null so they count as missing again.
         *
         * @param userMessage what to tell the user, carried in {@code actionMessage}; null for the
         *                    generic failure message
         */
        public TurnOutcome cancelled(StateDefinition stayIn, Map<String, Object> collectedData, String reason,
                                     String userMessage, List<String> fieldsToClear) {
            String combined = note == null ? reason : note + "; " + reason;
            Map<String, Object> fields = new LinkedHashMap<>(acceptedFields);
            Map<String, Object> remaining = new LinkedHashMap<>(collectedData);
            fieldsToClear.forEach(f -> {
                fields.put(f, null);
                remaining.put(f, null);
            });
            return new TurnOutcome(previousState, stayIn, fields, ignoredFields,
                    stayIn.missingFields(remaining), combined, userMessage, true);
        }
    }
}
