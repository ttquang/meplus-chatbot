package com.ttq.process;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum FieldType {
    STRING, NUMBER, INTEGER, BOOLEAN, DATE, ENUM,
    /** Several values from an enum-like list, such as the features a customer wants in a product. */
    TAGS;

    @JsonCreator
    public static FieldType of(String value) {
        return valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}
