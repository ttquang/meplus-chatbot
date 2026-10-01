package com.ttq.product;

import com.ttq.product.SellableUnitController.SellableUnitView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static com.ttq.product.CatalogFixture.BLOOD_PRESSURE_MONITOR;
import static com.ttq.product.CatalogFixture.EYE_MASK;
import static com.ttq.product.CatalogFixture.MASK_LAVENDER;
import static com.ttq.product.CatalogFixture.MASK_MUGWORT;
import static com.ttq.product.CatalogFixture.MONITOR_WHITE;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SellableUnitControllerTest {

    @Autowired
    SellableUnitController controller;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    BrandRepository brands;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductVariantRepository variants;

    @Autowired
    SellableUnitRepository units;

    @BeforeEach
    void loadCatalog() {
        CatalogFixture.load(brands, products);
        CatalogFixture.loadVariants(products, variants, units);
    }

    @Test
    void listsTheUnitsOfOneVariant() {
        assertThat(controller.list(MASK_LAVENDER.toLowerCase(), null)).extracting(SellableUnitView::code)
                .containsExactly(MASK_LAVENDER + "-U01", MASK_LAVENDER + "-U02");
    }

    @Test
    void listsTheUnitsOfEveryVariantOfAProduct() {
        assertThat(controller.list(null, EYE_MASK)).extracting(SellableUnitView::code)
                .containsExactly(MASK_LAVENDER + "-U01", MASK_LAVENDER + "-U02", MASK_MUGWORT + "-U01");
    }

    @Test
    void filtersCombine() {
        assertThat(controller.list(MASK_MUGWORT, EYE_MASK)).extracting(SellableUnitView::code)
                .containsExactly(MASK_MUGWORT + "-U01");
        assertThat(controller.list(MASK_MUGWORT, BLOOD_PRESSURE_MONITOR)).isEmpty();
    }

    @Test
    void findsAUnitByCodeWithItsPriceInVnd() {
        SellableUnitView unit = controller.get(MONITOR_WHITE.toLowerCase() + "-u01").getBody();

        assertThat(unit).isEqualTo(new SellableUnitView(MONITOR_WHITE + "-U01", "Cái - 1.250.000 đ", MONITOR_WHITE,
                BLOOD_PRESSURE_MONITOR, "[Omron] Máy đo huyết áp bắp tay tự động - HEM-7156", "Cái", 1,
                new BigDecimal("1250000")));
        assertThat(jsonMapper.writeValueAsString(unit)).contains("\"quantity\":1,\"price\":1250000}");
        assertThat(controller.get("NO-SUCH").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void theLabelLeavesOutAMissingPriceAndAQuantityOfOne() {
        assertThat(controller.get(MASK_LAVENDER + "-U02").getBody())
                .satisfies(u -> {
                    assertThat(u.saleUom()).isEqualTo("Hộp");
                    assertThat(u.quantity()).isEqualTo(5);
                    assertThat(u.price()).isNull();
                    assertThat(u.label()).isEqualTo("Hộp x5");
                });
        assertThat(controller.get(MASK_MUGWORT + "-U01").getBody())
                .extracting(SellableUnitView::label).isEqualTo("Hộp x5 - 150.000 đ");
    }

    @Test
    void unitsOfAnInactiveProductAreHidden() {
        Brand brand = brands.findByCodeIgnoreCase(CatalogFixture.OMRON).orElseThrow();
        Product retired = products.save(new Product("RETIRED-U", "Retired", brand, "Test", null, "cái"));
        ProductVariant variant = variants.save(new ProductVariant("RETIRED-U-001", retired));
        units.save(new SellableUnit("RETIRED-U-001-U01", variant, "Cái", 1, BigDecimal.TEN));
        retired.setActive(false);
        products.save(retired);

        assertThat(controller.list(null, "RETIRED-U")).isEmpty();
        assertThat(controller.get("RETIRED-U-001-U01").getStatusCode().value()).isEqualTo(404);
    }
}
