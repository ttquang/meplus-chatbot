package com.ttq.embedding;

import com.ttq.llm.CategoryChooser;
import com.ttq.llm.LlmException;
import com.ttq.product.ProductCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Finds the one product category a customer's message is about: the embedding search narrows the
 * categories down, and when that leaves more than one the LLM decides between them.
 */
@Service
public class ProductCategoryLlmSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductCategoryLlmSearchService.class);

    /** How the category was settled. */
    public enum DecidedBy {
        /** Nothing matched, so there is no category. */
        NONE,
        /** Only one category was close enough, so no model was asked. */
        EMBEDDING,
        /** Several were close and the LLM chose between them. */
        LLM,
        /** Several were close but the LLM could not be used, so the closest by embedding was taken. */
        EMBEDDING_FALLBACK
    }

    /**
     * @param category   the category, or null when {@code decidedBy} is NONE
     * @param score      the category's embedding similarity to the message
     * @param reason     why the LLM chose it; null unless {@code decidedBy} is LLM
     * @param candidates how many categories the embedding search found
     */
    public record Result(ProductCategory category, double score, DecidedBy decidedBy, String reason, int candidates) {
    }

    private final ProductCategoryEmbeddingService search;
    private final CategoryChooser chooser;

    public ProductCategoryLlmSearchService(ProductCategoryEmbeddingService search, CategoryChooser chooser) {
        this.search = search;
        this.chooser = chooser;
    }

    /**
     * @param candidates most categories the embedding search may hand to the LLM
     * @param minScore   categories scoring below this are not candidates
     * @throws EmbeddingException if the message cannot be embedded
     */
    public Result search(String message, int candidates, double minScore) {
        List<ProductCategoryEmbeddingService.Match> matches = search.search(message, candidates, minScore);
        if (matches.isEmpty()) {
            return new Result(null, 0, DecidedBy.NONE, null, 0);
        }
        ProductCategoryEmbeddingService.Match best = matches.getFirst();
        if (matches.size() == 1) {
            return new Result(best.category(), best.score(), DecidedBy.EMBEDDING, null, 1);
        }

        try {
            CategoryChooser.Choice choice = chooser.choose(message, matches.stream()
                    .map(m -> new CategoryChooser.Candidate(m.category().getCode(), m.category().getName(),
                            m.category().getDescription()))
                    .toList());
            ProductCategoryEmbeddingService.Match chosen = matches.stream()
                    .filter(m -> m.category().getCode().equals(choice.code()))
                    .findFirst()
                    .orElseThrow(() -> new LlmException("Chose " + choice.code() + ", which is not a candidate", null));
            return new Result(chosen.category(), chosen.score(), DecidedBy.LLM, choice.reason(), matches.size());
        } catch (LlmException e) {
            // The caller still gets a category; the closest by embedding is the best guess without the LLM.
            log.warn("LLM could not choose a category, using the closest by embedding: {}", e.getMessage());
            return new Result(best.category(), best.score(), DecidedBy.EMBEDDING_FALLBACK, null, matches.size());
        }
    }
}
