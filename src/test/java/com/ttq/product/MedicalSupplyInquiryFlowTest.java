package com.ttq.product;

import com.ttq.conversation.ConversationStatus;
import com.ttq.embedding.ProductCategoryEmbeddingService;
import com.ttq.embedding.ProductCategoryEmbeddingService.Match;
import com.ttq.engine.ConversationService;
import com.ttq.engine.ConversationService.TurnResult;
import com.ttq.llm.TurnContext;
import com.ttq.llm.TurnDecision;
import com.ttq.llm.TurnGenerator;
import com.ttq.llm.TurnPromptFactory;
import com.ttq.process.ChoiceResolver;
import com.ttq.process.FieldDefinition;
import com.ttq.process.FieldOptions.Option;
import com.ttq.process.FieldOptionsResolver;
import com.ttq.process.ProcessDefinition;
import com.ttq.process.ProcessRegistry;
import com.ttq.process.StateDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ttq.product.CatalogFixture.BLOOD_PRESSURE_MONITOR;
import static com.ttq.product.CatalogFixture.MASK_LAVENDER;
import static com.ttq.product.CatalogFixture.MASK_MUGWORT;
import static com.ttq.product.CatalogFixture.MONITOR_WHITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Walks the medical supply inquiry process against the application's own catalog endpoints: the
 * category is found from what the customer asks for, its attributes are collected until one variant
 * is left, and the variant's sellable unit gives the price.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chatbot.api.base-url=http://localhost:${local.server.port}")
@ActiveProfiles("test")
class MedicalSupplyInquiryFlowTest {

    private static final String PROCESS = "medical-supply-inquiry";
    private static final String MONITOR_UNIT = MONITOR_WHITE + "-U01";
    private static final String MONITOR_CATEGORY = "Máy đo huyết áp";
    private static final String SKIN_CATEGORY = "Chăm sóc da";
    private static final String BLUE = "ATTR-24:Xanh";
    private static final String PURPLE = "ATTR-24:Tím";
    private static final String MUGWORT = "ATTR-T1:Ngải cứu";

    @Autowired
    ConversationService service;

    @Autowired
    ProcessRegistry processes;

    @Autowired
    FieldOptionsResolver resolver;

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

    @Autowired
    ChoiceResolver choices;

    @Autowired
    TurnPromptFactory prompts;

    @MockitoBean
    TurnGenerator turnGenerator;

    @MockitoBean
    ProductCategoryEmbeddingService categorySearch;

    @BeforeEach
    void catalog() {
        CatalogFixture.load(brands, products);
        CatalogFixture.loadVariants(products, variants, units);
        CatalogFixture.loadVariantAttributes(products, variants, definitions, productAttributes, variantAttributes);
        CatalogFixture.loadComparison(products, definitions, productAttributes);
        // Whatever the customer asks for, the search finds these two kinds of product.
        when(categorySearch.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of(
                new Match(new ProductCategory("TEST-001", MONITOR_CATEGORY, "Máy đo huyết áp bắp tay tự động"), 0.9),
                new Match(new ProductCategory("TEST-002", SKIN_CATEGORY, "Sản phẩm chăm sóc da"), 0.5)));
    }

    @Test
    void theCustomerIsShownThePriceOfTheUnitTheyChose() {
        UUID id = service.start(PROCESS).conversation().getId();

        reply(new TurnDecision("Bạn đang tìm máy đo huyết áp phải không?",
                Map.of("productSearch", "huyết áp"), true, "choose_category"));
        assertThat(service.sendMessage(id, "Tôi cần máy đo huyết áp").conversation().getCurrentState())
                .isEqualTo("choose_category");

        reply(new TurnDecision("Bạn cần máy loại nào?",
                Map.of("productCategory", MONITOR_CATEGORY), true, "collect_attributes"));
        TurnResult category = service.sendMessage(id, "Đúng rồi");
        assertThat(category.conversation().getCurrentState()).isEqualTo("collect_attributes");
        assertThat(category.conversation().getCollectedData()).containsEntry("productCategory", MONITOR_CATEGORY);

        // The category has only one variant, so there is nothing left to ask but to confirm it. The
        // variant is sold in only one unit, so there is no unit to ask for either: the reply is written
        // again for the price.
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(
                new TurnDecision("Đây là thông tin sản phẩm. Bạn muốn mua theo đơn vị nào?",
                        Map.of("productVariantCode", MONITOR_WHITE), true, "choose_unit"),
                new TurnDecision("Giá của lựa chọn của bạn:", Map.of(), false, null));
        TurnResult price = service.sendMessage(id, "Đúng, lấy mẫu này");
        assertThat(price.conversation().getCurrentState()).isEqualTo("show_price");
        // The details come from the catalog, never from the model.
        assertThat(price.reply().getContent()).startsWith("Giá của lựa chọn của bạn:")
                .contains("lưu 60 kết quả").contains("Vòng bắp tay: 22 - 32 cm").contains("Cái - 1.250.000 đ");
        assertThat(price.conversation().getCollectedData())
                .containsEntry("productCode", BLOOD_PRESSURE_MONITOR)
                .containsEntry("sellableUnitCode", MONITOR_UNIT)
                .containsKey("sellableUnitPrice");

        // The customer wants to compare the product with others before leaving.
        reply(new TurnDecision("Đây là so sánh sản phẩm.", Map.of(), true, "compare_products"));
        TurnResult compared = service.sendMessage(id, "So sánh với sản phẩm khác giúp tôi");
        assertThat(compared.conversation().getCurrentState()).isEqualTo("compare_products");
        assertThat(compared.reply().getContent()).contains("• Kết nối (Có dây): So với không dây: ít hỏng hơn.")
                .contains("Sản phẩm khác: [Rionet] Máy trợ thính không dây - HB-23P");
        ProcessDefinition process = processes.get(PROCESS);
        assertThat(resolver.options(process.findField("comparedProductCode").orElseThrow(),
                compared.conversation().getCollectedData()).values()).containsExactly(CatalogFixture.HEARING_AID_WIRELESS);

        reply(new TurnDecision("Cảm ơn bạn! Hẹn gặp lại.", Map.of(), true, "done"));
        TurnResult done = service.sendMessage(id, "Cảm ơn, vậy thôi");
        assertThat(done.conversation().getStatus()).isEqualTo(ConversationStatus.COMPLETED);
    }

    @Test
    void aCategoryOutsideTheSearchResultsIsNotAccepted() {
        UUID id = service.start(PROCESS).conversation().getId();
        reply(new TurnDecision("Bạn muốn tìm loại nào?", Map.of("productSearch", "huyết áp"), true,
                "choose_category"));
        service.sendMessage(id, "Máy đo huyết áp");

        reply(new TurnDecision("Bạn cần loại nào?", Map.of("productCategory", "Máy trợ thính"), true,
                "collect_attributes"));
        TurnResult result = service.sendMessage(id, "Máy trợ thính");

        assertThat(result.conversation().getCurrentState()).isEqualTo("choose_category");
        assertThat(result.conversation().getCollectedData()).doesNotContainKey("productCategory");
    }

    @Test
    void lookupsHaveNoOptionsUntilWhatTheyNarrowByIsKnown() {
        ProcessDefinition process = processes.get(PROCESS);

        // Rather than listing the whole catalog, or failing on every turn.
        for (String field : List.of("productCategory", "attributeValues", "suggestedProductCode", "productVariantCode",
                "sellableUnitCode")) {
            assertThat(resolver.options(process.findField(field).orElseThrow(), Map.of()).isEmpty())
                    .as(field).isTrue();
        }
        assertThat(resolver.options(process.findField("productCategory").orElseThrow(),
                Map.of("productSearch", "huyết áp")).values()).containsExactly(MONITOR_CATEGORY, SKIN_CATEGORY);
        Map<String, Object> inCategory = Map.of("productCategory", MONITOR_CATEGORY);
        assertThat(resolver.options(process.findField("productVariantCode").orElseThrow(), inCategory).values())
                .containsExactly(MONITOR_WHITE);
    }

    @Test
    void otherProductsOfTheCategoryAreSuggestedWhenNoVariantMatchesTheAttributeValues() {
        ProcessDefinition process = processes.get(PROCESS);
        FieldDefinition variantField = process.findField("productVariantCode").orElseThrow();
        FieldDefinition suggested = process.findField("suggestedProductCode").orElseThrow();
        UUID id = service.start(PROCESS).conversation().getId();
        reply(new TurnDecision("Bạn muốn tìm loại nào?", Map.of("productSearch", "mặt nạ"), true,
                "choose_category"));
        service.sendMessage(id, "Tôi cần mặt nạ");
        reply(new TurnDecision("Bạn thích màu nào?", Map.of("productCategory", SKIN_CATEGORY), true,
                "collect_attributes"));
        service.sendMessage(id, "Chăm sóc da");

        reply(new TurnDecision("Không có mẫu nào, bạn muốn xem sản phẩm khác không?",
                Map.of("attributeValues", List.of(BLUE, "ATTR-T1:Oải hương")), false, null));
        Map<String, Object> data = service.sendMessage(id, "Màu xanh, mùi oải hương").conversation()
                .getCollectedData();

        assertThat(resolver.options(variantField, data).isEmpty()).isTrue();
        assertThat(resolver.options(suggested, data).values()).containsExactly(CatalogFixture.EYE_MASK);
        assertThat(prompts.systemPrompt(new TurnContext(process, process.state("collect_attributes"), data,
                List.of(), "", List.of()))).contains("suggestedProductCode (enum: " + CatalogFixture.EYE_MASK);

        // The customer takes a suggestion: the values are cleared and its variants are listed again.
        reply(new TurnDecision("Bạn chọn mẫu nào?",
                Map.of("suggestedProductCode", CatalogFixture.EYE_MASK, "attributeValues", List.of()), false, null));
        Map<String, Object> picked = service.sendMessage(id, "Cho tôi xem mặt nạ này").conversation()
                .getCollectedData();
        assertThat(picked).containsEntry("suggestedProductCode", CatalogFixture.EYE_MASK);
        assertThat(resolver.options(variantField, picked).values())
                .containsExactlyInAnyOrder(MASK_LAVENDER, MASK_MUGWORT);
    }

    @Test
    void eachSearchIsLookedUpSeparately() {
        FieldDefinition productCategory = processes.get(PROCESS).findField("productCategory").orElseThrow();
        when(categorySearch.search(anyString(), anyInt(), anyDouble())).thenAnswer(call ->
                List.of(new Match(new ProductCategory("TEST-9", "Found for " + call.getArgument(0), null), 0.5)));

        assertThat(resolver.options(productCategory, Map.of("productSearch", "một")).values())
                .containsExactly("Found for một");
        assertThat(resolver.options(productCategory, Map.of("productSearch", "hai")).values())
                .containsExactly("Found for hai");
    }

    @Test
    void theReplyIsWrittenAgainWithTheSearchResultsInView() {
        UUID id = service.start(PROCESS).conversation().getId();
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(
                new TurnDecision("Bạn cần loại sản phẩm nào?", Map.of("productSearch", "huyết áp"), true,
                        "choose_category"),
                new TurnDecision("Bạn cần máy đo huyết áp hay sản phẩm chăm sóc da?", Map.of(), false, null));

        TurnResult result = service.sendMessage(id, "Tôi cần máy đo huyết áp");

        assertThat(result.reply().getContent()).isEqualTo("Bạn cần máy đo huyết áp hay sản phẩm chăm sóc da?");
        assertThat(result.conversation().getCurrentState()).isEqualTo("choose_category");
        assertThat(result.outcome().previousState()).isEqualTo("find_product");
        assertThat(result.outcome().acceptedFields()).containsEntry("productSearch", "huyết áp");
        ArgumentCaptor<TurnContext> contexts = ArgumentCaptor.forClass(TurnContext.class);
        verify(turnGenerator, times(2)).generate(contexts.capture());
        TurnContext second = contexts.getAllValues().get(1);
        assertThat(second.state().id()).isEqualTo("choose_category");
        assertThat(second.collectedData()).containsEntry("productSearch", "huyết áp");
        assertThat(second.userMessage()).isEqualTo("Tôi cần máy đo huyết áp");
    }

    @Test
    void theCustomerIsAskedForTheUnitWhenTheVariantHasSeveral() {
        UUID id = service.start(PROCESS).conversation().getId();
        reply(new TurnDecision("Bạn muốn tìm loại nào?", Map.of("productSearch", "mặt nạ"), true,
                "choose_category"));
        service.sendMessage(id, "Tôi cần mặt nạ");
        reply(new TurnDecision("Bạn thích màu nào?", Map.of("productCategory", SKIN_CATEGORY), true,
                "collect_attributes"));
        service.sendMessage(id, "Chăm sóc da");
        clearInvocations(turnGenerator);

        reply(new TurnDecision("Bạn muốn mua theo đơn vị nào?", Map.of("productVariantCode", MASK_LAVENDER), true,
                "choose_unit"));
        TurnResult result = service.sendMessage(id, "Lấy mẫu màu tím");

        assertThat(result.conversation().getCurrentState()).isEqualTo("choose_unit");
        assertThat(result.conversation().getCollectedData()).doesNotContainKey("sellableUnitCode");
        // Entering a state whose actions add to the reply does not ask for a second look either.
        verify(turnGenerator, times(1)).generate(any(TurnContext.class));
    }

    @Test
    void attributeValuesNarrowTheVariantsAndTheNextAttributeIsOfferedAsButtons() {
        ProcessDefinition process = processes.get(PROCESS);
        FieldDefinition variantField = process.findField("productVariantCode").orElseThrow();
        StateDefinition collect = process.state("collect_attributes");
        UUID id = service.start(PROCESS).conversation().getId();
        reply(new TurnDecision("Bạn muốn tìm loại nào?", Map.of("productSearch", "mặt nạ"), true,
                "choose_category"));
        service.sendMessage(id, "Tôi cần mặt nạ");

        reply(new TurnDecision("Bạn thích màu nào?", Map.of("productCategory", SKIN_CATEGORY), true,
                "collect_attributes"));
        TurnResult first = service.sendMessage(id, "Chăm sóc da");

        Map<String, Object> data = first.conversation().getCollectedData();
        assertThat(first.conversation().getCurrentState()).isEqualTo("collect_attributes");
        assertThat(resolver.options(variantField, data).values()).containsExactlyInAnyOrder(MASK_LAVENDER, MASK_MUGWORT);
        assertThat(choices.forField(process, collect, data, "attributeValues").orElseThrow().options())
                .extracting(Option::value, Option::label)
                .containsExactly(tuple(PURPLE, "Tím"), tuple(BLUE, "Xanh"));
        assertThat(prompts.systemPrompt(new TurnContext(process, collect, data, List.of(), "", List.of())))
                .contains("Optional fields (ask for them when it helps; not needed to move on): attributeValues")
                .contains("Màu sắc: ATTR-24:Tím (Tím) [1] | ATTR-24:Xanh (Xanh) [1]")
                .contains("Next group to ask about: Màu sắc")
                .contains("Guidance for choosing in that group: Chỉ khác màu, công dụng như nhau - chọn theo sở thích.");

        // Naming a value of another attribute too is taken, and settles which variant is left.
        reply(new TurnDecision("Mẫu này mùi ngải cứu. Bạn xác nhận chứ?",
                Map.of("attributeValues", List.of(BLUE, MUGWORT)), false, null));
        TurnResult second = service.sendMessage(id, "Màu xanh, mùi ngải cứu");

        Map<String, Object> narrowed = second.conversation().getCollectedData();
        assertThat(narrowed).containsEntry("attributeValues", List.of(BLUE, MUGWORT));
        assertThat(resolver.options(variantField, narrowed).values()).containsExactly(MASK_MUGWORT);
        assertThat(resolver.options(variantField, narrowed).options()).extracting(Option::label)
                .containsExactly("Mặt nạ xông hơi mắt ngải cứu - Mugwort Steam Eyes Mask (Xanh / Ngải cứu) - giá cơ bản: Hộp x5 - 150.000 đ");
        // Nothing is left to ask, so no buttons.
        assertThat(choices.forField(process, collect, narrowed, "attributeValues")).isEmpty();
    }

    @Test
    void aVariantLeftOutByTheAttributeValuesIsNotAccepted() {
        UUID id = service.start(PROCESS).conversation().getId();
        reply(new TurnDecision("Bạn muốn tìm loại nào?", Map.of("productSearch", "mặt nạ"), true,
                "choose_category"));
        service.sendMessage(id, "Tôi cần mặt nạ");
        reply(new TurnDecision("Bạn thích màu nào?", Map.of("productCategory", SKIN_CATEGORY), true,
                "collect_attributes"));
        service.sendMessage(id, "Chăm sóc da");

        reply(new TurnDecision("Đây là thông tin sản phẩm.",
                Map.of("attributeValues", List.of(BLUE), "productVariantCode", MASK_LAVENDER), true, "choose_unit"));
        TurnResult result = service.sendMessage(id, "Màu xanh, mẫu oải hương");

        assertThat(result.conversation().getCurrentState()).isEqualTo("collect_attributes");
        assertThat(result.conversation().getCollectedData()).doesNotContainKey("productVariantCode");
    }

    private void reply(TurnDecision decision) {
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(decision);
    }
}
