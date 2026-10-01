package com.ttq.product;

import com.ttq.product.VariantAttributeController.VariantAttributeView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class VariantAttributeControllerTest {

    private static final String CATEGORY = "Chăm sóc da";

    @Autowired
    VariantAttributeController controller;

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
    void listsTheValuesOfTheVariantsInACategoryByAttribute() {
        assertThat(controller.list(null, CATEGORY, null))
                .extracting(VariantAttributeView::code, VariantAttributeView::name, VariantAttributeView::group,
                        VariantAttributeView::count)
                .containsExactly(
                        tuple("ATTR-24:Tím", "Tím", "Màu sắc", 1L),
                        tuple("ATTR-24:Xanh", "Xanh", "Màu sắc", 1L),
                        tuple("ATTR-T1:Oải hương", "Oải hương", "Mùi hương", 1L),
                        tuple("ATTR-T1:Ngải cứu", "Ngải cứu", "Mùi hương", 1L));
    }

    @Test
    void theGuidanceOfAnAttributeComesWithItsValues() {
        assertThat(controller.list(null, CATEGORY, null))
                .extracting(VariantAttributeView::code, VariantAttributeView::guidance)
                .containsExactly(
                        tuple("ATTR-24:Tím", "Chỉ khác màu, công dụng như nhau - chọn theo sở thích."),
                        tuple("ATTR-24:Xanh", "Chỉ khác màu, công dụng như nhau - chọn theo sở thích."),
                        tuple("ATTR-T1:Oải hương", null),
                        tuple("ATTR-T1:Ngải cứu", null));
    }

    @Test
    void theCountsTakeTheValuesAlreadyPickedIntoAccount() {
        List<VariantAttributeView> afterColor = controller.list(null, CATEGORY, List.of("ATTR-24:Xanh"));

        // Picking the other color would switch to it, so it still leaves a variant; a scent of the
        // variant that was left out would leave none once the color stays.
        assertThat(afterColor).extracting(VariantAttributeView::code, VariantAttributeView::count)
                .containsExactly(
                        tuple("ATTR-24:Tím", 1L),
                        tuple("ATTR-24:Xanh", 1L),
                        tuple("ATTR-T1:Oải hương", 0L),
                        tuple("ATTR-T1:Ngải cứu", 1L));
    }

    @Test
    void nothingIsListedForACategoryWithNoVariants() {
        assertThat(controller.list(null, "No such category", null)).isEmpty();
    }
}
