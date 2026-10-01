package com.ttq.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Map;

/**
 * Structured output the model returns for every user turn.
 */
public record TurnDecision(
        @JsonPropertyDescription("The message to show the user.")
        String reply,

        @JsonPropertyDescription("Values for process fields found in the user's latest message, keyed by field name. "
                + "Only include fields the user actually provided or corrected.")
        Map<String, Object> extractedFields,

        @JsonPropertyDescription("True if the objective of the current state has been achieved.")
        boolean objectiveMet,

        @JsonPropertyDescription("Id of the state to move to, chosen from the allowed next states, "
                + "or null to stay in the current state.")
        String nextState,

        @JsonPropertyDescription("Id of another process to hand the conversation over to, chosen from the "
                + "other processes listed, when the user clearly wants that instead; otherwise null.")
        String switchProcess,

        @JsonPropertyDescription("Name of the single field your reply asks the user for, or null when the reply "
                + "asks for something else, asks for several fields at once, or asks for nothing.")
        String asking) {

    public TurnDecision {
        extractedFields = extractedFields == null ? Map.of() : extractedFields;
    }

    /** A decision that stays within the current process. */
    public TurnDecision(String reply, Map<String, Object> extractedFields, boolean objectiveMet, String nextState) {
        this(reply, extractedFields, objectiveMet, nextState, null);
    }

    /** A decision that does not say which field it asks for. */
    public TurnDecision(String reply, Map<String, Object> extractedFields, boolean objectiveMet, String nextState,
                        String switchProcess) {
        this(reply, extractedFields, objectiveMet, nextState, switchProcess, null);
    }
}
