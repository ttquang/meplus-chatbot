package com.ttq.engine;

import java.util.UUID;

public class ConcurrentTurnException extends RuntimeException {

    public ConcurrentTurnException(UUID id) {
        super("Conversation " + id + " was updated by another request; please retry");
    }
}
