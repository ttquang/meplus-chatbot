package com.ttq.embedding;

/** A reindex was asked for while another one is still running. */
public class ReindexInProgressException extends RuntimeException {

    public ReindexInProgressException() {
        super("The product embeddings are already being rebuilt; please try again when that finishes");
    }
}
