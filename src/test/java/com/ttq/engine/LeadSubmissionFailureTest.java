package com.ttq.engine;

import com.ttq.conversation.ConversationStatus;
import com.ttq.engine.ConversationService.TurnResult;
import com.ttq.lead.LeadApiModels.CreateIndividualLeadRequest;
import com.ttq.lead.LeadService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** When the lead API fails, the conversation must not claim the request was submitted. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chatbot.api.base-url=http://localhost:${local.server.port}")
@ActiveProfiles("test")
class LeadSubmissionFailureTest {

    @Autowired
    ConversationService service;

    @MockitoBean
    TurnGenerator turnGenerator;

    @MockitoBean
    LeadService leads;

    @Test
    void aFailedSubmissionKeepsTheConversationInVerifyPhone() {
        when(leads.createIndividual(any(CreateIndividualLeadRequest.class)))
                .thenThrow(new IllegalStateException("lead API is down"));

        UUID id = service.start("loan-lead-capture").conversation().getId();

        reply(new TurnDecision("What would you like the loan for?",
                Map.of("contactName", "Ann Lee", "customerType", "INDIVIDUAL"), true, "identify_customer"));
        service.sendMessage(id, "Hi, I'm Ann Lee, borrowing for myself");

        reply(new TurnDecision("Shall an advisor take this further?",
                Map.of("loanPurpose", "EQUIPMENT_MOTOR_VEHICLE", "loanAmount", 15000), true, "loan_needs"));
        service.sendMessage(id, "A car loan, 15,000");

        reply(new TurnDecision("What's your line of business?", Map.of("wantsToApply", true), true, "apply_decision"));
        service.sendMessage(id, "Yes please");

        reply(new TurnDecision("How can we reach you?",
                Map.of("industry", "TRANSPORT", "totalAssetSize", "UP_TO_3M"), true, "individual_business_profile"));
        service.sendMessage(id, "Deliveries, just starting");

        reply(new TurnDecision("Let me read that back...",
                Map.of("phoneNumber", "0900000001", "email", "ann@example.com", "city", "Da Nang",
                        "preferredContactTime", "EVENING"), true, "contact_details"));
        service.sendMessage(id, "0900000001, ann@example.com, Da Nang, evenings");

        reply(new TurnDecision("All done, your request is submitted!", Map.of(), true, "confirm"));
        service.sendMessage(id, "Looks right");

        reply(new TurnDecision("Please type the code we texted you.", Map.of(), true, "verify_phone"));
        service.sendMessage(id, "Yes, confirmed");

        // The code is right, so verifyOtp passes and the lead api is the one that fails.
        reply(new TurnDecision("Thank you! Your request is submitted.", Map.of("otpCode", "123456"), true,
                "submit_individual_lead"));
        TurnResult failed = service.sendMessage(id, "123456");

        assertThat(failed.conversation().getCurrentState()).isEqualTo("verify_phone");
        assertThat(failed.conversation().getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(failed.outcome().actionFailed()).isTrue();
        assertThat(failed.outcome().note()).contains("createIndividualLead").contains("failed");
        // The model's optimistic reply is replaced, so the user is not told it worked.
        assertThat(failed.reply().getContent()).doesNotContain("submitted")
                .isEqualTo("Sorry, I couldn't submit your request just now. Shall I try again?");
        // Not the user's fault, so the verified code is kept for the retry.
        assertThat(failed.conversation().getCollectedData()).containsEntry("otpCode", "123456");
    }

    private void reply(TurnDecision decision) {
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(decision);
    }
}
