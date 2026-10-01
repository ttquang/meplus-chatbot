package com.ttq.engine;

import com.ttq.conversation.ConversationStatus;
import com.ttq.conversation.MessageRole;
import com.ttq.engine.ConversationService.StartResult;
import com.ttq.engine.ConversationService.TurnResult;
import com.ttq.llm.TurnContext;
import com.ttq.llm.TurnDecision;
import com.ttq.llm.TurnGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ConversationServiceTest {

    @Autowired
    ConversationService service;

    @MockitoBean
    TurnGenerator turnGenerator;

    @Test
    void walksThroughLoanProcessToCompletion() {
        StartResult start = service.start("loan-application");
        UUID id = start.conversation().getId();
        assertThat(start.greeting().getContent()).contains("full name");
        assertThat(start.conversation().getCurrentState()).isEqualTo("greeting");

        reply(new TurnDecision("Thanks Ann! Are you employed, and what's your monthly income?",
                Map.of("fullName", "Ann Lee"), true, "collect_income"));
        TurnResult t1 = service.sendMessage(id, "I'm Ann Lee");
        assertThat(t1.outcome().transitioned()).isTrue();
        assertThat(t1.conversation().getCurrentState()).isEqualTo("collect_income");

        // Model tries to skip ahead with a field still missing: stays put.
        reply(new TurnDecision("How much would you like to borrow?",
                Map.of("employmentType", "employed"), true, "collect_amount"));
        TurnResult t2 = service.sendMessage(id, "I'm employed");
        assertThat(t2.conversation().getCurrentState()).isEqualTo("collect_income");
        assertThat(t2.outcome().note()).contains("monthlyIncome");
        assertThat(t2.conversation().getCollectedData()).containsEntry("employmentType", "EMPLOYED");

        reply(new TurnDecision("Great. How much and for how long?",
                Map.of("monthlyIncome", 5200), true, "collect_amount"));
        service.sendMessage(id, "5200 a month");

        reply(new TurnDecision("Here's a summary... is that right?",
                Map.of("amount", "10,000", "termMonths", 24), true, "summary"));
        TurnResult t4 = service.sendMessage(id, "10k over 24 months");
        assertThat(t4.conversation().getCurrentState()).isEqualTo("summary");
        assertThat(t4.conversation().getCollectedData()).containsEntry("termMonths", 24L);

        reply(new TurnDecision("Thank you! A loan officer will contact you.", Map.of(), true, "done"));
        TurnResult t5 = service.sendMessage(id, "Yes, correct");
        assertThat(t5.conversation().getStatus()).isEqualTo(ConversationStatus.COMPLETED);

        assertThat(service.messages(id)).hasSize(11)
                .first().extracting(m -> m.getRole()).isEqualTo(MessageRole.ASSISTANT);

        assertThatThrownBy(() -> service.sendMessage(id, "hello?"))
                .isInstanceOf(ConversationClosedException.class);
    }

    @Test
    void unknownConversationIsNotFound() {
        assertThatThrownBy(() -> service.sendMessage(UUID.randomUUID(), "hi"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    private void reply(TurnDecision decision) {
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(decision);
    }
}
