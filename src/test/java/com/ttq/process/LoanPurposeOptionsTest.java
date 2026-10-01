package com.ttq.process;

import com.ttq.api.ApiCallExecutor;
import com.ttq.api.ApiCatalog;
import com.ttq.engine.TransitionPolicy;
import com.ttq.engine.TransitionPolicy.TurnOutcome;
import com.ttq.lead.CustomerType;
import com.ttq.llm.TurnDecision;
import com.ttq.reference.LoanPurpose;
import com.ttq.reference.LoanPurposeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The loan purposes on offer come from the reference API, not the process file, so they depend on
 * the customer type and can change while the application is running.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chatbot.api.base-url=http://localhost:${local.server.port}")
@ActiveProfiles("test")
class LoanPurposeOptionsTest {

    @Autowired
    ProcessRegistry processes;

    @Autowired
    ApiCatalog apis;

    @Autowired
    ApiCallExecutor executor;

    @Autowired
    FieldOptionsResolver resolver;

    @Autowired
    LoanPurposeRepository purposes;

    @Autowired
    TransitionPolicy policy;

    private FieldDefinition loanPurpose() {
        return processes.get("loan-lead-capture").findField("loanPurpose").orElseThrow();
    }

    @Test
    void everyCustomerTypeStartsWithTheSblafPurposesInFormOrder() {
        for (CustomerType type : CustomerType.values()) {
            FieldOptions options = resolver.options(loanPurpose(), Map.of("customerType", type.name()));
            assertThat(options.dynamic()).isTrue();
            assertThat(options.values()).as(type.name()).containsExactly("WORKING_CAPITAL",
                    "REAL_ESTATE_CONSTRUCTION", "REAL_ESTATE_ACQUISITION", "LOAN_TAKEOUT_REFINANCING",
                    "BUSINESS_EXPANSION", "EQUIPMENT_MOTOR_VEHICLE", "BIOLOGICAL_ASSET", "OTHER");
        }
        FieldOptions individual = resolver.options(loanPurpose(), Map.of("customerType", "INDIVIDUAL"));
        assertThat(individual.options()).extracting(FieldOptions.Option::label)
                .contains("Working capital (including receivables and inventory financing)", "Loan takeout/refinancing");
    }

    @Test
    void aPurposeNotOnOfferIsNotAccepted() {
        ProcessDefinition process = processes.get("loan-lead-capture");

        TurnOutcome outcome = policy.apply(process, "loan_needs",
                Map.of("contactName", "Bob Tran", "customerType", "CORPORATION"),
                new TurnDecision("Noted", Map.of("loanPurpose", "CAR", "loanAmount", 250000), false, null));

        assertThat(outcome.ignoredFields()).containsExactly("loanPurpose");
        assertThat(outcome.acceptedFields()).containsOnlyKeys("loanAmount");
    }

    @Test
    void addingAPurposeToOneCustomerTypeOnlyChangesWhatThatTypeIsOffered() {
        LoanPurpose added = purposes.save(
                new LoanPurpose(CustomerType.COOPERATIVE, "MEMBER_RELENDING", "Relending to members", 9));
        try {
            // A fresh resolver, because the running one caches lookups for five minutes.
            FieldOptionsResolver fresh = new FieldOptionsResolver(apis, executor);

            assertThat(fresh.options(loanPurpose(), Map.of("customerType", "COOPERATIVE")).values())
                    .contains("MEMBER_RELENDING");
            assertThat(fresh.options(loanPurpose(), Map.of("customerType", "CORPORATION")).values())
                    .doesNotContain("MEMBER_RELENDING");
        } finally {
            purposes.delete(added);
        }
    }
}
