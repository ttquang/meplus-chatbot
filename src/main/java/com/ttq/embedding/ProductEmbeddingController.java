package com.ttq.embedding;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-embeddings")
public class ProductEmbeddingController {

    private final ProductEmbeddingService service;

    public ProductEmbeddingController(ProductEmbeddingService service) {
        this.service = service;
    }

    /**
     * Embeds the products that are new or changed since they were last embedded. Returns when done,
     * which for a first run over a large catalog can take minutes.
     */
    @PostMapping("/reindex")
    public ProductEmbeddingService.Result reindex() {
        return service.reindexAll();
    }
}
