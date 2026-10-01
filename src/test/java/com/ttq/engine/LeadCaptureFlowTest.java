package com.ttq.engine;

import com.ttq.conversation.ConversationStatus;
import com.ttq.engine.ConversationService.TurnResult;
import com.ttq.lead.CustomerType;
import com.ttq.lead.Lead;
import com.ttq.lead.LeadRepository;
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

/**
 * Walks the lead capture process, checking the conditional routing, the phone verification and the
 * lead creation action. A real server runs so the catalog's HTTP calls hit the application's own
 * lead and mock OTP APIs. The test profile fixes the mock code to 123456.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chatbot.api.base-url=http://localhost:${local.server.port}")
@ActiveProfiles("test")
class LeadCaptureFlowTest {

    @Autowired
    ConversationService service;

    @Autowired
    LeadRepository leads;

    @MockitoBean
    TurnGenerator turnGenerator;

    @Test
    void individualLeadIsCreatedAndItsTicketAppendedToTheReply() {
        UUID id = service.start("loan-lead-capture").conversation().getId();

        reply(new TurnDecision("Nice to meet you, Ann.", Map.of(), true, "identify_customer"));
        service.sendMessage(id, "Hi, I'm Ann Lee");

        reply(new TurnDecision("What would you like the loan for?",
                Map.of("contactName", "Ann Lee", "customerType", "INDIVIDUAL"), true, "loan_needs"));
        service.sendMessage(id, "For myself");

        reply(new TurnDecision("Would you like an advisor to take this further?",
                Map.of("loanPurpose", "EQUIPMENT_MOTOR_VEHICLE", "loanAmount", 15000), true, "apply_decision"));
        service.sendMessage(id, "A car loan, about 15,000");

        // The model names the company branch by mistake; the condition redirects to the individual's profile.
        reply(new TurnDecision("Great, what line of business are you in?",
                Map.of("wantsToApply", true), true, "company_profile"));
        TurnResult routed = service.sendMessage(id, "Yes please");
        assertThat(routed.conversation().getCurrentState()).isEqualTo("individual_business_profile");
        assertThat(routed.outcome().note()).contains("Redirected");

        // No trade name needed: an individual's business may not have one.
        reply(new TurnDecision("And what's the best number for you?",
                Map.of("industry", "TRANSPORT", "totalAssetSize", "UP_TO_3M"), true, "contact_details"));
        TurnResult profiled = service.sendMessage(id, "I'll do deliveries, just starting out");
        assertThat(profiled.conversation().getCurrentState()).isEqualTo("contact_details");

        reply(new TurnDecision("Let me read that back to you...",
                Map.of("phoneNumber", "0900000001", "email", "ann@example.com", "city", "Da Nang",
                        "preferredContactTime", "EVENING"), true, "confirm"));
        service.sendMessage(id, "0900000001, ann@example.com, Da Nang, evenings");

        reply(new TurnDecision("Thanks! I've texted you a verification code, please type it here.",
                Map.of(), true, "verify_phone"));
        TurnResult verifying = service.sendMessage(id, "Yes, that's all correct");
        assertThat(verifying.conversation().getCurrentState()).isEqualTo("verify_phone");
        assertThat(verifying.conversation().getCollectedData()).containsKey("otpRequestId");
        // The masked number comes from the OTP api, not the model.
        assertThat(verifying.reply().getContent()).contains("We've sent a 6-digit code to ******0001");
        assertThat(leads.findByConversationId(id)).isEmpty();

        reply(new TurnDecision("Thank you! An advisor will call you within one business day.",
                Map.of("otpCode", "123456"), true, "submit_individual_lead"));
        TurnResult submitted = service.sendMessage(id, "123456");

        assertThat(submitted.conversation().getCurrentState()).isEqualTo("submit_individual_lead");
        assertThat(submitted.conversation().getStatus()).isEqualTo(ConversationStatus.COMPLETED);

        Lead lead = leads.findByConversationId(id).orElseThrow();
        String ticket = lead.getTicketNumber();
        assertThat(ticket).startsWith("LD-IND-");
        assertThat(lead.getCustomerType()).isEqualTo(CustomerType.INDIVIDUAL);
        assertThat(lead.getIndustry()).isEqualTo("TRANSPORT");
        assertThat(lead.getCompanyName()).isNull();
        assertThat(submitted.conversation().getCollectedData()).containsEntry("ticketNumber", ticket);
        // The model never sees the ticket, so the engine appends it rather than letting it guess.
        assertThat(submitted.reply().getContent())
                .startsWith("Thank you! An advisor will call you within one business day.")
                .contains("Your reference number is " + ticket);
        // The individual's SBLAF (ISP) checklist follows the reference number.
        assertThat(submitted.reply().getContent())
                .containsSubsequence("Your reference number is", "Documents to prepare",
                        "valid government-issued ID", "Marriage contract", "Business plan or proposal",
                        "For purchasing equipment or motor vehicles", "Purchase agreement or price quotation")
                .doesNotContain("authorized representative", "For refinancing", "Building permit");
    }

    @Test
    void businessLeadGoesThroughTheCompanyProfileAndCompanyApi() {
        UUID id = service.start("loan-lead-capture").conversation().getId();

        reply(new TurnDecision("Hello!", Map.of("contactName", "Bob Tran", "customerType", "PARTNERSHIP"),
                true, "identify_customer"));
        service.sendMessage(id, "Bob Tran, I represent Acme Ltd");

        reply(new TurnDecision("What does the company need?", Map.of(), true, "loan_needs"));
        service.sendMessage(id, "It's for our business");

        reply(new TurnDecision("Shall an advisor take this further?",
                Map.of("loanPurpose", "WORKING_CAPITAL", "loanAmount", 250000), true, "apply_decision"));
        service.sendMessage(id, "Working capital, 250k");

        reply(new TurnDecision("Tell me about the company.", Map.of("wantsToApply", true), true, null));
        TurnResult routed = service.sendMessage(id, "Yes");
        assertThat(routed.conversation().getCurrentState()).isEqualTo("company_profile");

        reply(new TurnDecision("And how can we reach you?",
                Map.of("companyName", "Acme Ltd", "industry", "RETAIL", "totalAssetSize", "FROM_3M_TO_15M"),
                true, "contact_details"));
        service.sendMessage(id, "Acme Ltd, retail, assets around 2 million");

        reply(new TurnDecision("Here's everything I have...",
                Map.of("phoneNumber", "0900000002", "email", "bob@acme.example", "city", "Hanoi",
                        "preferredContactTime", "MORNING"), true, "confirm"));
        service.sendMessage(id, "0900000002, bob@acme.example, Hanoi, mornings");

        reply(new TurnDecision("Please type the code we texted you.", Map.of(), true, "verify_phone"));
        service.sendMessage(id, "Confirmed");

        reply(new TurnDecision("Submitted, thank you.", Map.of("otpCode", "123 456"), true, "submit_company_lead"));
        TurnResult submitted = service.sendMessage(id, "123 456");

        assertThat(submitted.conversation().getCurrentState()).isEqualTo("submit_company_lead");
        Lead lead = leads.findByConversationId(id).orElseThrow();
        assertThat(lead.getTicketNumber()).startsWith("LD-COM-");
        assertThat(lead.getCustomerType()).isEqualTo(CustomerType.PARTNERSHIP);
        assertThat(submitted.reply().getContent()).contains("Your reference number is LD-COM-");
        // Only the partnership's documents from the SBLAF (CPC) checklist, not those of other types.
        assertThat(submitted.reply().getContent())
                .containsSubsequence("Documents to prepare", "ID of the authorized representative",
                        "Partnership resolution", "articles of partnership", "Financial documents")
                .doesNotContain("Marriage contract", "CDA certificate", "articles of incorporation")
                // Working capital needs no purpose-specific documents.
                .doesNotContain("For refinancing", "For construction", "For acquiring", "For purchasing");
    }

    @Test
    void aPersonalLoanEndsTheConversationBeforeAnyCustomerTypeIsCollected() {
        UUID id = service.start("loan-lead-capture").conversation().getId();

        reply(new TurnDecision("Nice to meet you, Joy. Is the loan for a business?",
                Map.of("contactName", "Joy Reyes"), true, "identify_customer"));
        service.sendMessage(id, "I'm Joy Reyes and I need a loan");

        reply(new TurnDecision("I'm sorry, we only offer business loans.", Map.of(), false, "no_lead"));
        TurnResult ended = service.sendMessage(id, "It's for my daughter's tuition");

        assertThat(ended.conversation().getCurrentState()).isEqualTo("no_lead");
        assertThat(ended.conversation().getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(ended.conversation().getCollectedData()).doesNotContainKey("customerType");
        assertThat(leads.findByConversationId(id)).isEmpty();
    }

    @Test
    void aWrongCodeIsRejectedAndAskedForAgain() {
        UUID id = reachVerifyPhone("0900000003");

        reply(new TurnDecision("Thank you, your request is submitted!",
                Map.of("otpCode", "000000"), true, "submit_individual_lead"));
        TurnResult rejected = service.sendMessage(id, "000000");

        assertThat(rejected.conversation().getCurrentState()).isEqualTo("verify_phone");
        assertThat(rejected.conversation().getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(rejected.outcome().actionFailed()).isTrue();
        assertThat(rejected.outcome().note()).contains("verifyOtp");
        // The user is told the code was wrong, not that the request went through or that it broke.
        assertThat(rejected.reply().getContent()).startsWith("That code didn't work.");
        // The rejected code is forgotten, so the assistant has to ask for it again.
        assertThat(rejected.conversation().getCollectedData().get("otpCode")).isNull();
        assertThat(rejected.outcome().missingFields()).containsExactly("otpCode");
        assertThat(leads.findByConversationId(id)).isEmpty();

        reply(new TurnDecision("Thank you!", Map.of("otpCode", "123456"), true, "submit_individual_lead"));
        TurnResult submitted = service.sendMessage(id, "Sorry, it's 123456");
        assertThat(submitted.conversation().getCurrentState()).isEqualTo("submit_individual_lead");
        assertThat(leads.findByConversationId(id)).isPresent();
        // The documents for this purpose, acquiring real estate, and no other purpose's.
        assertThat(submitted.reply().getContent())
                .contains("For acquiring real estate", "Price quotation of the property")
                .doesNotContain("For purchasing", "For construction");
    }

    @Test
    void goingBackToConfirmDoesNotNeedTheCodeAndConfirmingAgainSendsANewOne() {
        UUID id = reachVerifyPhone("0900000004");
        Object firstRequest = service.get(id).getCollectedData().get("otpRequestId");

        reply(new TurnDecision("Sure, here are your details again...", Map.of("phoneNumber", "0900000044"),
                false, "confirm"));
        TurnResult back = service.sendMessage(id, "I gave the wrong number, it's 0900000044");
        assertThat(back.conversation().getCurrentState()).isEqualTo("confirm");

        reply(new TurnDecision("I've texted a new code.", Map.of(), true, "verify_phone"));
        TurnResult resent = service.sendMessage(id, "Correct now");
        assertThat(resent.reply().getContent()).contains("******0044");
        assertThat(resent.conversation().getCollectedData().get("otpRequestId")).isNotEqualTo(firstRequest);
    }

    /** An individual customer who has just confirmed their details, with a code texted to them. */
    private UUID reachVerifyPhone(String phoneNumber) {
        UUID id = service.start("loan-lead-capture").conversation().getId();
        reply(new TurnDecision("Is the loan for you or a company?",
                Map.of("contactName", "Cara Vu", "customerType", "INDIVIDUAL"), true, "identify_customer"));
        service.sendMessage(id, "Cara Vu, for myself");
        reply(new TurnDecision("What is the loan for?", Map.of(), true, "loan_needs"));
        service.sendMessage(id, "That's right");
        reply(new TurnDecision("Shall an advisor call you?",
                Map.of("loanPurpose", "REAL_ESTATE_ACQUISITION", "loanAmount", 50000), true, "apply_decision"));
        service.sendMessage(id, "Home, 50k");
        reply(new TurnDecision("What's your line of business?", Map.of("wantsToApply", true), true,
                "individual_business_profile"));
        service.sendMessage(id, "Yes");
        reply(new TurnDecision("How can we reach you?",
                Map.of("industry", "SERVICES", "totalAssetSize", "UP_TO_3M"), true, "contact_details"));
        service.sendMessage(id, "I run a small bakery from home");
        reply(new TurnDecision("Let me read that back...",
                Map.of("phoneNumber", phoneNumber, "email", "cara@example.com", "city", "Hue",
                        "preferredContactTime", "ANYTIME"), true, "confirm"));
        service.sendMessage(id, phoneNumber + ", cara@example.com, Hue, anytime");
        reply(new TurnDecision("Please type the code we texted you.", Map.of(), true, "verify_phone"));
        TurnResult verifying = service.sendMessage(id, "All correct");
        assertThat(verifying.conversation().getCurrentState()).isEqualTo("verify_phone");
        return id;
    }

    private void reply(TurnDecision decision) {
        when(turnGenerator.generate(any(TurnContext.class))).thenReturn(decision);
    }
}
