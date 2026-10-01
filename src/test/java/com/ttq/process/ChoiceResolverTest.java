package com.ttq.process;

import com.ttq.process.ChoiceResolver.Choices;
import com.ttq.process.FieldOptions.Option;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The answers a question may offer as buttons come from the process, never from the model: it only
 * says which field it asked for. A real server runs so the loan purposes are looked up for real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chatbot.api.base-url=http://localhost:${local.server.port}")
@ActiveProfiles("test")
class ChoiceResolverTest {

    @Autowired
    ProcessRegistry processes;

    @Autowired
    ChoiceResolver resolver;

    private ProcessDefinition leadCapture() {
        return processes.get("loan-lead-capture");
    }

    private Optional<Choices> asking(String stateId, Map<String, Object> collectedData, String field) {
        ProcessDefinition process = leadCapture();
        return resolver.forField(process, process.state(stateId), collectedData, field);
    }

    @Test
    void aBooleanFieldOffersYesAndNo() {
        Optional<Choices> choices = asking("apply_decision",
                Map.of("contactName", "Ann Lee", "customerType", "INDIVIDUAL"), "wantsToApply");

        assertThat(choices).get().extracting(Choices::field).isEqualTo("wantsToApply");
        assertThat(choices.orElseThrow().options())
                .containsExactly(new Option("true", "Yes"), new Option("false", "No"));
    }

    @Test
    void anEnumFieldOffersTheValuesItAccepts() {
        Optional<Choices> choices = asking("identify_customer", Map.of("contactName", "Ann Lee"), "customerType");

        assertThat(choices.orElseThrow().options()).extracting(Option::value).containsExactly(
                "INDIVIDUAL", "SOLE_PROPRIETORSHIP", "COOPERATIVE", "PARTNERSHIP", "ONE_PERSON_CORPORATION",
                "CORPORATION");
    }

    @Test
    void aFieldTheModelDidNotNameOffersNothing() {
        Map<String, Object> collected = Map.of("contactName", "Ann Lee");

        assertThat(asking("identify_customer", collected, null)).isEmpty();
        assertThat(asking("identify_customer", collected, " ")).isEmpty();
    }

    @Test
    void aFieldThisStateIsNotCollectingOffersNothing() {
        // wantsToApply belongs to a later state, and a field already collected is not being asked for.
        assertThat(asking("identify_customer", Map.of("contactName", "Ann Lee"), "wantsToApply")).isEmpty();
        assertThat(asking("identify_customer", Map.of("contactName", "Ann Lee", "customerType", "INDIVIDUAL"),
                "customerType")).isEmpty();
        assertThat(asking("identify_customer", Map.of(), "madeUpField")).isEmpty();
    }

    @Test
    void aFieldWithNoFixedValuesOffersNothing() {
        assertThat(asking("identify_customer", Map.of(), "contactName")).isEmpty();
    }

    @Test
    void tooManyValuesAreLeftToTheReply() {
        // The reference api returns eight loan purposes, more than chatbot.max-choices.
        assertThat(asking("loan_needs", Map.of("customerType", "INDIVIDUAL"), "loanPurpose")).isEmpty();
    }

    @Test
    void withoutAModelTurnTheNextMissingFieldIsGuessed() {
        ProcessDefinition process = leadCapture();

        Optional<Choices> choices = resolver.forNextMissingField(process, process.state("identify_customer"),
                Map.of("contactName", "Ann Lee"));

        assertThat(choices).get().extracting(Choices::field).isEqualTo("customerType");
        // Nothing is guessed while the state's first missing field is one with no values to offer.
        assertThat(resolver.forNextMissingField(process, process.state("identify_customer"), Map.of())).isEmpty();
    }

    @Test
    void aFieldAnActionSetsIsNeverOffered() {
        // Read-only fields are filled by an action, so the user is never asked for them.
        FieldDefinition verified = new FieldDefinition("verified", FieldType.BOOLEAN, "set by an action",
                List.of(), null, true);
        FieldDefinition confirmed = new FieldDefinition("confirmed", FieldType.BOOLEAN, "asked of the user",
                List.of());
        StateDefinition state = new StateDefinition("check", "Check it", List.of("verified", "confirmed"),
                List.of(), false);
        ProcessDefinition process = new ProcessDefinition("test", "Test", null, "Check things", null, null,
                List.of(verified, confirmed), "check", List.of(state));

        assertThat(resolver.forField(process, state, Map.of(), "verified")).isEmpty();
        assertThat(resolver.forNextMissingField(process, state, Map.of())).get()
                .extracting(Choices::field).isEqualTo("confirmed");
    }
}
