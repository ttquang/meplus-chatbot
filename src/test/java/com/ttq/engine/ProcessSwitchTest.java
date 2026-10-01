package com.ttq.engine;

import com.ttq.conversation.ChatMessage;
import com.ttq.engine.ConversationService.StartResult;
import com.ttq.engine.ConversationService.TurnResult;
import com.ttq.llm.TurnContext;
import com.ttq.llm.TurnDecision;
import com.ttq.llm.TurnGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Conversations handed over from one process to another based on what the user writes. */
@SpringBootTest
@ActiveProfiles("test")
class ProcessSwitchTest {

    @Autowired
    ConversationService service;

    @MockitoBean
    TurnGenerator turnGenerator;

    @Test
    void theAssistantHandsOverToTheProcessTheUserAsksFor() {
        StartResult start = service.start("assistant");
        UUID id = start.conversation().getId();
        assertThat(start.greeting().getContent()).contains("apply for a business loan").contains("status");

        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(
                new TurnDecision("One moment.", Map.of(), false, null, "loan-application-status"),
                new TurnDecision("Thanks! What mobile number did you apply with?",
                        Map.of("ticketNumber", "LD-IND-000042"), false, null));
        TurnResult turn = service.sendMessage(id, "Where is my application LD-IND-000042?");

        assertThat(turn.switchedFrom()).isEqualTo("assistant");
        assertThat(turn.conversation().getProcessId()).isEqualTo("loan-application-status");
        assertThat(turn.conversation().getCurrentState()).isEqualTo("welcome");
        // The new process answered the same message, so the reference number is not asked again.
        assertThat(turn.conversation().getCollectedData()).containsEntry("ticketNumber", "LD-IND-000042");
        assertThat(turn.reply().getContent()).isEqualTo("Thanks! What mobile number did you apply with?");

        ArgumentCaptor<TurnContext> contexts = ArgumentCaptor.forClass(TurnContext.class);
        verify(turnGenerator, times(2)).generate(contexts.capture());
        List<TurnContext> calls = contexts.getAllValues();
        assertThat(calls.get(0).switchTargets()).extracting(p -> p.id())
                .containsExactly("loan-lead-capture", "loan-application-status");
        assertThat(calls.get(1).process().id()).isEqualTo("loan-application-status");
        assertThat(calls.get(1).userMessage()).isEqualTo("Where is my application LD-IND-000042?");
        assertThat(calls.get(1).switchTargets()).isEmpty();

        assertThat(service.messages(id)).extracting(ChatMessage::getState)
                .containsExactly("triage", "triage", "welcome");
    }

    @Test
    void switchingMidwayStartsTheOtherProcessAfresh() {
        UUID id = service.start("loan-lead-capture").conversation().getId();
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(
                new TurnDecision("Nice to meet you, Ann.", Map.of("contactName", "Ann Reyes"), true,
                        "identify_customer"));
        service.sendMessage(id, "I'm Ann Reyes");

        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(
                new TurnDecision("Sure.", Map.of(), false, null, "loan-application-status"),
                new TurnDecision("What is your reference number?", Map.of(), false, null));
        TurnResult turn = service.sendMessage(id, "Actually I already applied, I want to check my status");

        assertThat(turn.switchedFrom()).isEqualTo("loan-lead-capture");
        assertThat(turn.conversation().getProcessId()).isEqualTo("loan-application-status");
        assertThat(turn.conversation().getCurrentState()).isEqualTo("welcome");
        assertThat(turn.conversation().getCollectedData()).doesNotContainKey("contactName");
    }

    @Test
    void aSwitchToAProcessNotListedIsRefused() {
        UUID id = service.start("assistant").conversation().getId();
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(
                new TurnDecision("Would you like to apply, or check an application?", Map.of(), false, null,
                        "loan-application"));

        TurnResult turn = service.sendMessage(id, "hello");

        assertThat(turn.switchedFrom()).isNull();
        assertThat(turn.conversation().getProcessId()).isEqualTo("assistant");
        assertThat(turn.reply().getContent()).isEqualTo("Would you like to apply, or check an application?");
        verify(turnGenerator, times(1)).generate(any(TurnContext.class));
    }

    @Test
    void theAssistantAsksWhenItIsUnclear() {
        UUID id = service.start("assistant").conversation().getId();
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(
                new TurnDecision("Happy to help! Do you want to apply for a loan, or check an application?",
                        Map.of(), false, null));

        TurnResult turn = service.sendMessage(id, "hi there");

        assertThat(turn.switchedFrom()).isNull();
        assertThat(turn.conversation().getProcessId()).isEqualTo("assistant");
        assertThat(turn.conversation().getCurrentState()).isEqualTo("triage");
    }
}
