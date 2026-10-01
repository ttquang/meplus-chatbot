package com.ttq.product;

import com.ttq.embedding.EmbeddingClient;
import com.ttq.embedding.EmbeddingException;
import com.ttq.embedding.ProductEmbedding;
import com.ttq.embedding.ProductEmbeddingRepository;
import com.ttq.embedding.ProductEmbeddingService;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ttq.product.CatalogFixture.EYE_MASK;
import static com.ttq.product.CatalogFixture.FILM_DRESSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProductEmbeddingServiceTest {

    /** Stands in for Ollama: a 3-dimensional vector derived from the text, and a record of the calls. */
    static class FakeEmbeddingClient implements EmbeddingClient {
        final List<List<String>> calls = new ArrayList<>();
        final AtomicBoolean failing = new AtomicBoolean();

        @Override
        public List<float[]> embed(List<String> texts) {
            if (failing.get()) {
                throw new EmbeddingException("down", null);
            }
            calls.add(List.copyOf(texts));
            return texts.stream().map(t -> new float[]{t.length(), 1f, -0.5f}).toList();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        FakeEmbeddingClient fakeEmbeddingClient() {
            return new FakeEmbeddingClient();
        }
    }

    @Autowired
    ProductEmbeddingService service;

    @Autowired
    ProductEmbeddingRepository embeddings;

    @Autowired
    FakeEmbeddingClient client;

    @Autowired
    BrandRepository brands;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductVariantRepository variants;

    @Autowired
    AttributeDefinitionRepository definitions;

    @Autowired
    ProductAttributeRepository productAttributes;

    @Autowired
    ProductVariantAttributeRepository variantAttributes;

    @Autowired
    SellableUnitRepository units;

    @BeforeEach
    void setUp() {
        CatalogFixture.load(brands, products);
        CatalogFixture.loadVariants(products, variants, units);
        CatalogFixture.loadVariantAttributes(products, variants, definitions, productAttributes, variantAttributes);
        embeddings.deleteAll();
        client.calls.clear();
        client.failing.set(false);
    }

    @Test
    void embedsEveryActiveProductFromAllItsDetails() {
        long active = products.findAll().stream().filter(Product::isActive).count();

        ProductEmbeddingService.Result result = service.reindexAll();

        assertThat(result.total()).isEqualTo((int) active);
        assertThat(result.embedded()).isEqualTo((int) active);
        assertThat(embeddings.count()).isEqualTo(active);

        ProductEmbedding mask = embeddings.findByProductCodeIn(List.of(EYE_MASK)).getFirst();
        assertThat(mask.getModel()).isEqualTo("qwen3-embedding:4b");
        assertThat(mask.getEmbedding()).hasSize(3);
        assertThat(mask.getContent())
                .contains("Mặt nạ xông hơi mắt ngải cứu", "Thương hiệu: Khác", "Danh mục: Chăm sóc da")
                .contains("màu sắc Tím", "mùi hương Oải hương", "Cái giá 30.000đ", "Hộp 5")
                .contains("màu sắc Xanh", "Hộp 5 giá 150.000đ");
    }

    @Test
    void leavesUnchangedProductsAloneOnTheNextRun() {
        service.reindexAll();
        client.calls.clear();

        ProductEmbeddingService.Result result = service.reindexAll();

        assertThat(result.embedded()).isZero();
        assertThat(result.skipped()).isEqualTo(result.total());
        assertThat(client.calls).isEmpty();
    }

    @Test
    void reEmbedsOnlyTheProductWhoseDetailsChanged() {
        service.reindexAll();
        client.calls.clear();
        Product dressing = products.findByCodeIgnoreCaseAndActiveTrue(FILM_DRESSING).orElseThrow();
        ProductVariant added = variants.save(new ProductVariant(FILM_DRESSING + "-NEW", dressing));
        ProductVariantAttribute size = variantAttributes.save(new ProductVariantAttribute(
                added, definitions.findByCode("ATTR-27").orElseThrow(), "10 x 12 cm"));
        try {
            ProductEmbeddingService.Result result = service.reindexAll();

            assertThat(result.embedded()).isEqualTo(1);
            assertThat(client.calls).hasSize(1).first().asList().singleElement()
                    .asString().contains(FILM_DRESSING + "-NEW", "kích cỡ 10 x 12 cm");
        } finally {
            variantAttributes.delete(size);
            variants.delete(variants.findByCodeIgnoreCase(FILM_DRESSING + "-NEW").orElseThrow());
        }
    }

    @Test
    void keepsWhatWasEmbeddedWhenTheServerFailsAndFinishesOnRetry() {
        client.failing.set(true);
        assertThatThrownBy(service::reindexAll).isInstanceOf(EmbeddingException.class);
        assertThat(embeddings.count()).isZero();

        client.failing.set(false);
        ProductEmbeddingService.Result result = service.reindexAll();

        assertThat(result.embedded()).isEqualTo(result.total());
    }
}
