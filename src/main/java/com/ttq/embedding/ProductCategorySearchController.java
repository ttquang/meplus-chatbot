package com.ttq.embedding;

import com.ttq.embedding.ProductSearchController.SearchRequest;
import com.ttq.product.ProductCategoryController.ProductCategoryView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Finds the product categories a customer's message is about, by meaning rather than by matching words. */
@RestController
@RequestMapping("/api")
public class ProductCategorySearchController {

    private final ProductCategoryEmbeddingService service;
    private final ProductCategoryLlmSearchService llmSearch;

    public ProductCategorySearchController(ProductCategoryEmbeddingService service,
                                           ProductCategoryLlmSearchService llmSearch) {
        this.service = service;
        this.llmSearch = llmSearch;
    }

    public record CategoryMatchView(String code, String name, String description, double score) {
    }

    /**
     * @param category   the one category the message is about; null when nothing matched
     * @param decidedBy  NONE, EMBEDDING (one close match), LLM (chosen among several) or
     *                   EMBEDDING_FALLBACK (several close, the LLM unavailable, the closest taken)
     * @param reason     the LLM's reason; null unless it decided
     * @param candidates how many categories the embedding search found
     */
    public record ChosenCategoryView(CategoryMatchView category, String decidedBy, String reason, int candidates) {
    }

    /**
     * Like {@code /api/product-category-search} but answers with a single category. The embedding
     * search finds up to {@code limit} candidates (5 when omitted) scoring at least {@code minScore};
     * when that is more than one, DeepSeek decides between them.
     */
    @PostMapping("/product-category-search-with-llm")
    public ChosenCategoryView searchWithLlm(@Valid @RequestBody SearchRequest request) {
        ProductCategoryLlmSearchService.Result result = llmSearch.search(request.message(),
                request.limit() == null ? 5 : request.limit(),
                request.minScore() == null ? -1 : request.minScore());
        CategoryMatchView category = null;
        if (result.category() != null) {
            ProductCategoryView view = ProductCategoryView.of(result.category());
            category = new CategoryMatchView(view.code(), view.name(), view.description(), result.score());
        }
        return new ChosenCategoryView(category, result.decidedBy().name(), result.reason(), result.candidates());
    }

    /** Takes the same request as {@code /api/product-search}. */
    @PostMapping("/product-category-search")
    public List<CategoryMatchView> search(@Valid @RequestBody SearchRequest request) {
        return service.search(request.message(),
                        request.limit() == null ? 5 : request.limit(),
                        request.minScore() == null ? -1 : request.minScore())
                .stream()
                .map(m -> {
                    ProductCategoryView view = ProductCategoryView.of(m.category());
                    return new CategoryMatchView(view.code(), view.name(), view.description(), m.score());
                })
                .toList();
    }

    /** Embeds the categories that are new or changed since they were last embedded. */
    @PostMapping("/product-category-embeddings/reindex")
    public ProductCategoryEmbeddingService.Result reindex() {
        return service.reindexAll();
    }
}
