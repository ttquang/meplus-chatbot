package com.ttq.action;

import java.util.Map;

/**
 * What an action produced.
 *
 * @param fields  values to merge into the conversation's collected data, e.g. the ticket number
 * @param message a line appended to the assistant's reply, so facts the model cannot know —
 *                such as a ticket number — are never invented by it. May be null.
 */
public record ActionResult(Map<String, Object> fields, String message) {

    public ActionResult {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
