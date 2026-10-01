package com.ttq.engine;

import java.util.UUID;

public class ConversationClosedException extends RuntimeException {

    public ConversationClosedException(UUID id) {
        super("Conversation " + id + " is already completed");
    }
}
