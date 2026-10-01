package com.ttq.llm;

import com.ttq.conversation.ChatMessage;
import com.ttq.process.ProcessDefinition;
import com.ttq.process.StateDefinition;

import java.util.List;
import java.util.Map;

/**
 * Everything the model needs to produce the next turn.
 *
 * @param history     prior messages, oldest first, excluding {@code userMessage}
 * @param userMessage the user's latest message
 * @param switchTargets other processes the conversation may be handed over to; empty when it cannot switch
 */
public record TurnContext(
        ProcessDefinition process,
        StateDefinition state,
        Map<String, Object> collectedData,
        List<ChatMessage> history,
        String userMessage,
        List<ProcessDefinition> switchTargets) {

    public TurnContext {
        switchTargets = switchTargets == null ? List.of() : List.copyOf(switchTargets);
    }
}
