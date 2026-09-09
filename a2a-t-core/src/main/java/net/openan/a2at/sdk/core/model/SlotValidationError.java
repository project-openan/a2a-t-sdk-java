package net.openan.a2at.sdk.core.model;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Structured validation error details for one named slot.
 *
 * <p>Instances are carried inside parameter-extraction failures so callers can inspect which slot failed, under which
 * error code, and why, without parsing exception messages. The {@code message} is rendered by the SDK from the code's
 * message template; {@code facts} carries the structured fact values that produced the message.
 *
 * @param slotName name of the slot the error refers to
 * @param code machine-readable error code for the error
 * @param message human-readable explanation of the error
 * @param facts fact values keyed by fact parameter name, may be null
 * @since 2026-08
 */
public record SlotValidationError(String slotName, String code, String message, @Nullable Map<String, String> facts) {

    public SlotValidationError {
        facts = facts == null ? null : Map.copyOf(facts);
    }

    /**
     * Creates a slot validation error without structured facts.
     *
     * @param slotName name of the slot the error refers to
     * @param code machine-readable error code for the error
     * @param message human-readable explanation of the error
     */
    public SlotValidationError(String slotName, String code, String message) {
        this(slotName, code, message, null);
    }
}
