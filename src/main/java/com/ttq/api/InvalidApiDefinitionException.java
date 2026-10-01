package com.ttq.api;

import java.util.List;

public class InvalidApiDefinitionException extends RuntimeException {

    public InvalidApiDefinitionException(String source, List<String> errors) {
        super("Invalid api definition file [%s]:%s".formatted(source,
                errors.stream().map(e -> "\n - " + e).reduce("", String::concat)));
    }
}
