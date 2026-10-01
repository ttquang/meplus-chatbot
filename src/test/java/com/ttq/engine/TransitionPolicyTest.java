package com.ttq.engine;

import com.ttq.engine.TransitionPolicy.TurnOutcome;
import com.ttq.llm.TurnDecision;
import com.ttq.process.FieldDefinition;
import com.ttq.process.FieldOptions;
import com.ttq.process.FieldOptionsResolver;
import com.ttq.process.FieldType;
import com.ttq.process.ProcessDefinition;
import com.ttq.process.StateDefinition;
import com.ttq.process.Transition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransitionPolicyTest {

    // Options come from the field definition here; the API-backed case is covered by its own test.
    private final FieldOptionsResolver fieldOptions = mock(FieldOptionsResolver.class);
    private final TransitionPolicy policy = new TransitionPolicy(fieldOptions);

    TransitionPolicyTest() {
        when(fieldOptions.options(any(FieldDefinition.class), anyMap()))
                .thenAnswer(call -> FieldOptions.fixed(call.<FieldDefinition>getArgument(0).values()));
    }

    private final ProcessDefinition process = new ProcessDefinition("p", "P", null, "objective", null, null,
            List.of(new FieldDefinition("name", FieldType.STRING, null, null),
                    new FieldDefinition("age", FieldType.INTEGER, null, null),
                    new FieldDefinition("plan", FieldType.ENUM, null, List.of("BASIC", "PRO"))),
            "ask_name",
            List.of(new StateDefinition("ask_name", "get name", List.of("name"),
                            List.of(Transition.of("ask_age")), false),
                    new StateDefinition("ask_age", "get age", List.of("age"),
                            List.of(Transition.of("ask_plan"), Transition.of("done")), false),
                    new StateDefinition("ask_plan", "get plan", List.of("plan"),
                            List.of(new Transition("pro_setup", Map.of("plan", "PRO")),
                                    new Transition("done", Map.of("plan", "BASIC"))), false),
                    new StateDefinition("pro_setup", "set up pro", List.of(),
                            List.of(Transition.of("done")), false),
                    new StateDefinition("done", "thank", List.of(), List.of(), true)));

    @Test
    void advancesWhenRequestedTransitionIsAllowedAndFieldsCollected() {
        TurnOutcome outcome = policy.apply(process, "ask_name", Map.of(),
                new TurnDecision("Hi Ann, how old are you?", Map.of("name", "Ann"), true, "ask_age"));

        assertThat(outcome.transitioned()).isTrue();
        assertThat(outcome.state().id()).isEqualTo("ask_age");
        assertThat(outcome.acceptedFields()).containsEntry("name", "Ann");
        assertThat(outcome.missingFields()).containsExactly("age");
        assertThat(outcome.note()).isNull();
    }

    @Test
    void rejectsTransitionWhenRequiredFieldsMissing() {
        TurnOutcome outcome = policy.apply(process, "ask_name", Map.of(),
                new TurnDecision("How old are you?", Map.of(), true, "ask_age"));

        assertThat(outcome.transitioned()).isFalse();
        assertThat(outcome.state().id()).isEqualTo("ask_name");
        assertThat(outcome.note()).contains("missing required fields");
    }

    @Test
    void rejectsTransitionToStateNotInNext() {
        TurnOutcome outcome = policy.apply(process, "ask_name", Map.of(),
                new TurnDecision("Done!", Map.of("name", "Ann"), true, "done"));

        assertThat(outcome.state().id()).isEqualTo("ask_name");
        assertThat(outcome.note()).contains("not an allowed next state");
    }

    @Test
    void autoAdvancesToSingleNextStateWhenObjectiveMet() {
        TurnOutcome outcome = policy.apply(process, "ask_name", Map.of(),
                new TurnDecision("Thanks!", Map.of("name", "Ann"), true, null));

        assertThat(outcome.state().id()).isEqualTo("ask_age");
    }

    @Test
    void staysWhenObjectiveMetButNextStateIsAmbiguous() {
        TurnOutcome outcome = policy.apply(process, "ask_age", Map.of("name", "Ann"),
                new TurnDecision("Thanks!", Map.of("age", 30), true, null));

        assertThat(outcome.state().id()).isEqualTo("ask_age");
    }

    @Test
    void normalizesValuesAndIgnoresUnknownOrInvalidFields() {
        TurnOutcome outcome = policy.apply(process, "ask_plan", Map.of(),
                new TurnDecision("ok", Map.of("plan", "pro", "age", "31", "color", "red"), false, null));

        assertThat(outcome.acceptedFields()).containsEntry("plan", "PRO").containsEntry("age", 31L);
        assertThat(outcome.ignoredFields()).containsExactly("color");

        TurnOutcome invalid = policy.apply(process, "ask_plan", Map.of(),
                new TurnDecision("ok", Map.of("plan", "ENTERPRISE"), false, null));
        assertThat(invalid.acceptedFields()).isEmpty();
        assertThat(invalid.ignoredFields()).containsExactly("plan");
    }

    @Test
    void enteringTerminalStateIsReportedAsTerminal() {
        TurnOutcome outcome = policy.apply(process, "ask_plan", Map.of(),
                new TurnDecision("Thanks, all done", Map.of("plan", "BASIC"), true, "done"));

        assertThat(outcome.state().terminal()).isTrue();
    }

    @Test
    void followsBranchWhoseConditionMatchesTheCollectedData() {
        TurnOutcome outcome = policy.apply(process, "ask_plan", Map.of(),
                new TurnDecision("Let's set up Pro", Map.of("plan", "pro"), true, "pro_setup"));

        assertThat(outcome.state().id()).isEqualTo("pro_setup");
        assertThat(outcome.note()).isNull();
    }

    @Test
    void redirectsToTheEligibleBranchWhenTheModelPicksTheWrongOne() {
        TurnOutcome outcome = policy.apply(process, "ask_plan", Map.of(),
                new TurnDecision("Let's set up Pro", Map.of("plan", "BASIC"), true, "pro_setup"));

        assertThat(outcome.state().id()).isEqualTo("done");
        assertThat(outcome.note()).contains("Redirected transition from 'pro_setup' to 'done'");
    }

    @Test
    void routesByConditionWhenModelNamesNoState() {
        TurnOutcome outcome = policy.apply(process, "ask_plan", Map.of(),
                new TurnDecision("Great choice", Map.of("plan", "PRO"), true, null));

        assertThat(outcome.state().id()).isEqualTo("pro_setup");
    }

    @Test
    void staysWhenNoBranchIsEligible() {
        ProcessDefinition noMatch = new ProcessDefinition("p", "P", null, "objective", null, null,
                List.of(new FieldDefinition("plan", FieldType.ENUM, null, List.of("BASIC", "PRO"))),
                "pick",
                List.of(new StateDefinition("pick", "pick a plan", List.of(),
                                List.of(new Transition("done", Map.of("plan", "PRO"))), false),
                        new StateDefinition("done", "thank", List.of(), List.of(), true)));

        // 'plan' was never collected, so the condition cannot be satisfied.
        TurnOutcome outcome = policy.apply(noMatch, "pick", Map.of(),
                new TurnDecision("ok", Map.of(), true, "done"));

        assertThat(outcome.state().id()).isEqualTo("pick");
        assertThat(outcome.note()).contains("condition").contains("not met");
    }
}
