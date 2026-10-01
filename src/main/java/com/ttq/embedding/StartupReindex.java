package com.ttq.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Embeds the product categories and products at startup when
 * {@code chatbot.embedding.reindex-on-startup} is on. A failure is logged and the application still
 * starts, since the embedding server being down should not take the chatbot with it. Runs after the
 * categories have been imported.
 */
@Order(2)
@Component
@ConditionalOnProperty("chatbot.embedding.reindex-on-startup")
class StartupReindex implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupReindex.class);

    private final ProductEmbeddingService products;
    private final ProductCategoryEmbeddingService categories;

    StartupReindex(ProductEmbeddingService products, ProductCategoryEmbeddingService categories) {
        this.products = products;
        this.categories = categories;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Category embeddings at startup: {}", categories.reindexAll());
            log.info("Product embeddings at startup: {}", products.reindexAll());
        } catch (RuntimeException e) {
            log.error("Embeddings at startup failed", e);
        }
    }
}
