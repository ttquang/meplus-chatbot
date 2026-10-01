package com.ttq.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.ttq.product.CatalogFixture.FILM_DRESSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SellableUnitRepositoryTest {

    @Autowired
    SellableUnitRepository units;

    @Autowired
    ProductVariantRepository variants;

    @Autowired
    ProductRepository products;

    @Autowired
    BrandRepository brands;

    private ProductVariant dressing;

    @BeforeEach
    void loadCatalog() {
        CatalogFixture.load(brands, products);
        Product product = products.findByCodeIgnoreCaseAndActiveTrue(FILM_DRESSING).orElseThrow();
        dressing = variants.saveAndFlush(new ProductVariant(FILM_DRESSING + "-001", product));
    }

    @Test
    void aVariantCanBeSoldInSeveralUnits() {
        units.saveAndFlush(new SellableUnit(FILM_DRESSING + "-001-U01", dressing, "Cái", 1, new BigDecimal("25000")));
        units.saveAndFlush(new SellableUnit(FILM_DRESSING + "-001-U02", dressing, "Hộp", 100, null));

        assertThat(units.findByProductVariantCodeOrderByCodeAsc(FILM_DRESSING + "-001"))
                .extracting(SellableUnit::getCode, SellableUnit::getSaleUom, SellableUnit::getQuantity,
                        SellableUnit::getPrice)
                .containsExactly(
                        tuple(FILM_DRESSING + "-001-U01", "Cái", 1, new BigDecimal("25000")),
                        tuple(FILM_DRESSING + "-001-U02", "Hộp", 100, null));
        assertThat(units.findByCodeIgnoreCase(FILM_DRESSING.toLowerCase() + "-001-u02")).get()
                .extracting(u -> u.getProductVariant().getProduct().getCode()).isEqualTo(FILM_DRESSING);
    }

    @Test
    void theLargestCatalogPriceIsStoredExactly() {
        units.saveAndFlush(new SellableUnit(FILM_DRESSING + "-001-U03", dressing, "Bộ", 1, new BigDecimal("14500000")));

        assertThat(units.findByCodeIgnoreCase(FILM_DRESSING + "-001-U03")).get()
                .extracting(SellableUnit::getPrice).isEqualTo(new BigDecimal("14500000"));
    }

    @Test
    void aSellableUnitCodeIsUnique() {
        units.saveAndFlush(new SellableUnit(FILM_DRESSING + "-001-U04", dressing, "Cái", 1, null));
        SellableUnit duplicate = new SellableUnit(FILM_DRESSING + "-001-U04", dressing, "Hộp", 10, null);

        assertThatThrownBy(() -> units.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
