package com.ttq.embedding;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Embeds through Ollama's {@code /api/embed}, which takes several inputs in one call. */
@Component
class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient http;
    private final String model;

    OllamaEmbeddingClient(EmbeddingProperties properties, RestClient.Builder builder) {
        this.model = properties.model();
        this.http = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        HttpClientSettings.defaults()
                                .withReadTimeout(properties.timeout())))
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        EmbedResponse response;
        try {
            response = http.post()
                    .uri("/api/embed")
                    .body(new EmbedRequest(model, texts))
                    .retrieve()
                    .body(EmbedResponse.class);
        } catch (RuntimeException e) {
            throw new EmbeddingException("Ollama embed call failed: " + e.getMessage(), e);
        }
        if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
            throw new EmbeddingException("Ollama returned "
                    + (response == null || response.embeddings() == null ? "no" : response.embeddings().size())
                    + " embeddings for " + texts.size() + " inputs", null);
        }
        return response.embeddings();
    }

    private record EmbedRequest(String model, List<String> input) {
    }

    private record EmbedResponse(List<float[]> embeddings) {
    }
}
