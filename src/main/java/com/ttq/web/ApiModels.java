package com.ttq.web;

import com.ttq.conversation.ChatMessage;
import com.ttq.conversation.Conversation;
import com.ttq.conversation.ConversationStatus;
import com.ttq.conversation.MessageRole;
import com.ttq.process.ChoiceResolver.Choices;
import com.ttq.process.FieldOptions;
import com.ttq.process.ProcessDefinition;
import com.ttq.process.StateDefinition;
import com.ttq.process.Transition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies of the REST API. */
public final class ApiModels {

    private ApiModels() {
    }

    /** @param processId process to start in; omitted to start in the default one, which works out what the user wants */
    public record StartConversationRequest(String processId) {
    }

    public record SendMessageRequest(@NotBlank @Size(max = 4000) String content) {
    }

    public record StartConversationResponse(ConversationView conversation, MessageView greeting) {
    }

    /**
     * @param acceptedFields field values stored from this turn
     * @param ignoredFields  fields the model extracted that were unknown or invalid
     * @param transitionNote why the model's proposed transition was rejected, if it was
     * @param switchedFromProcess id of the process the conversation was handed over from this turn, or null
     */
    public record TurnResponse(
            MessageView reply,
            String previousState,
            boolean transitioned,
            Map<String, Object> acceptedFields,
            List<String> ignoredFields,
            String transitionNote,
            String switchedFromProcess,
            ConversationView conversation) {
    }

    public record StateView(String id, String objective, List<String> requiredFields, List<Transition> next,
                            boolean terminal) {

        static StateView of(StateDefinition s) {
            return new StateView(s.id(), s.objective(), s.requiredFields(), s.next(), s.terminal());
        }
    }

    /** @param choices answers a client may offer as buttons for the question just asked, or null */
    public record ConversationView(
            UUID id,
            String processId,
            String processName,
            String objective,
            ConversationStatus status,
            StateView currentState,
            Map<String, Object> collectedData,
            List<String> missingFields,
            ChoicesView choices,
            Instant createdAt,
            Instant updatedAt) {

        static ConversationView of(Conversation c, ProcessDefinition process, Choices choices) {
            StateDefinition state = process.state(c.getCurrentState());
            return new ConversationView(c.getId(), process.id(), process.name(), process.objective(), c.getStatus(),
                    StateView.of(state), c.getCollectedData(), state.missingFields(c.getCollectedData()),
                    ChoicesView.of(choices), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    /**
     * The answers on offer for one question. A client may show them as buttons and send the label
     * of the one picked as the user's next message, which reads in the transcript as if it had been
     * typed; the model maps it back to the value, which it was given with the field.
     *
     * @param field   name of the field being asked for
     * @param options its values, in the order the process or its catalog api gives them
     */
    public record ChoicesView(String field, List<ChoiceView> options) {

        static ChoicesView of(Choices choices) {
            return choices == null ? null : new ChoicesView(choices.field(),
                    choices.options().stream().map(ChoiceView::of).toList());
        }
    }

    /** @param label what to show on the button; the value itself when the source gave no label */
    public record ChoiceView(String value, String label) {

        static ChoiceView of(FieldOptions.Option option) {
            String label = option.label();
            return new ChoiceView(option.value(), label == null || label.isBlank() ? option.value() : label);
        }
    }

    public record MessageView(Long id, MessageRole role, String content, String state, Instant createdAt) {

        static MessageView of(ChatMessage m) {
            return new MessageView(m.getId(), m.getRole(), m.getContent(), m.getState(), m.getCreatedAt());
        }
    }
}
