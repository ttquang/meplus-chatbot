package com.ttq.llm;

import com.ttq.process.FieldDefinition;
import com.ttq.process.FieldOptions;
import com.ttq.process.FieldOptionsResolver;
import com.ttq.process.FieldType;
import com.ttq.process.ProcessDefinition;
import com.ttq.process.StateDefinition;
import com.ttq.process.Transition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the system prompt that tells the model where the conversation is in its process.
 */
@Component
public class TurnPromptFactory {

    private final JsonMapper jsonMapper;
    private final FieldOptionsResolver fieldOptions;

    public TurnPromptFactory(JsonMapper jsonMapper, FieldOptionsResolver fieldOptions) {
        this.jsonMapper = jsonMapper;
        this.fieldOptions = fieldOptions;
    }

    public String systemPrompt(TurnContext ctx) {
        ProcessDefinition process = ctx.process();
        StateDefinition state = ctx.state();
        StringBuilder sb = new StringBuilder();

        if (process.instructions() != null && !process.instructions().isBlank()) {
            sb.append(process.instructions().strip()).append("\n\n");
        }
        sb.append("You are guiding the user through the process \"").append(process.name()).append("\".\n");
        sb.append("Overall objective: ").append(process.objective().strip()).append("\n\n");

        sb.append("## Current state: ").append(state.id()).append('\n');
        sb.append("Objective: ").append(state.objective().strip()).append('\n');
        if (state.requiredFields().isEmpty()) {
            sb.append("Required fields: none\n");
        } else {
            List<String> missing = state.missingFields(ctx.collectedData());
            sb.append("Required fields: ").append(String.join(", ", state.requiredFields()))
                    .append(" (still missing: ").append(missing.isEmpty() ? "none" : String.join(", ", missing))
                    .append(")\n");
        }
        if (!state.optionalFields().isEmpty()) {
            sb.append("Optional fields (ask for them when it helps; not needed to move on): ")
                    .append(String.join(", ", state.optionalFields())).append('\n');
        }

        sb.append("\n## Allowed next states\n");
        if (state.next().isEmpty()) {
            sb.append(state.terminal() ? "None. This is the final state.\n"
                    : "None. Stay in this state until the conversation is handed over to another process.\n");
        }
        for (Transition transition : state.next()) {
            process.findState(transition.state()).ifPresent(next -> {
                sb.append("- ").append(next.id()).append(": ").append(next.objective().strip());
                if (!transition.when().isEmpty()) {
                    sb.append(" (only when ").append(describe(transition.when())).append(')');
                }
                if (transition.back()) {
                    sb.append(" (going back: allowed even while required fields are missing)");
                }
                sb.append('\n');
            });
        }

        if (!process.fields().isEmpty()) {
            sb.append("\n## Fields\n");
            process.fields().forEach(f -> sb.append("- ")
                    .append(describe(f, fieldOptions.options(f, ctx.collectedData()), ctx.collectedData()))
                    .append('\n'));
        }

        if (!ctx.switchTargets().isEmpty()) {
            sb.append("""

                    ## Other processes
                    The conversation can be handed over to one of the processes below. If the user's latest message
                    clearly shows they want one of them rather than the current process, set switchProcess to its id.
                    That process then takes over and writes the reply, so yours is discarded; keep it short. What has
                    been collected so far is not carried over, so do not switch on a passing mention, while the user
                    is answering your question, or when you are unsure: ask which they want instead.
                    """);
            ctx.switchTargets().forEach(p -> {
                sb.append("- ").append(p.id()).append(": ").append(p.name());
                if (p.description() != null && !p.description().isBlank()) {
                    sb.append(" - ").append(p.description().strip());
                }
                sb.append('\n');
            });
        }

        sb.append("\n## Collected so far\n").append(jsonMapper.writeValueAsString(ctx.collectedData())).append('\n');

        sb.append("""

                ## Rules
                - Work toward the current state's objective. Ask for missing required fields; keep replies short and natural.
                - Put any field values the user gives in their latest message into extractedFields, using the exact field
                  names above. Use JSON numbers for number/integer fields, true/false for boolean, YYYY-MM-DD for dates,
                  and enum values exactly as listed. For a tags field give a JSON array of listed values: every tag
                  that applies after this message, keeping earlier ones the user still wants and dropping any they
                  no longer want, or [] to clear them. Users may correct earlier values in any state.
                - Never invent or guess field values.
                - Set asking to the name of the one field your reply asks the user for, so the app can offer its
                  values as buttons. Name the field even when it belongs to the state you are moving to. Set it to
                  null when your reply asks for more than one field at once, asks something that is not a field, or
                  asks for nothing. The buttons are added by the app, so write your reply as if they were not there.
                - Set objectiveMet to true only when the current state's objective is achieved, counting values you
                  extracted from this message.
                - Set nextState only when objectiveMet is true, every required field is collected, and the state is in the
                  allowed next states list. A state marked "going back" may be set without objectiveMet or the required
                  fields, when the user asks for it. When you set nextState, your reply must already begin working on
                  that state's objective.
                - Otherwise set nextState to null and keep working on the current objective.
                - Set switchProcess to null unless handing over as described under Other processes. When you set
                  it, set nextState to null and leave extractedFields empty.
                - Respond with a single JSON object only.
                """);
        return sb.toString();
    }

    /** Renders a transition's condition as "customerType is COMPANY and loanPurpose is one of HOME, CAR". */
    private static String describe(Map<String, Object> when) {
        return when.entrySet().stream()
                .map(e -> e.getValue() instanceof Collection<?> values
                        ? e.getKey() + " is one of " + values.stream().map(String::valueOf)
                                .collect(Collectors.joining(", "))
                        : e.getKey() + " is " + e.getValue())
                .collect(Collectors.joining(" and "));
    }

    private static String describe(FieldDefinition f, FieldOptions options, Map<String, Object> collectedData) {
        String type = switch (f.type()) {
            case ENUM -> "enum: " + options.options().stream().map(FieldOptions.Option::describe)
                    .collect(Collectors.joining(" | "));
            case TAGS -> "tags, a JSON array of the values listed below";
            default -> f.type().name().toLowerCase();
        };
        String desc = f.description() == null ? "" : ": " + f.description();
        String setBy = f.readOnly() ? " [set by the system; never put it in extractedFields]" : "";
        String line = f.name() + " (" + type + ")" + setBy + desc;
        return f.type() == FieldType.TAGS ? line + describeTags(options, collectedData.get(f.name())) : line;
    }

    /**
     * The values of a tags field one group per line, as "value (label; also: other words) [n]",
     * where n is how many results picking the value would leave, then the group worth asking about next.
     */
    private static String describeTags(FieldOptions options, Object chosen) {
        if (options.isEmpty()) {
            return "\n  (no values available yet)";
        }
        Map<String, List<FieldOptions.Option>> byGroup = new LinkedHashMap<>();
        options.options().forEach(o -> byGroup.computeIfAbsent(o.group() == null ? "Other" : o.group(),
                g -> new ArrayList<>()).add(o));
        StringBuilder sb = new StringBuilder();
        byGroup.forEach((group, values) -> sb.append("\n  ").append(group).append(": ")
                .append(values.stream().map(TurnPromptFactory::describeTag).collect(Collectors.joining(" | "))));
        List<FieldOptions.Option> next = options.nextGroup(chosen instanceof Collection<?> c ? c : List.of());
        sb.append("\n  Next group to ask about: ").append(next.isEmpty() ? "none" : next.getFirst().group());
        next.stream().map(FieldOptions.Option::guidance).filter(g -> g != null && !g.isBlank()).findFirst()
                .ifPresent(g -> sb.append("\n  Guidance for choosing in that group: ").append(g.strip()));
        return sb.toString();
    }

    private static String describeTag(FieldOptions.Option o) {
        StringBuilder sb = new StringBuilder(o.value()).append(" (")
                .append(o.label() == null || o.label().isBlank() ? o.value() : o.label());
        if (o.description() != null && !o.description().isBlank()) {
            sb.append("; also: ").append(o.description().strip());
        }
        sb.append(')');
        if (o.count() != null) {
            sb.append(" [").append(o.count()).append(']');
        }
        return sb.toString();
    }
}
