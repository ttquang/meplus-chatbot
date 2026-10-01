package com.ttq.product;

import com.ttq.embedding.EmbeddingClient;
import com.ttq.embedding.ProductCategoryEmbeddingRepository;
import com.ttq.embedding.ProductCategoryEmbeddingService;
import com.ttq.embedding.ProductCategorySearchController;
import com.ttq.embedding.ProductCategorySearchController.CategoryMatchView;
import com.ttq.embedding.ProductSearchController.SearchRequest;
import com.ttq.product.ProductCategoryController.ProductCategoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductCategoryTest {

    static final List<List<String>> CALLS = new ArrayList<>();

    /** Embeds a text as how much it talks about hearing, bedsores and blood sugar. */
    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        EmbeddingClient keywordEmbeddingClient() {
            return texts -> {
                CALLS.add(List.copyOf(texts));
                return texts.stream().map(t -> {
                    String s = t.toLowerCase();
                    return new float[]{
                            s.contains("nghe") || s.contains("trợ thính") ? 1f : 0f,
                            s.contains("loét") || s.contains("lở loét") ? 1f : 0f,
                            s.contains("đường huyết") ? 1f : 0f,
                            0.01f};
                }).toList();
            };
        }
    }

    @Autowired
    ProductCategoryController controller;

    @Autowired
    ProductCategoryRepository categories;

    @Autowired
    ProductCategoryEmbeddingService indexer;

    @Autowired
    ProductCategoryEmbeddingRepository embeddings;

    @Autowired
    ProductCategorySearchController search;

    @BeforeEach
    void setUp() {
        embeddings.deleteAll();
        CALLS.clear();
    }

    @Test
    void importsTheSheetsCategoriesWithTheirDescriptions() {
        List<ProductCategoryView> all = controller.list(null);

        assertThat(all).hasSize(76);
        assertThat(all).extracting(ProductCategoryView::code).doesNotHaveDuplicates().allMatch(c -> c.startsWith("CATEGORY-"));
        assertThat(controller.list("trợ thính")).extracting(ProductCategoryView::name).contains("Máy trợ thính");
        assertThat(controller.list("MÁY TRỢ THÍNH").getFirst().description()).contains("người nghe kém");
        assertThat(controller.get("category-001").getBody()).isNotNull();
        assertThat(controller.get("CATEGORY-999").getBody()).isNull();
    }

    @Test
    void embedsEveryCategoryOnceAndSkipsThemAfterwards() {
        var first = indexer.reindexAll();
        assertThat(first.embedded()).isEqualTo(76);
        assertThat(embeddings.count()).isEqualTo(76);

        CALLS.clear();
        var second = indexer.reindexAll();
        assertThat(second.embedded()).isZero();
        assertThat(second.skipped()).isEqualTo(76);
        assertThat(CALLS).isEmpty();
    }

    @Test
    void returnsTheCategoriesClosestToTheMessage() {
        indexer.reindexAll();

        List<CategoryMatchView> matches = search.search(new SearchRequest("ông tôi nghe kém", 3, 0.5));

        assertThat(matches).extracting(CategoryMatchView::name).contains("Máy trợ thính");
        assertThat(matches.getFirst().score()).isGreaterThan(0.5);
        assertThat(matches).extracting(CategoryMatchView::score).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }
}
