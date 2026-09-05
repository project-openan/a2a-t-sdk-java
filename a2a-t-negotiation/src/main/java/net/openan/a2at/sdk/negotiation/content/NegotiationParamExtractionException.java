package net.openan.a2at.sdk.negotiation.content;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATBusinessException;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import org.jspecify.annotations.Nullable;

/**
 * Raised when validating a negotiation message and extracting parameters from it fails.
 *
 * @since 2026-08
 */
public class NegotiationParamExtractionException extends A2ATBusinessException {

    private final List<SlotValidationError> errors;

    /**
     * Creates a negotiation parameter-extraction failure with one specific error code and slot details.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     */
    public NegotiationParamExtractionException(String code, String message, List<SlotValidationError> errors) {
        super(code, message);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Creates a negotiation parameter-extraction failure with one specific error code, slot details and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     * @param cause root cause of the failure
     */
    public NegotiationParamExtractionException(
            String code, String message, List<SlotValidationError> errors, @Nullable Throwable cause) {
        super(code, message);
        if (cause != null) {
            initCause(cause);
        }
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Creates a negotiation parameter-extraction failure carrying one catalog code, an already rendered message, its
     * fact values, slot details and a root cause.
     *
     * <p>Unlike the {@code ErrorCatalog}-rendering constructors, this constructor keeps the message exactly as passed
     * in, so a message rendered upstream (for example by the shared validation pipeline) survives the wrap unchanged
     * while the facts stay available to callers.
     *
     * @param code machine-readable error code for the failure
     * @param message already rendered failure message
     * @param facts fact values keyed by fact parameter name, may be null
     * @param errors structured per-slot validation error details
     * @param cause root cause of the failure
     */
    public NegotiationParamExtractionException(
            String code,
            String message,
            @Nullable Map<String, String> facts,
            List<SlotValidationError> errors,
            @Nullable Throwable cause) {
        super(code, message, facts, cause);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Creates a negotiation parameter-extraction failure whose message is rendered from the code's template in one
     * language.
     *
     * @param entry catalog entry identifying the failure
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     * @param facts fact values keyed by fact parameter name, may be null
     * @param errors structured per-slot validation error details
     */
    public NegotiationParamExtractionException(
            ErrorCatalog entry,
            @Nullable String language,
            @Nullable Map<String, String> facts,
            List<SlotValidationError> errors) {
        this(entry, language, facts, errors, null);
    }

    /**
     * Creates a negotiation parameter-extraction failure with a rendered message, slot details and a root cause.
     *
     * @param entry catalog entry identifying the failure
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     * @param facts fact values keyed by fact parameter name, may be null
     * @param errors structured per-slot validation error details
     * @param cause root cause of the failure
     */
    public NegotiationParamExtractionException(
            ErrorCatalog entry,
            @Nullable String language,
            @Nullable Map<String, String> facts,
            List<SlotValidationError> errors,
            @Nullable Throwable cause) {
        super(entry.getCode(), ErrorMessages.render(entry, language, facts), facts);
        if (cause != null) {
            initCause(cause);
        }
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Returns the structured per-slot validation error details.
     *
     * @return immutable list of slot validation errors, never null
     */
    public List<SlotValidationError> getErrors() {
        return errors;
    }
}
