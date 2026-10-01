package com.ttq.api;

/** Thrown when a catalog API call could not be made or did not succeed. */
public class ApiCallException extends RuntimeException {

    public ApiCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
