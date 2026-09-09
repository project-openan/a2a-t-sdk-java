package net.openan.a2at.sdk.prompt.analysis.model;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One slot validation error emitted by the structured extraction step.
 *
 * <p>The {@code message} is rendered by the SDK from the code's message template; {@code facts} carries the structured
 * fact values that produced the message.
 *
 * @param slotName slot name
 * @param code validation error code
 * @param message validation message
 * @param facts fact values keyed by fact parameter name, may be null
 * @since 2026-06
 */
public record StructuredSlotValidationError(
        String slotName, String code, String message, @Nullable Map<String, String> facts) {

    public StructuredSlotValidationError {
        facts = facts == null ? null : Map.copyOf(facts);
    }

    /**
     * Creates a slot validation error without structured facts.
     *
     * @param slotName slot name
     * @param code validation error code
     * @param message validation message
     */
    public StructuredSlotValidationError(String slotName, String code, String message) {
        this(slotName, code, message, null);
    }
}
