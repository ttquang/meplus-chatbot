package com.ttq.action;

/**
 * Something the application does when a conversation enters a state, such as creating a lead.
 *
 * <p>Actions run outside the database transaction of the turn, like the model call, and must be
 * safe to retry: the same conversation entering the same state twice should not produce two
 * records. A failure is reported by throwing {@link ActionFailedException}, which cancels the
 * transition so the conversation stays where it was.
 */
public interface ProcessAction {

    /** The name used in a process definition's {@code action:} field. */
    String name();

    ActionResult execute(ActionContext context);
}
