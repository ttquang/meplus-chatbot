package com.ttq.action;

import java.util.List;

/**
 * Thrown when an action could not complete, so the conversation must not move on.
 *
 * <p>{@code userMessage} and {@code fieldsToClear} are set when the failure is the user's to fix,
 * such as a wrong one-time code: the user is told what went wrong instead of the generic apology,
 * and the rejected values are forgotten so the assistant asks for them again.
 */
public class ActionFailedException extends RuntimeException {

    private final String userMessage;
    private final List<String> fieldsToClear;

    public ActionFailedException(String message, Throwable cause) {
        this(message, cause, null, List.of());
    }

    public ActionFailedException(String message, Throwable cause, String userMessage, List<String> fieldsToClear) {
        super(message, cause);
        this.userMessage = userMessage;
        this.fieldsToClear = fieldsToClear == null ? List.of() : List.copyOf(fieldsToClear);
    }

    /** What to tell the user, or null to use the generic failure message. */
    public String userMessage() {
        return userMessage;
    }

    public List<String> fieldsToClear() {
        return fieldsToClear;
    }
}
