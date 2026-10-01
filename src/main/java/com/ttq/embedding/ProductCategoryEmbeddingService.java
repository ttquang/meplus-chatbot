package com.ttq.embedding;

import com.ttq.product.ProductCategory;
import com.ttq.product.ProductCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps one embedding per active product category, made from its name and description, and finds
 * the categories closest to a customer's message. Like the product embeddings, a category already
 * embedded from the same text and model is skipped.
 */
@Service
public class ProductCategoryEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ProductCategoryEmbeddingService.class);

    /** Qwen3-embedding reads a query best with a task instruction in front; documents get none. */
    private static final String QUERY_PREFIX =
            "Instruct: Given a customer's message, retrieve the medical supply product categories that match it\nQuery: ";

    private final ProductCategoryRepository categories;
    private final ProductCategoryEmbeddingRepository embeddings;
    private final EmbeddingClient client;
    private final EmbeddingProperties properties;
    private final ReentrantLock running = new ReentrantLock();

    public ProductCategoryEmbeddingService(ProductCategoryRepository categories,
                                           ProductCategoryEmbeddingRepository embeddings,
                                           EmbeddingClient client, EmbeddingProperties properties) {
        this.categories = categories;
        this.embeddings = embeddings;
        this.client = client;
        this.properties = properties;
    }

    /**
     * @param total    active categories looked at
     * @param embedded categories embedded now, new or changed
     * @param skipped  categories already embedded from the same text and model
     */
    public record Result(int total, int embedded, int skipped) {
    }

    /**
     * @param score cosine similarity to the message, from -1 to 1; higher is closer
     */
    public record Match(ProductCategory category, double score) {
    }

    /**
     * Embeds every active category that is new or changed.
     *
     * @throws ReindexInProgressException if another run is in progress
     * @throws EmbeddingException         if the embedding server fails
     */
    public Result reindexAll() {
        if (!running.tryLock()) {
            throw new ReindexInProgressException();
        }
        try {
            List<ProductCategory> all = categories.findByActiveTrue(Sort.by("id"));
            Map<String, ProductCategoryEmbedding> existing = new HashMap<>();
            embeddings.findAll().forEach(e -> existing.put(e.getCategoryCode(), e));

            int batchSize = Math.max(1, properties.batchSize());
            int embedded = 0;
            List<ProductCategory> stale = new ArrayList<>();
            for (ProductCategory category : all) {
                ProductCategoryEmbedding current = existing.get(category.getCode());
                String hash = EmbeddingSupport.hash(properties.model(), document(category));
                if (current == null || !current.getContentHash().equals(hash)) {
                    stale.add(category);
                }
            }
            for (int from = 0; from < stale.size(); from += batchSize) {
                List<ProductCategory> batch = stale.subList(from, Math.min(from + batchSize, stale.size()));
                List<String> texts = batch.stream().map(ProductCategoryEmbeddingService::document).toList();
                List<float[]> vectors = client.embed(texts);
                List<ProductCategoryEmbedding> toSave = new ArrayList<>();
                for (int i = 0; i < batch.size(); i++) {
                    String code = batch.get(i).getCode();
                    ProductCategoryEmbedding row = existing.getOrDefault(code, new ProductCategoryEmbedding(code));
                    row.update(texts.get(i), EmbeddingSupport.hash(properties.model(), texts.get(i)),
                            properties.model(), vectors.get(i));
                    toSave.add(row);
                }
                embeddings.saveAll(toSave);
                embedded += toSave.size();
                log.info("Category embeddings: {}/{} stale categories done", embedded, stale.size());
            }
            return new Result(all.size(), embedded, all.size() - embedded);
        } finally {
            running.unlock();
        }
    }

    /**
     * @param limit    most matches to return
     * @param minScore matches scoring below this are left out
     * @return active categories, best first. Categories not embedded yet, or embedded with another
     * model, cannot match.
     * @throws EmbeddingException if the message cannot be embedded
     */
    public List<Match> search(String message, int limit, double minScore) {
        float[] query = client.embed(List.of(QUERY_PREFIX + message.strip())).getFirst();

        record Scored(String code, double score) {
        }
        List<Scored> best = embeddings.findByModel(properties.model()).stream()
                .map(e -> new Scored(e.getCategoryCode(), EmbeddingSupport.cosine(query, e.getEmbedding())))
                .filter(s -> s.score() >= minScore)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .toList();
        if (best.isEmpty()) {
            return List.of();
        }

        // Embeddings of categories since deactivated drop out here.
        Map<String, ProductCategory> active = new HashMap<>();
        categories.findByCodeInAndActiveTrue(best.stream().map(Scored::code).toList())
                .forEach(c -> active.put(c.getCode(), c));
        return best.stream()
                .filter(s -> active.containsKey(s.code()))
                .map(s -> new Match(active.get(s.code()), s.score()))
                .toList();
    }

    private static String document(ProductCategory category) {
        String description = category.getDescription();
        return "Danh mục: " + category.getName()
                + (description == null || description.isBlank() ? "" : "\nMô tả: " + description.strip());
    }
}
