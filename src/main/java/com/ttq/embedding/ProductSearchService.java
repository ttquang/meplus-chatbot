package com.ttq.embedding;

import com.ttq.product.Product;
import com.ttq.product.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finds the products closest in meaning to a customer's message: the message is embedded with the
 * same model as the products, and each product is scored by cosine similarity. The products are
 * few enough to compare in memory; a catalog large enough to make that slow wants a vector index.
 */
@Service
public class ProductSearchService {

    /** Qwen3-embedding reads a query best with a task instruction in front; documents get none. */
    private static final String QUERY_PREFIX =
            "Instruct: Given a customer's message, retrieve the medical supply products that match it\nQuery: ";

    private final EmbeddingClient client;
    private final EmbeddingProperties properties;
    private final ProductEmbeddingRepository embeddings;
    private final ProductRepository products;

    public ProductSearchService(EmbeddingClient client, EmbeddingProperties properties,
                                ProductEmbeddingRepository embeddings, ProductRepository products) {
        this.client = client;
        this.properties = properties;
        this.embeddings = embeddings;
        this.products = products;
    }

    /**
     * @param score cosine similarity to the message, from -1 to 1; higher is closer
     */
    public record Match(Product product, double score) {
    }

    /**
     * @param limit    most matches to return
     * @param minScore matches scoring below this are left out
     * @return active products, best first. Products not embedded yet, or embedded with another
     * model, cannot match.
     * @throws EmbeddingException if the message cannot be embedded
     */
    public List<Match> search(String message, int limit, double minScore) {
        float[] query = client.embed(List.of(QUERY_PREFIX + message.strip())).getFirst();

        record Scored(String code, double score) {
        }
        List<Scored> best = embeddings.findByModel(properties.model()).stream()
                .map(e -> new Scored(e.getProductCode(), EmbeddingSupport.cosine(query, e.getEmbedding())))
                .filter(s -> s.score() >= minScore)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .toList();
        if (best.isEmpty()) {
            return List.of();
        }

        // Embeddings of products since deactivated or removed drop out here.
        Map<String, Product> active = products
                .findByCodeInAndActiveTrue(best.stream().map(Scored::code).toList()).stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity()));
        return best.stream()
                .filter(s -> active.containsKey(s.code()))
                .map(s -> new Match(active.get(s.code()), s.score()))
                .toList();
    }
}
