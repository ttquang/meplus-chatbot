package com.ttq.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param baseUrl          Ollama server the embeddings are requested from
 * @param model            embedding model; stored with each vector so a model change re-embeds everything
 * @param batchSize        products embedded per request to the server
 * @param timeout          how long to wait for one request; the first one also loads the model
 * @param reindexOnStartup embed the catalog when the application starts; unchanged products are skipped
 */
@ConfigurationProperties("chatbot.embedding")
public record EmbeddingProperties(
        @DefaultValue("http://172.24.1.81:11434") String baseUrl,
        @DefaultValue("qwen3-embedding:4b") String model,
        @DefaultValue("16") int batchSize,
        @DefaultValue("PT2M") Duration timeout,
        @DefaultValue("false") boolean reindexOnStartup) {
}
