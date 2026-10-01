package com.ttq.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductVariantRepositoryTest {

    @Autowired
    ProductVariantRepository variants;

    @Autowired
    ProductRepository products;

    @Autowired
    BrandRepository brands;

    @BeforeEach
    void loadCatalog() {
        CatalogFixture.load(brands, products);
    }

    private Product product(String code) {
        return products.findByCodeIgnoreCaseAndActiveTrue(code).orElseThrow();
    }

    @Test
    void variantsAreStoredAgainstTheirProductsCode() {
        Product hearingAid = product("PRODUCT-00004");
        variants.saveAndFlush(new ProductVariant("PRODUCT-00004-S-BEIGE", hearingAid));
        variants.saveAndFlush(new ProductVariant("PRODUCT-00004-M", hearingAid));

        assertThat(variants.findByProductCodeOrderByCodeAsc("PRODUCT-00004"))
                .extracting(ProductVariant::getCode)
                .containsExactly("PRODUCT-00004-M", "PRODUCT-00004-S-BEIGE");
        assertThat(variants.findByCodeIgnoreCase("product-00004-m")).get()
                .extracting(v -> v.getProduct().getCode()).isEqualTo("PRODUCT-00004");
    }

    @Test
    void aVariantCodeIsUnique() {
        Product product = product("PRODUCT-00005");
        variants.saveAndFlush(new ProductVariant("PRODUCT-00005-A", product));

        ProductVariant duplicate = new ProductVariant("PRODUCT-00005-A", product);

        assertThatThrownBy(() -> variants.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
