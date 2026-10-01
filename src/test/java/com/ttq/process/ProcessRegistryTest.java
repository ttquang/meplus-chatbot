package com.ttq.process;

import com.ttq.ChatbotProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRegistryTest {

    private static final ChatbotProperties PROPERTIES =
            new ChatbotProperties("classpath*:processes/*.y*ml", "classpath*:apis/*.y*ml", 30, "failed",
                    "assistant", 6);

    @Test
    void loadsBundledProcesses() throws Exception {
        ProcessRegistry registry = new ProcessRegistry(PROPERTIES);

        ProcessDefinition loan = registry.get("loan-application");
        assertThat(loan.initialState()).isEqualTo("greeting");
        assertThat(loan.state("collect_income").requiredFields()).containsExactly("employmentType", "monthlyIncome");
        assertThat(loan.state("done").terminal()).isTrue();
        assertThat(loan.findField("termMonths")).map(FieldDefinition::type).contains(FieldType.INTEGER);
        // Short form: a plain state id parses as a transition with no condition.
        assertThat(loan.state("greeting").next()).containsExactly(Transition.of("collect_income"));
    }

    @Test
    void loadsConditionalTransitionsAndActions() throws Exception {
        ProcessRegistry registry = new ProcessRegistry(PROPERTIES);
        ProcessDefinition process = registry.get("loan-lead-capture");

        StateDefinition decision = process.state("apply_decision");
        assertThat(decision.nextStateIds()).containsExactly("company_profile", "individual_business_profile", "no_lead");
        assertThat(decision.findTransition("company_profile")).get()
                .extracting(Transition::when)
                .isEqualTo(Map.of("wantsToApply", true, "customerType", List.of("SOLE_PROPRIETORSHIP",
                        "COOPERATIVE", "PARTNERSHIP", "ONE_PERSON_CORPORATION", "CORPORATION")));

        assertThat(process.state("verify_phone").actions()).containsExactly("sendOtp");
        assertThat(process.state("submit_individual_lead").actions())
                .containsExactly("verifyOtp", "createIndividualLead");
        assertThat(process.state("submit_company_lead").actions()).containsExactly("verifyOtp", "createCompanyLead");
        assertThat(process.state("confirm").actions()).isEmpty();
        assertThat(process.state("verify_phone").findTransition("confirm")).get()
                .extracting(Transition::back).isEqualTo(true);
    }

    @Test
    void conditionsAreEvaluatedAgainstCollectedData() throws Exception {
        ProcessRegistry registry = new ProcessRegistry(PROPERTIES);
        ProcessDefinition process = registry.get("loan-lead-capture");
        StateDefinition decision = process.state("apply_decision");

        assertThat(process.eligibleTransitions(decision, Map.of("wantsToApply", true, "customerType", "CORPORATION")))
                .extracting(Transition::state).containsExactly("company_profile");
        assertThat(process.eligibleTransitions(decision, Map.of("wantsToApply", false, "customerType", "CORPORATION")))
                .extracting(Transition::state).containsExactly("no_lead");
        // Every type but an individual takes the company profile; an individual has its own.
        for (String businessType : List.of("SOLE_PROPRIETORSHIP", "COOPERATIVE", "PARTNERSHIP",
                "ONE_PERSON_CORPORATION")) {
            assertThat(process.eligibleTransitions(decision, Map.of("wantsToApply", true, "customerType", businessType)))
                    .extracting(Transition::state).containsExactly("company_profile");
        }
        assertThat(process.eligibleTransitions(decision, Map.of("wantsToApply", true, "customerType", "INDIVIDUAL")))
                .extracting(Transition::state).containsExactly("individual_business_profile");
        // Nothing collected yet: no branch is eligible.
        assertThat(process.eligibleTransitions(decision, Map.of())).isEmpty();
    }

    @Test
    void reportsStructuralErrors() {
        ProcessDefinition broken = new ProcessDefinition("broken", "Broken", null, "objective", null, null,
                List.of(new FieldDefinition("kind", FieldType.ENUM, null, null)),
                "missing",
                List.of(new StateDefinition("a", "do a", List.of("unknownField"),
                                List.of(Transition.of("nowhere")), false),
                        new StateDefinition("b", "do b", List.of(), List.of(Transition.of("a")), true),
                        new StateDefinition("c", "do c", List.of(), List.of(), false)));

        assertThat(ProcessRegistry.validate(broken)).containsExactlyInAnyOrder(
                "enum field 'kind' needs 'values' or 'valuesFrom'",
                "'initialState' must reference an existing state",
                "state 'a': unknown next state 'nowhere'",
                "state 'a': unknown required field 'unknownField'",
                "state 'b': terminal states cannot have 'next'",
                "state 'c': non-terminal states need 'next'");
    }

    @Test
    void reportsConditionErrors() {
        ProcessDefinition broken = new ProcessDefinition("broken", "Broken", null, "objective", null, null,
                List.of(new FieldDefinition("plan", FieldType.ENUM, null, List.of("BASIC", "PRO")),
                        new FieldDefinition("age", FieldType.INTEGER, null, null)),
                "a",
                List.of(new StateDefinition("a", "do a", List.of(),
                                List.of(new Transition("b", Map.of("plan", "ENTERPRISE")),
                                        new Transition("b", Map.of("unknown", "x")),
                                        new Transition("end", Map.of("age", "not a number"))), false),
                        new StateDefinition("b", "do b", List.of(), List.of(Transition.of("end")), false),
                        new StateDefinition("end", "done", List.of(), List.of(), true)));

        assertThat(ProcessRegistry.validate(broken)).containsExactlyInAnyOrder(
                "state 'a': transition to 'b': invalid value 'ENTERPRISE' for field 'plan' in 'when'",
                "state 'a': duplicate next state 'b'",
                "state 'a': transition to 'b': unknown field 'unknown' in 'when'",
                "state 'a': transition to 'end': invalid value 'not a number' for field 'age' in 'when'");
    }

    @Test
    void conditionsOnAFieldWhoseValuesComeFromAnApiAreAccepted() {
        FieldDefinition purpose = new FieldDefinition("purpose", FieldType.ENUM, null, null, "purposes");
        ProcessDefinition process = new ProcessDefinition("dynamic", "Dynamic", null, "objective", null, null,
                List.of(purpose), "a",
                List.of(new StateDefinition("a", "do a", List.of(),
                                List.of(new Transition("b", Map.of("purpose", List.of("CAR", "HOME"))),
                                        new Transition("end", Map.of("purpose", " "))), false),
                        new StateDefinition("b", "do b", List.of(), List.of(Transition.of("end")), false),
                        new StateDefinition("end", "done", List.of(), List.of(), true)));

        // The values are only known from the api, so any value is allowed except a blank one.
        assertThat(ProcessRegistry.validate(process)).containsExactly(
                "state 'a': transition to 'end': invalid value ' ' for field 'purpose' in 'when'");

        StateDefinition a = process.state("a");
        assertThat(process.eligibleTransitions(a, Map.of("purpose", "home")))
                .extracting(Transition::state).containsExactly("b");
        assertThat(process.eligibleTransitions(a, Map.of("purpose", "BOAT"))).isEmpty();
    }

    @Test
    void reportsFollowUpErrors() {
        ProcessDefinition broken = new ProcessDefinition("broken", "Broken", null, "objective", null, null,
                List.of(new FieldDefinition("done", FieldType.BOOLEAN, null, null)),
                "a",
                List.of(new StateDefinition("a", "do a", List.of(), List.of(Transition.of("end")), false, null,
                                List.of(new FollowUp(Map.of("done", "maybe"), "Anything else?"),
                                        new FollowUp(Map.of("unknown", true), "Hello?"),
                                        new FollowUp(Map.of(), " "))),
                        new StateDefinition("end", "done", List.of(), List.of(), true)));

        assertThat(ProcessRegistry.validate(broken)).containsExactlyInAnyOrder(
                "state 'a': followUp: invalid value 'maybe' for field 'done' in 'when'",
                "state 'a': followUp: unknown field 'unknown' in 'when'",
                "state 'a': every 'followUp' needs a 'message'");
    }

    @Test
    void loadsProcessSwitches() throws Exception {
        ProcessRegistry registry = new ProcessRegistry(PROPERTIES);

        assertThat(registry.get("assistant").switchTo())
                .containsExactly("loan-lead-capture", "loan-application-status");
        assertThat(registry.get("loan-lead-capture").switchTo()).containsExactly("loan-application-status");
        assertThat(registry.get("loan-application-status").switchTo()).containsExactly("loan-lead-capture");
        assertThat(registry.get("loan-application").switchTo()).isEmpty();
    }

    @Test
    void aProcessThatSwitchesMayHaveAStateWithoutNext() {
        ProcessDefinition router = new ProcessDefinition("router", "Router", null, "objective", null, null,
                List.of(), "triage", List.of(new StateDefinition("triage", "find out", List.of(), List.of(), false)),
                List.of("other"));
        ProcessDefinition self = new ProcessDefinition("self", "Self", null, "objective", null, null,
                List.of(), "triage", List.of(new StateDefinition("triage", "find out", List.of(), List.of(), false)),
                List.of("self"));

        assertThat(ProcessRegistry.validate(router)).isEmpty();
        assertThat(ProcessRegistry.validate(self)).containsExactly("'switchTo' cannot name the process itself");
    }

    @Test
    void loadsReadOnlyFieldsAndFollowUps() throws Exception {
        ProcessDefinition status = new ProcessRegistry(PROPERTIES).get("loan-application-status");

        assertThat(status.findField("applicationCompleted")).get().extracting(FieldDefinition::readOnly)
                .isEqualTo(true);
        assertThat(status.findField("advisorMessage")).get().extracting(FieldDefinition::readOnly)
                .isEqualTo(false);
        StateDefinition show = status.state("show_status");
        assertThat(status.followUpMessages(show, Map.of("applicationCompleted", false))).hasSize(1);
        assertThat(status.followUpMessages(show, Map.of("applicationCompleted", true))).isEmpty();
        assertThat(status.followUpMessages(show, Map.of())).isEmpty();
    }
}
