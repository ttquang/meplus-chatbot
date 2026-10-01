package com.ttq.api;

public class ApiNotFoundException extends RuntimeException {

    public ApiNotFoundException(String id) {
        super("No api definition with id '" + id + "'");
    }
}
