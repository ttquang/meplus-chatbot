package com.ttq.embedding;

/** The embedding server could not produce the vectors asked for. */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
