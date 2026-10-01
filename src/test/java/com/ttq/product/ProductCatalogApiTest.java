package com.ttq.product;

import com.ttq.action.ActionContext;
import com.ttq.action.ActionFailedException;
import com.ttq.action.ActionRegistry;
import com.ttq.action.ActionResult;
import com.ttq.process.FieldDefinition;
import com.ttq.process.FieldOptions;
import com.ttq.process.FieldOptionsResolver;
import com.ttq.process.FieldType;
import com.ttq.process.ProcessRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static com.ttq.product.CatalogFixture.BLOOD_PRESSURE_MONITOR;
import static com.ttq.product.CatalogFixture.EYE_MASK;
import static com.ttq.product.CatalogFixture.HEARING_AID_WIRED;
import static com.ttq.product.CatalogFixture.HEARING_AID_WIRELESS;
import static com.ttq.product.CatalogFixture.MASK_LAVENDER;
import static com.ttq.product.CatalogFixture.MASK_MUGWORT;
import static com.ttq.product.CatalogFixture.MONITOR_WHITE;
import static com.ttq.product.CatalogFixture.OMRON;
import static com.ttq.product.CatalogFixture.RIONET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/** The chatbot reaches the product and brand endpoints through the api catalog, over http. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chatbot.api.base-url=http://localhost:${local.server.port}")
@ActiveProfiles("test")
class ProductCatalogApiTest {

    @Autowired
    FieldOptionsResolver resolver;

    @Autowired
    ActionRegistry actions;

    @Autowired
    ProcessRegistry processes;

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
    void loadCatalog() {
        CatalogFixture.load(brands, products);
        CatalogFixture.loadVariants(products, variants, units);
        CatalogFixture.loadVariantAttributes(products, variants, definitions, productAttributes, variantAttributes);
    }

    private static FieldDefinition field(String name, String valuesFrom) {
        return new FieldDefinition(name, FieldType.STRING, null, null, valuesFrom);
    }

    private ActionResult run(String action, Map<String, Object> collected) {
        return actions.run(action, new ActionContext(UUID.randomUUID(), processes.get("assistant"), collected));
    }

    @Test
    void productsCanBeOfferedAsAFieldsValuesNarrowedByWhatWasCollected() {
        FieldOptions options = resolver.options(field("productCode", "products"),
                Map.of("productCategory", "Máy trợ thính", "brandCode", RIONET));

        assertThat(options.dynamic()).isTrue();
        assertThat(options.values()).containsExactlyInAnyOrder(HEARING_AID_WIRED, HEARING_AID_WIRELESS);
        assertThat(options.options()).extracting(FieldOptions.Option::label)
                .contains("[Rionet] Máy trợ thính có dây đeo - HA-20DX");
    }

    @Test
    void productSearchMatchesVietnameseText() {
        FieldOptions options = resolver.options(field("productCode", "products"),
                Map.of("productSearch", "trợ thính"));

        assertThat(options.values()).containsExactlyInAnyOrder(HEARING_AID_WIRED, HEARING_AID_WIRELESS);
    }

    @Test
    void brandsCanBeOfferedAsAFieldsValues() {
        assertThat(resolver.options(field("brandCode", "brands"), Map.of("brandCountry", "Nhật Bản")).values())
                .containsExactlyInAnyOrder(OMRON, RIONET);
    }

    @Test
    void getProductCapturesTheProductAndItsBrand() {
        ActionResult result = run("getProduct", Map.of("productCode", BLOOD_PRESSURE_MONITOR));

        assertThat(result.fields())
                .containsEntry("productName", "[Omron] Máy đo huyết áp bắp tay tự động - HEM-7156")
                .containsEntry("productCategory", "Máy đo huyết áp")
                .containsEntry("productUom", "cái")
                .containsEntry("productBrandCode", OMRON)
                .containsEntry("productBrandName", "Omron")
                .containsEntry("productBrandCountry", "Nhật Bản")
                .containsKey("productDescription");
        assertThat(result.message())
                .startsWith("[Omron] Máy đo huyết áp bắp tay tự động - HEM-7156 (Omron, Máy đo huyết áp): ");
    }

    @Test
    void getBrandCapturesItsNameAndCountry() {
        assertThat(run("getBrand", Map.of("brandCode", OMRON.toLowerCase())).fields())
                .containsEntry("brandName", "Omron")
                .containsEntry("brandCountry", "Nhật Bản");
    }

    @Test
    void theVariantsOfTheChosenProductCanBeOffered() {
        FieldOptions options = resolver.options(field("productVariantCode", "productVariants"),
                Map.of("productCode", EYE_MASK));

        assertThat(options.options()).extracting(FieldOptions.Option::value, FieldOptions.Option::label)
                .containsExactly(
                        tuple(MASK_LAVENDER, "Tím / Oải hương"),
                        tuple(MASK_MUGWORT, "Xanh / Ngải cứu"));
    }

    @Test
    void variantsAreNotOfferedBeforeAProductIsChosen() {
        assertThat(resolver.options(field("productVariantCode", "productVariants"), Map.of()).isEmpty()).isTrue();
    }

    @Test
    void theUnitsOfTheChosenVariantCanBeOfferedWithTheirPrices() {
        FieldOptions options = resolver.options(field("sellableUnitCode", "sellableUnits"),
                Map.of("productVariantCode", MASK_LAVENDER));

        assertThat(options.options()).extracting(FieldOptions.Option::value, FieldOptions.Option::label)
                .containsExactly(
                        tuple(MASK_LAVENDER + "-U01", "Cái - 30.000 đ"),
                        tuple(MASK_LAVENDER + "-U02", "Hộp x5"));
    }

    @Test
    void getProductVariantCapturesTheVariant() {
        ActionResult result = run("getProductVariant", Map.of("productVariantCode", MONITOR_WHITE));

        assertThat(result.fields())
                .containsEntry("variantLabel", "M")
                .containsEntry("variantProductCode", BLOOD_PRESSURE_MONITOR);
        assertThat(result.message())
                .isEqualTo("[Omron] Máy đo huyết áp bắp tay tự động - HEM-7156: M\nVòng bắp tay: 22 - 32 cm");
    }

    @Test
    void getSellableUnitCapturesItsPrice() {
        ActionResult result = run("getSellableUnit", Map.of("sellableUnitCode", MONITOR_WHITE + "-U01"));

        assertThat(result.fields())
                .containsEntry("sellableUnitSaleUom", "Cái")
                .containsEntry("sellableUnitQuantity", 1)
                .containsEntry("sellableUnitPrice", 1250000)
                .containsEntry("sellableUnitVariantCode", MONITOR_WHITE)
                .containsEntry("sellableUnitProductCode", BLOOD_PRESSURE_MONITOR);
        assertThat(result.message())
                .isEqualTo("[Omron] Máy đo huyết áp bắp tay tự động - HEM-7156: Cái - 1.250.000 đ");
    }

    @Test
    void anUnknownSellableUnitIsRejectedAndForgotten() {
        assertThatThrownBy(() -> run("getSellableUnit", Map.of("sellableUnitCode", "NO-SUCH-U01")))
                .isInstanceOfSatisfying(ActionFailedException.class,
                        e -> assertThat(e.fieldsToClear()).containsExactly("sellableUnitCode"));
    }

    @Test
    void anUnknownCodeIsRejectedAndForgotten() {
        assertThatThrownBy(() -> run("getProduct", Map.of("productCode", "PRODUCT-99999")))
                .isInstanceOfSatisfying(ActionFailedException.class, e -> {
                    assertThat(e.userMessage()).contains("PRODUCT-99999");
                    assertThat(e.fieldsToClear()).containsExactly("productCode");
                });
    }
}
