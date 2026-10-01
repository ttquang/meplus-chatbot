package com.ttq.process;

import com.ttq.ChatbotProperties;
import com.ttq.process.FieldOptions.Option;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Works out the answers a client may offer as buttons for the question just asked.
 *
 * <p>The values themselves are never taken from the model: a boolean field offers yes and no, and
 * an enum offers what {@link FieldOptionsResolver} currently resolves for it, which for a dynamic
 * enum is what its catalog API returned. So a button can only ever carry a value
 * {@link FieldDefinition#normalize} accepts.
 *
 * <p>Which field is being asked for is the part the model is better at, since its reply may ask
 * for any of the state's missing fields, or for none of them. {@link #forField} takes the field the
 * model named and offers nothing when it named none; {@link #forNextMissingField} guesses at the
 * next missing field instead, for when there is no model turn to go by, such as a conversation
 * picked up again after a reload.
 */
@Component
public class ChoiceResolver {

    private final FieldOptionsResolver fieldOptions;
    private final ChatbotProperties properties;

    public ChoiceResolver(FieldOptionsResolver fieldOptions, ChatbotProperties properties) {
        this.fieldOptions = fieldOptions;
        this.properties = properties;
    }

    /**
     * Choices for the field the model said its reply asks for, or none when it named no usable
     * field. That is a missing required field of the state, or one of its optional fields.
     */
    public Optional<Choices> forField(ProcessDefinition process, StateDefinition state,
                                      Map<String, Object> collectedData, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return Optional.empty();
        }
        String name = fieldName.strip();
        return Stream.concat(askableFields(process, state, collectedData).stream(),
                        state.optionalFields().stream().map(process::findField).flatMap(Optional::stream)
                                .filter(f -> !f.readOnly()))
                .filter(f -> f.name().equals(name))
                .findFirst()
                .flatMap(f -> choices(f, collectedData));
    }

    /** Choices for the first field of this state still to be collected, as a best guess. */
    public Optional<Choices> forNextMissingField(ProcessDefinition process, StateDefinition state,
                                                 Map<String, Object> collectedData) {
        return askableFields(process, state, collectedData).stream()
                .findFirst()
                .flatMap(f -> choices(f, collectedData));
    }

    /**
     * The state's missing required fields that the user can be asked for, in the order the state
     * lists them. A read-only field is set by an action rather than by the user, so it is left out.
     */
    private static List<FieldDefinition> askableFields(ProcessDefinition process, StateDefinition state,
                                                       Map<String, Object> collectedData) {
        return state.missingFields(collectedData).stream()
                .map(process::findField)
                .flatMap(Optional::stream)
                .filter(f -> !f.readOnly())
                .toList();
    }

    /**
     * The values of a field worth showing as buttons: yes and no for a boolean, the resolved
     * options for an enum, and for a tags field those of the next group worth asking about. Nothing
     * for any other type, for options that could not be resolved, and for a list too long to show
     * as buttons.
     */
    private Optional<Choices> choices(FieldDefinition field, Map<String, Object> collectedData) {
        List<Option> options = switch (field.type()) {
            case BOOLEAN -> List.of(new Option("true", "Yes"), new Option("false", "No"));
            case ENUM -> fieldOptions.options(field, collectedData).options();
            case TAGS -> fieldOptions.options(field, collectedData).nextGroup(collected(field, collectedData));
            default -> List.of();
        };
        if (options.isEmpty() || options.size() > properties.maxChoices()) {
            return Optional.empty();
        }
        return Optional.of(new Choices(field.name(), options));
    }

    /** The values already collected for a tags field, empty when there are none. */
    private static Collection<?> collected(FieldDefinition field, Map<String, Object> collectedData) {
        return collectedData.get(field.name()) instanceof Collection<?> values ? values : List.of();
    }

    /**
     * Answers offered for one question.
     *
     * @param field   name of the field being asked for
     * @param options the values it currently accepts, in the order the field's source gives them
     */
    public record Choices(String field, List<Option> options) {

        public Choices {
            options = List.copyOf(options);
        }
    }
}
