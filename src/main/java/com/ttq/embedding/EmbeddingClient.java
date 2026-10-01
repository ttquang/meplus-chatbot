package com.ttq.embedding;

import java.util.List;

/** Turns text into vectors. */
public interface EmbeddingClient {

    /**
     * @return one vector per text, in the order of {@code texts}
     * @throws EmbeddingException if the vectors could not be obtained
     */
    List<float[]> embed(List<String> texts);
}
