package com.ttq.process;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A piece of data a process collects from the user.
 *
 * @param values     allowed values when {@code type} is {@link FieldType#ENUM} or {@link FieldType#TAGS}
 * @param valuesFrom id of a catalog API that returns the allowed values, for lists that change
 *                   without a redeploy; replaces {@code values} when set
 * @param readOnly   set only by actions, such as a status an API returned; a value the model
 *                   proposes is ignored, so the user cannot talk their way into a branch that
 *                   depends on it
 */
public record FieldDefinition(String name, FieldType type, String description, List<String> values,
                              String valuesFrom, boolean readOnly) {

    public FieldDefinition {
        type = type == null ? FieldType.STRING : type;
        values = values == null ? List.of() : List.copyOf(values);
    }

    /** A field whose allowed values, if any, are fixed in the process definition. */
    public FieldDefinition(String name, FieldType type, String description, List<String> values) {
        this(name, type, description, values, null);
    }

    /** A field the model may fill in. */
    public FieldDefinition(String name, FieldType type, String description, List<String> values,
                           String valuesFrom) {
        this(name, type, description, values, valuesFrom, false);
    }

    /**
     * Coerces a value extracted by the model into this field's type.
     * Returns empty when the value is missing or invalid, so the field stays uncollected.
     */
    public Optional<Object> normalize(Object raw) {
        return normalize(raw, values);
    }

    /**
     * Coerces a value, checking enums against {@code allowedValues} rather than the declared list,
     * so a field whose options come from an API can be validated against what that API returned.
     */
    public Optional<Object> normalize(Object raw, List<String> allowedValues) {
        if (raw == null) {
            return Optional.empty();
        }
        if (type == FieldType.TAGS) {
            return normalizeTags(raw, allowedValues);
        }
        String text = raw.toString().strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(switch (type) {
                case STRING -> text;
                case NUMBER -> raw instanceof Number n ? n : new BigDecimal(text.replace(",", ""));
                case INTEGER -> raw instanceof Number n
                        ? new BigDecimal(n.toString()).longValueExact()
                        : new BigDecimal(text.replace(",", "")).longValueExact();
                case BOOLEAN -> parseBoolean(text);
                case DATE -> LocalDate.parse(text).toString();
                case ENUM -> allowedValues.stream()
                        .filter(v -> v.equalsIgnoreCase(text))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("not one of " + allowedValues));
                case TAGS -> throw new IllegalStateException("tags are normalized separately");
            });
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * A list of tags, from a JSON array or a comma separated string, keeping those that are allowed
     * in the spelling the list gives. An empty list clears the field; a list of which none are
     * allowed is invalid, so a mistake does not wipe out the tags already collected.
     */
    private static Optional<Object> normalizeTags(Object raw, List<String> allowedValues) {
        List<String> given = raw instanceof Collection<?> items
                ? items.stream().filter(Objects::nonNull).map(Object::toString).toList()
                : Arrays.asList(raw.toString().split(","));
        List<String> tags = given.stream()
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .toList();
        if (tags.isEmpty()) {
            return Optional.of(List.of());
        }
        List<String> accepted = tags.stream()
                .flatMap(t -> allowedValues.stream().filter(v -> v.equalsIgnoreCase(t)).limit(1))
                .distinct()
                .toList();
        return accepted.isEmpty() ? Optional.empty() : Optional.of(accepted);
    }

    /** True for an enum or tags field whose allowed values come from an API rather than the process definition. */
    public boolean hasDynamicValues() {
        return (type == FieldType.ENUM || type == FieldType.TAGS) && valuesFrom != null && !valuesFrom.isBlank();
    }

    /** True for a field whose values are picked from a list, fixed or looked up. */
    public boolean hasListedValues() {
        return type == FieldType.ENUM || type == FieldType.TAGS;
    }

    /**
     * True when {@code value} may appear in a {@code when} condition on this field. The values of a
     * dynamic enum are only known from its API, so any non-blank value is accepted for it; a value
     * the API never returns simply never matches.
     */
    public boolean isValidConditionValue(Object value) {
        if (hasDynamicValues()) {
            return value != null && !value.toString().isBlank();
        }
        return normalize(value).isPresent();
    }

    /**
     * True when both values are valid for this field and mean the same thing, so that
     * {@code when: { plan: pro }} matches a stored {@code "PRO"} and {@code 24} matches {@code 24.0}.
     */
    public boolean valuesMatch(Object expected, Object actual) {
        if (type == FieldType.TAGS) {
            // A condition on a tags field holds when the value is among the collected tags.
            return actual instanceof Collection<?> tags && expected != null
                    && tags.stream().anyMatch(t -> t != null
                    && t.toString().strip().equalsIgnoreCase(expected.toString().strip()));
        }
        if (hasDynamicValues()) {
            // The collected value was checked against the API's list when it was accepted.
            return isValidConditionValue(expected) && actual != null
                    && expected.toString().strip().equalsIgnoreCase(actual.toString().strip());
        }
        Optional<Object> left = normalize(expected);
        Optional<Object> right = normalize(actual);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.get() instanceof Number a && right.get() instanceof Number b) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        return left.get().equals(right.get());
    }

    private static Boolean parseBoolean(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y" -> true;
            case "false", "no", "n" -> false;
            default -> throw new IllegalArgumentException("not a boolean: " + text);
        };
    }
}
