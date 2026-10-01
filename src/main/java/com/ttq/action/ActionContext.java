package com.ttq.action;

import com.ttq.process.ProcessDefinition;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What an action is given to work with.
 *
 * @param collectedData everything collected so far, including this turn's accepted fields
 */
public record ActionContext(UUID conversationId, ProcessDefinition process, Map<String, Object> collectedData) {

    public Optional<String> text(String field) {
        return Optional.ofNullable(collectedData.get(field)).map(Object::toString).map(String::strip)
                .filter(s -> !s.isEmpty());
    }

    public String requiredText(String field) {
        return text(field).orElseThrow(() -> new ActionFailedException(
                "Field '%s' is required but was not collected".formatted(field), null));
    }

    public BigDecimal requiredNumber(String field) {
        String raw = requiredText(field);
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new ActionFailedException("Field '%s' is not a number: %s".formatted(field, raw), e);
        }
    }
}
