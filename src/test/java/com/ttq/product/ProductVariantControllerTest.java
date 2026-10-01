package com.ttq.product;

import com.ttq.product.ProductVariantController.ProductVariantView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.ttq.product.CatalogFixture.BLOOD_PRESSURE_MONITOR;
import static com.ttq.product.CatalogFixture.EYE_MASK;
import static com.ttq.product.CatalogFixture.MASK_LAVENDER;
import static com.ttq.product.CatalogFixture.MASK_MUGWORT;
import static com.ttq.product.CatalogFixture.MONITOR_WHITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@SpringBootTest
@ActiveProfiles("test")
class ProductVariantControllerTest {

    @Autowired
    ProductVariantController controller;

    @Autowired
    BrandRepository brands;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductVariantRepository variants;

    @Autowired
    SellableUnitRepository units;

    @Autowired
    AttributeDefinitionRepository definitions;

    @Autowired
    ProductAttributeRepository productAttributes;

    @Autowired
    ProductVariantAttributeRepository variantAttributes;

    @BeforeEach
    void loadCatalog() {
        CatalogFixture.load(brands, products);
        CatalogFixture.loadVariants(products, variants, units);
        CatalogFixture.loadVariantAttributes(products, variants, definitions, productAttributes, variantAttributes);
    }

    @Test
    void listsTheVariantsOfAProductIgnoringCase() {
        assertThat(controller.list(EYE_MASK.toLowerCase(), null, null))
                .extracting(ProductVariantView::code, ProductVariantView::label)
                .containsExactly(
                        tuple(MASK_LAVENDER, "Tím / Oải hương"),
                        tuple(MASK_MUGWORT, "Xanh / Ngải cứu"));
    }

    @Test
    void listsEveryVariantWhenNoProductIsGiven() {
        assertThat(controller.list(null, null, null)).extracting(ProductVariantView::code)
                .contains(MONITOR_WHITE, MASK_LAVENDER, MASK_MUGWORT);
    }

    @Test
    void findsAVariantByCodeWithItsProduct() {
        assertThat(controller.get(MONITOR_WHITE.toLowerCase()).getBody()).isEqualTo(new ProductVariantView(
                MONITOR_WHITE, "M", BLOOD_PRESSURE_MONITOR,
                "[Omron] Máy đo huyết áp bắp tay tự động - HEM-7156",
                List.of(new ProductVariantController.AttributeValueView("ATTR-27", "Kích cỡ", "M")),
                "[Omron] Máy đo huyết áp bắp tay tự động - HEM-7156 (M) - giá cơ bản: Cái - 1.250.000 đ", "Vòng bắp tay: 22 - 32 cm"));
        assertThat(controller.get("NO-SUCH").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void aVariantWithNothingToTellItApartIsLabelledWithItsProductName() {
        Product monitor = products.findByCodeIgnoreCaseAndActiveTrue(BLOOD_PRESSURE_MONITOR).orElseThrow();
        variants.save(new ProductVariant("PLAIN-001", monitor));

        assertThat(controller.get("PLAIN-001").getBody()).isNotNull()
                .extracting(ProductVariantView::label).isEqualTo(monitor.getName());
    }

    @Test
    void variantsOfAnInactiveProductAreHidden() {
        Brand brand = brands.findByCodeIgnoreCase(CatalogFixture.OMRON).orElseThrow();
        Product retired = products.save(new Product("RETIRED-V", "Retired", brand, "Test", null, "cái"));
        variants.save(new ProductVariant("RETIRED-V-001", retired));
        retired.setActive(false);
        products.save(retired);

        assertThat(controller.list("RETIRED-V", null, null)).isEmpty();
        assertThat(controller.get("RETIRED-V-001").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void variantsCanBeNarrowedByCategoryAndAttributeValues() {
        assertThat(controller.list(null, "chăm sóc da", null)).extracting(ProductVariantView::code)
                .containsExactly(MASK_LAVENDER, MASK_MUGWORT);
        assertThat(controller.list(null, "Chăm sóc da", List.of("ATTR-24:Xanh"))).extracting(ProductVariantView::code)
                .containsExactly(MASK_MUGWORT);
        // Alternatives of one attribute add up; a value nothing in the category has is ignored.
        assertThat(controller.list(null, "Chăm sóc da", List.of("ATTR-24:Tím", "attr-24:xanh", "ATTR-27:M")))
                .extracting(ProductVariantView::code).containsExactly(MASK_LAVENDER, MASK_MUGWORT);
        assertThat(controller.list(null, "Chăm sóc da", List.of("ATTR-24:Xanh", "ATTR-T1:Oải hương")))
                .isEmpty();
        assertThat(controller.list(null, "No such category", null)).isEmpty();
    }

    @Test
    void aVariantIsShownWithItsProductWhenChoosingAmongSeveralProducts() {
        assertThat(controller.list(EYE_MASK, null, null))
                .extracting(ProductVariantView::displayName)
                .containsExactly(
                        "Mặt nạ xông hơi mắt ngải cứu - Mugwort Steam Eyes Mask (Tím / Oải hương) - giá cơ bản: Cái - 30.000 đ",
                        "Mặt nạ xông hơi mắt ngải cứu - Mugwort Steam Eyes Mask (Xanh / Ngải cứu) - giá cơ bản: Hộp x5 - 150.000 đ");
    }

    @Test
    void aVariantHasItsAdditionalDescriptionOrNone() {
        assertThat(controller.get(MONITOR_WHITE).getBody()).isNotNull()
                .extracting(ProductVariantView::additionalDesc).isEqualTo("Vòng bắp tay: 22 - 32 cm");
        assertThat(controller.get(MASK_LAVENDER).getBody()).isNotNull()
                .extracting(ProductVariantView::additionalDesc).isNull();
    }
}
