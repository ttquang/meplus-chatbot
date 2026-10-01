package com.ttq.process;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The values a field currently accepts, with the text shown to the user.
 *
 * @param options value and label pairs, in the order the source returned them
 * @param dynamic true when the options came from an API rather than the process definition
 */
public record FieldOptions(List<Option> options, boolean dynamic) {

    public FieldOptions {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static FieldOptions fixed(List<String> values) {
        return new FieldOptions(values.stream().map(v -> new Option(v, null)).toList(), false);
    }

    public List<String> values() {
        return options.stream().map(Option::value).toList();
    }

    public boolean isEmpty() {
        return options.isEmpty();
    }

    /**
     * The options of the first group worth asking about next, for a tags field: a group none of the
     * {@code chosen} values belongs to, with at least two options that still lead somewhere. Empty
     * when the options have no groups or every group is answered or has nothing left to choose.
     */
    public List<Option> nextGroup(Collection<?> chosen) {
        Set<String> picked = chosen == null ? Set.of() : chosen.stream()
                .filter(Objects::nonNull)
                .map(v -> v.toString().strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> answered = options.stream()
                .filter(o -> o.group() != null && picked.contains(o.value().toLowerCase(Locale.ROOT)))
                .map(Option::group)
                .collect(Collectors.toSet());
        Map<String, List<Option>> open = new LinkedHashMap<>();
        options.stream()
                .filter(o -> o.group() != null && !answered.contains(o.group()) && o.available())
                .forEach(o -> open.computeIfAbsent(o.group(), g -> new ArrayList<>()).add(o));
        return open.values().stream().filter(group -> group.size() >= 2).findFirst().orElse(List.of());
    }

    /**
     * @param label       human-readable text for the value, null when the source gave none
     * @param group       the question the value answers, for a tags field whose values come in
     *                    groups of alternatives; null when the source gave none
     * @param count       how many results picking the value would leave, null when the source gave none
     * @param description other words for the value, to help the model recognise it; null when none
     * @param guidance    how to choose between the values of the value's group, for the question about
     *                    that group; null when none
     */
    public record Option(String value, String label, String group, Long count, String description,
                         String guidance) {

        public Option(String value, String label) {
            this(value, label, null, null, null, null);
        }

        public Option(String value, String label, String group, Long count, String description) {
            this(value, label, group, count, description, null);
        }

        /** False when picking this value is known to leave nothing. */
        public boolean available() {
            return count == null || count > 0;
        }

        public String describe() {
            return label == null || label.isBlank() || label.equals(value) ? value : value + " (" + label + ")";
        }
    }
}
