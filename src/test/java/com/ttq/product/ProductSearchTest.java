package com.ttq.product;

import com.ttq.embedding.EmbeddingClient;
import com.ttq.embedding.ProductEmbeddingRepository;
import com.ttq.embedding.ProductEmbeddingService;
import com.ttq.embedding.ProductSearchController;
import com.ttq.embedding.ProductSearchController.MatchView;
import com.ttq.embedding.ProductSearchController.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.ttq.product.CatalogFixture.BLOOD_PRESSURE_MONITOR;
import static com.ttq.product.CatalogFixture.EYE_MASK;
import static com.ttq.product.CatalogFixture.HEARING_AID_WIRED;
import static com.ttq.product.CatalogFixture.HEARING_AID_WIRELESS;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductSearchTest {

    /** Embeds a text as how much it talks about blood pressure, hearing and eye masks. */
    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        EmbeddingClient keywordEmbeddingClient() {
            return texts -> texts.stream().map(t -> {
                String s = t.toLowerCase();
                return new float[]{
                        s.contains("huyết áp") ? 1f : 0f,
                        s.contains("trợ thính") || s.contains("nghe") ? 1f : 0f,
                        s.contains("mặt nạ") || s.contains("mắt") ? 1f : 0f,
                        0.01f};
            }).toList();
        }
    }

    @Autowired
    ProductSearchController controller;

    @Autowired
    ProductEmbeddingService indexer;

    @Autowired
    ProductEmbeddingRepository embeddings;

    @Autowired
    BrandRepository brands;

    @Autowired
    ProductRepository products;

    @BeforeEach
    void indexCatalog() {
        CatalogFixture.load(brands, products);
        embeddings.deleteAll();
        indexer.reindexAll();
    }

    @Test
    void returnsTheProductsClosestToTheMessageBestFirst() {
        List<MatchView> matches = controller.search(new SearchRequest("máy đo huyết áp cho ông bà", 3, null));

        assertThat(matches).hasSize(3);
        assertThat(matches.getFirst().code()).isEqualTo(BLOOD_PRESSURE_MONITOR);
        assertThat(matches.getFirst().brand()).isEqualTo("Omron");
        assertThat(matches).extracting(MatchView::score).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void findsBothHearingAidsForAHearingQuestion() {
        List<MatchView> matches = controller.search(new SearchRequest("ông tôi nghe kém", 2, null));

        assertThat(matches).extracting(MatchView::code)
                .containsExactlyInAnyOrder(HEARING_AID_WIRED, HEARING_AID_WIRELESS);
    }

    @Test
    void leavesOutMatchesBelowTheMinimumScore() {
        List<MatchView> matches = controller.search(new SearchRequest("mặt nạ xông hơi cho mắt", 10, 0.9));

        assertThat(matches).extracting(MatchView::code).containsExactly(EYE_MASK);
    }

    @Test
    void ignoresProductsThatWereDeactivatedAfterBeingEmbedded() {
        Product monitor = products.findByCodeIgnoreCaseAndActiveTrue(BLOOD_PRESSURE_MONITOR).orElseThrow();
        monitor.setActive(false);
        products.save(monitor);
        try {
            assertThat(controller.search(new SearchRequest("máy đo huyết áp", 10, 0.9))).isEmpty();
        } finally {
            monitor.setActive(true);
            products.save(monitor);
        }
    }
}
