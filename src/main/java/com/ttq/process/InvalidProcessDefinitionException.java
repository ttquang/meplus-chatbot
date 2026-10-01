package com.ttq.process;

import java.util.List;

public class InvalidProcessDefinitionException extends RuntimeException {

    public InvalidProcessDefinitionException(String source, List<String> errors) {
        super("Invalid process definition " + source + ":\n - " + String.join("\n - ", errors));
    }
}
