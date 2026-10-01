package com.ttq.process;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fixed line added to the reply when the conversation enters a state, after the state's actions
 * have run, so it can depend on what they returned. Used for what the model cannot know when it
 * writes its reply, such as a question that only applies to some of the statuses an API returns.
 *
 * <pre>
 * followUp:
 *   - when: { applicationCompleted: false }
 *     message: Would you like to leave a message for your loan advisor?
 * </pre>
 *
 * @param when    conditions on the collected data, as in a transition; empty means always
 * @param message the line to add
 */
public record FollowUp(Map<String, Object> when, String message) {

    public FollowUp {
        // Not Map.copyOf: a null value is invalid but must survive until validation reports it.
        when = when == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(when));
    }
}
