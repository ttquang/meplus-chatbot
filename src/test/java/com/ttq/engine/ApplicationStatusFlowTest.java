package com.ttq.engine;

import com.ttq.conversation.ConversationStatus;
import com.ttq.crm.LeadNote;
import com.ttq.crm.MockCrmService;
import com.ttq.engine.ConversationService.TurnResult;
import com.ttq.lead.LeadApiModels.CreateIndividualLeadRequest;
import com.ttq.lead.LeadService;
import com.ttq.lead.LeadStatus;
import com.ttq.llm.TurnContext;
import com.ttq.llm.TurnDecision;
import com.ttq.llm.TurnGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Walks the application status process against the application's own lead and mock OTP APIs.
 * The test profile fixes the mock code to 123456.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chatbot.api.base-url=http://localhost:${local.server.port}")
@ActiveProfiles("test")
class ApplicationStatusFlowTest {

    private static final String PHONE = "+639171112222";
    private static final String OFFER =
            "Would you like to leave a message for your loan advisor? I can pass it on with your application.";

    @Autowired
    ConversationService service;

    @Autowired
    LeadService leads;

    @Autowired
    MockCrmService crm;

    @MockitoBean
    TurnGenerator turnGenerator;

    String ticket;

    @BeforeEach
    void applicationInReview() {
        ticket = leads.createIndividual(new CreateIndividualLeadRequest("Eve Santos", "REAL_ESTATE_ACQUISITION", null,
                new BigDecimal("500000"), PHONE, "eve@example.com", "Makati", "MORNING", null, null, null, UUID.randomUUID()))
                .ticketNumber();
        leads.updateStatus(ticket, LeadStatus.IN_REVIEW);
    }

    @Test
    void theStatusIsShownOnceThePhoneIsVerified() {
        UUID id = reachVerifyPhone(ticket);

        reply(new TurnDecision("Thanks, you're verified. Here's the latest on your application:",
                Map.of("otpCode", "123456"), true, "show_status"));
        TurnResult shown = service.sendMessage(id, "123456");

        assertThat(shown.conversation().getCurrentState()).isEqualTo("show_status");
        assertThat(shown.conversation().getCollectedData())
                .containsEntry("applicationStatus", "IN_REVIEW")
                .containsEntry("applicationCompleted", false);
        // The status comes from the lead service, never from the model, and the offer to leave a
        // message comes after it, since the model could not know the application was in progress.
        assertThat(shown.reply().getContent())
                .startsWith("Thanks, you're verified.")
                .contains("Application " + ticket + ": In review.")
                .contains(LeadStatus.IN_REVIEW.description())
                .endsWith(OFFER);
        assertThat(shown.conversation().getStatus()).isEqualTo(ConversationStatus.ACTIVE);
    }

    @Test
    void aMessageForTheAdvisorIsConfirmedAndStored() {
        UUID id = reachShowStatus();

        reply(new TurnDecision("Of course. What would you like to tell your advisor?", Map.of(), true,
                "leave_message"));
        assertThat(service.sendMessage(id, "Yes please").conversation().getCurrentState())
                .isEqualTo("leave_message");

        reply(new TurnDecision("Here's your message: \"Please call me after 5pm.\" Shall I send it?",
                Map.of("advisorMessage", "Please call me after 5pm."), true, "confirm_message"));
        TurnResult confirming = service.sendMessage(id, "Please call me after 5pm.");
        assertThat(confirming.conversation().getCurrentState()).isEqualTo("confirm_message");
        assertThat(crm.notes(ticket)).get().asList().isEmpty();

        reply(new TurnDecision("Done! Your advisor will get back to you.", Map.of(), true, "message_sent"));
        TurnResult sent = service.sendMessage(id, "Yes, send it");

        assertThat(sent.conversation().getCurrentState()).isEqualTo("message_sent");
        assertThat(sent.conversation().getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        // Attached to the lead in the CRM, where the advisor reads it.
        assertThat(crm.notes(ticket)).get().asList().singleElement()
                .extracting("text", "type", "source")
                .containsExactly("Please call me after 5pm.", LeadNote.NoteType.CUSTOMER_MESSAGE, "CHATBOT");
        assertThat(sent.conversation().getCollectedData()).containsKey("advisorNoteId");
    }

    @Test
    void noMessageIsOfferedOnACompletedApplication() {
        leads.updateStatus(ticket, LeadStatus.APPROVED);
        UUID id = reachShowStatus();
        assertThat(service.get(id).getCollectedData()).containsEntry("applicationCompleted", true);

        // The model claims the application is still open and heads for leave_message; the value is
        // the system's alone, so it is ignored and the only branch left, done, is taken.
        reply(new TurnDecision("Sure, what's your message?", Map.of("applicationCompleted", false), true,
                "leave_message"));
        TurnResult result = service.sendMessage(id, "It's not finished, I want to leave a message");

        assertThat(result.outcome().ignoredFields()).containsExactly("applicationCompleted");
        assertThat(result.conversation().getCollectedData()).containsEntry("applicationCompleted", true);
        assertThat(result.conversation().getCurrentState()).isEqualTo("done");
        assertThat(crm.notes(ticket)).get().asList().isEmpty();
    }

    @Test
    void declaringTheOfferEndsTheConversation() {
        UUID id = reachShowStatus();

        reply(new TurnDecision("No problem. Have a great day!", Map.of(), true, "done"));
        TurnResult result = service.sendMessage(id, "No thanks");

        assertThat(result.conversation().getCurrentState()).isEqualTo("done");
        assertThat(result.conversation().getStatus()).isEqualTo(ConversationStatus.COMPLETED);
    }

    @Test
    void aWrongCodeShowsNothingAndIsAskedForAgain() {
        UUID id = reachVerifyPhone(ticket);

        reply(new TurnDecision("Here's your status:", Map.of("otpCode", "000000"), true, "show_status"));
        TurnResult rejected = service.sendMessage(id, "000000");

        assertThat(rejected.conversation().getCurrentState()).isEqualTo("verify_phone");
        assertThat(rejected.reply().getContent()).startsWith("That code didn't work.").doesNotContain("In review");
        assertThat(rejected.conversation().getCollectedData()).doesNotContainKey("applicationStatus");
        assertThat(rejected.outcome().missingFields()).containsExactly("otpCode");
    }

    @Test
    void aReferenceThatDoesNotMatchThePhoneIsAskedForAgainWithoutANewCode() {
        UUID id = reachVerifyPhone("LD-IND-999999");
        Object otpRequest = service.get(id).getCollectedData().get("otpRequestId");

        reply(new TurnDecision("Here's your status:", Map.of("otpCode", "123456"), true, "show_status"));
        TurnResult notFound = service.sendMessage(id, "123456");

        assertThat(notFound.conversation().getCurrentState()).isEqualTo("verify_phone");
        assertThat(notFound.reply().getContent()).startsWith("I couldn't find an application");
        assertThat(notFound.outcome().missingFields()).containsExactly("ticketNumber");

        // The verified code stays valid, so the corrected reference goes straight to the status.
        reply(new TurnDecision("Thanks, here it is:", Map.of("ticketNumber", ticket), true, "show_status"));
        TurnResult shown = service.sendMessage(id, "Sorry, it's " + ticket);

        assertThat(shown.conversation().getCurrentState()).isEqualTo("show_status");
        assertThat(shown.conversation().getCollectedData()).containsEntry("otpRequestId", otpRequest);
        assertThat(shown.reply().getContent()).contains("Application " + ticket + ": In review.");
    }

    @Test
    void someoneWithoutAReferenceIsToldWhereToFindIt() {
        UUID id = service.start("loan-application-status").conversation().getId();

        reply(new TurnDecision("The reference number was shown at the end of your application chat...",
                Map.of(), true, "no_reference"));
        TurnResult result = service.sendMessage(id, "I don't have one");

        assertThat(result.conversation().getCurrentState()).isEqualTo("no_reference");
        assertThat(result.conversation().getStatus()).isEqualTo(ConversationStatus.COMPLETED);
    }

    /** A verified customer who has just been shown their application's status. */
    private UUID reachShowStatus() {
        UUID id = reachVerifyPhone(ticket);
        reply(new TurnDecision("Thanks, here's the latest:", Map.of("otpCode", "123456"), true, "show_status"));
        TurnResult shown = service.sendMessage(id, "123456");
        assertThat(shown.conversation().getCurrentState()).isEqualTo("show_status");
        boolean completed = (Boolean) shown.conversation().getCollectedData().get("applicationCompleted");
        assertThat(shown.reply().getContent().endsWith(OFFER)).isEqualTo(!completed);
        return id;
    }

    /** A customer who has given a reference number and phone, with a code texted to them. */
    private UUID reachVerifyPhone(String ticketNumber) {
        UUID id = service.start("loan-application-status").conversation().getId();
        reply(new TurnDecision("I've texted you a verification code, please type it here.",
                Map.of("ticketNumber", ticketNumber, "phoneNumber", PHONE), true, "verify_phone"));
        TurnResult verifying = service.sendMessage(id, ticketNumber + ", 0917 111 2222");
        assertThat(verifying.conversation().getCurrentState()).isEqualTo("verify_phone");
        assertThat(verifying.reply().getContent()).contains("We've sent a 6-digit code to ********2222");
        return id;
    }

    private void reply(TurnDecision decision) {
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(decision);
    }
}
