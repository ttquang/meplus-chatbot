package com.ttq.web;

import com.ttq.llm.TurnContext;
import com.ttq.llm.TurnDecision;
import com.ttq.llm.TurnGenerator;
import com.ttq.web.ApiModels.ChoiceView;
import com.ttq.web.ApiModels.ConversationView;
import com.ttq.web.ApiModels.SendMessageRequest;
import com.ttq.web.ApiModels.StartConversationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A reply that asks for a field carries that field's values, so a client can offer them as buttons
 * without knowing anything about the process.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConversationChoicesApiTest {

    @Autowired
    ConversationController controller;

    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    TurnGenerator turnGenerator;

    @Test
    void aReplyAskingForAFieldCarriesTheValuesItAccepts() {
        UUID id = startLeadCapture();

        reply(new TurnDecision("Nice to meet you, Ann. What sort of borrower are you?",
                Map.of("contactName", "Ann Lee"), true, "identify_customer", null, "customerType"));
        ConversationView conversation = send(id, "I'm Ann Lee").conversation();

        assertThat(conversation.choices().field()).isEqualTo("customerType");
        assertThat(conversation.choices().options()).extracting(ChoiceView::value).containsExactly(
                "INDIVIDUAL", "SOLE_PROPRIETORSHIP", "COOPERATIVE", "PARTNERSHIP", "ONE_PERSON_CORPORATION",
                "CORPORATION");
        // The process file gives these values no labels, so each button shows the value itself.
        assertThat(conversation.choices().options()).allSatisfy(o -> assertThat(o.label()).isEqualTo(o.value()));
        assertThat(jsonMapper.writeValueAsString(conversation))
                .contains("\"choices\":{\"field\":\"customerType\",\"options\":[{\"value\":\"INDIVIDUAL\"");
    }

    @Test
    void aReplyThatNamesNoFieldCarriesNoValues() {
        UUID id = startLeadCapture();

        reply(new TurnDecision("Nice to meet you, Ann. Is the loan for a business?",
                Map.of("contactName", "Ann Lee"), true, "identify_customer"));

        assertThat(send(id, "I'm Ann Lee").conversation().choices()).isNull();
    }

    @Test
    void aConversationPickedUpAgainOffersTheValuesOfTheNextFieldToCollect() {
        UUID id = startLeadCapture();
        reply(new TurnDecision("Nice to meet you, Ann. Is the loan for a business?",
                Map.of("contactName", "Ann Lee"), true, "identify_customer"));
        send(id, "I'm Ann Lee");

        // Nothing the model said is at hand after a reload, so the next field to collect is offered.
        assertThat(controller.get(id).choices().field()).isEqualTo("customerType");
    }

    private UUID startLeadCapture() {
        ConversationView conversation =
                controller.start(new StartConversationRequest("loan-lead-capture")).conversation();
        // The greeting asks for a name, which has no values to offer.
        assertThat(conversation.choices()).isNull();
        return conversation.id();
    }

    private ApiModels.TurnResponse send(UUID id, String content) {
        return controller.sendMessage(id, new SendMessageRequest(content));
    }

    private void reply(TurnDecision decision) {
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(decision);
    }
}
