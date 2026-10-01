package com.ttq.product;

import com.ttq.embedding.EmbeddingClient;
import com.ttq.embedding.ProductCategoryEmbeddingRepository;
import com.ttq.embedding.ProductCategoryEmbeddingService;
import com.ttq.embedding.ProductCategorySearchController;
import com.ttq.embedding.ProductCategorySearchController.ChosenCategoryView;
import com.ttq.embedding.ProductSearchController.SearchRequest;
import com.ttq.llm.CategoryChooser;
import com.ttq.llm.LlmException;
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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductCategoryLlmSearchTest {

    /** What the stand-in LLM does and what it was asked. */
    static class FakeChooser implements CategoryChooser {
        final List<List<Candidate>> calls = new ArrayList<>();
        Function<List<Candidate>, Choice> answer;

        @Override
        public Choice choose(String message, List<Candidate> candidates) {
            calls.add(candidates);
            return answer.apply(candidates);
        }
    }

    @TestConfiguration
    static class Config {
        /** Embeds a text as how much it talks about oxygen, blood sugar and the weather. */
        @Bean
        @Primary
        EmbeddingClient keywordEmbeddingClient() {
            return texts -> texts.stream().map(t -> {
                String s = t.toLowerCase();
                return new float[]{
                        s.contains("oxy") ? 1f : 0f,
                        s.contains("đường huyết") ? 1f : 0f,
                        s.contains("trời") ? 1f : 0f,
                        0.01f};
            }).toList();
        }

        @Bean
        @Primary
        FakeChooser fakeChooser() {
            return new FakeChooser();
        }
    }

    @Autowired
    ProductCategorySearchController controller;

    @Autowired
    ProductCategoryEmbeddingService indexer;

    @Autowired
    ProductCategoryEmbeddingRepository embeddings;

    @Autowired
    FakeChooser chooser;

    @BeforeEach
    void setUp() {
        embeddings.deleteAll();
        indexer.reindexAll();
        chooser.calls.clear();
        chooser.answer = candidates -> new CategoryChooser.Choice(candidates.getLast().code(), "last one");
    }

    @Test
    void asksTheLlmToChooseWhenSeveralCategoriesMatchAndReturnsOnlyThatOne() {
        ChosenCategoryView result = controller.searchWithLlm(new SearchRequest("cần oxy cho mẹ", 10, 0.9));

        assertThat(chooser.calls).hasSize(1);
        List<CategoryChooser.Candidate> asked = chooser.calls.getFirst();
        assertThat(asked.size()).isGreaterThan(1);
        assertThat(asked).extracting(CategoryChooser.Candidate::name).contains("Máy tạo oxy", "Dây thở oxy");
        assertThat(result.decidedBy()).isEqualTo("LLM");
        assertThat(result.reason()).isEqualTo("last one");
        assertThat(result.candidates()).isEqualTo(asked.size());
        assertThat(result.category().code()).isEqualTo(asked.getLast().code());
    }

    @Test
    void doesNotAskTheLlmWhenOnlyOneCategoryMatches() {
        ChosenCategoryView result = controller.searchWithLlm(new SearchRequest("cần oxy cho mẹ", 1, null));

        assertThat(chooser.calls).isEmpty();
        assertThat(result.decidedBy()).isEqualTo("EMBEDDING");
        assertThat(result.category()).isNotNull();
        assertThat(result.candidates()).isEqualTo(1);
    }

    @Test
    void returnsNoCategoryWhenNothingIsCloseEnough() {
        ChosenCategoryView result = controller.searchWithLlm(new SearchRequest("hôm nay trời đẹp", 5, 0.99));

        assertThat(chooser.calls).isEmpty();
        assertThat(result.category()).isNull();
        assertThat(result.decidedBy()).isEqualTo("NONE");
    }

    @Test
    void fallsBackToTheClosestByEmbeddingWhenTheLlmFails() {
        chooser.answer = candidates -> {
            throw new LlmException("down", null);
        };

        ChosenCategoryView result = controller.searchWithLlm(new SearchRequest("cần oxy cho mẹ", 10, 0.9));

        assertThat(result.decidedBy()).isEqualTo("EMBEDDING_FALLBACK");
        assertThat(result.category().code()).isEqualTo(chooser.calls.getFirst().getFirst().code());
    }

    @Test
    void fallsBackWhenTheLlmChoosesSomethingThatWasNotOffered() {
        chooser.answer = candidates -> new CategoryChooser.Choice("CATEGORY-999", "made up");

        ChosenCategoryView result = controller.searchWithLlm(new SearchRequest("cần oxy cho mẹ", 10, 0.9));

        assertThat(result.decidedBy()).isEqualTo("EMBEDDING_FALLBACK");
        assertThat(result.category().code()).isNotEqualTo("CATEGORY-999");
    }
}
