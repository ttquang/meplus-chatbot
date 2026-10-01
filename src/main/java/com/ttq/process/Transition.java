package com.ttq.process;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An edge from one state to another, optionally guarded by conditions on the collected data.
 *
 * <p>In YAML a transition is either a plain state id or an object with a {@code when} map:
 * <pre>
 * next: [contact_details]
 *
 * next:
 *   - state: company_profile

 *     when: { wantsToApply: true, customerType: COMPANY }
 *   - state: confirm
 *     back: true
 * </pre>
 *
 * @param when field name to expected value, or to a list of accepted values; every entry must
 *             match for the transition to be eligible. Empty means always eligible.
 * @param back a way back to an earlier step, which may be taken before the current state's required
 *             fields are collected, e.g. to change a phone number while waiting for its code
 */
public record Transition(String state, Map<String, Object> when, boolean back) {

    public Transition {
        // Not Map.copyOf: a null value is invalid but must survive until validation reports it.
        when = when == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(when));
    }

    public Transition(String state, Map<String, Object> when) {
        this(state, when, false);
    }

    /** Reads the short form, where a transition is just the target state id. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Transition of(String state) {
        return new Transition(state, Map.of());
    }
}
