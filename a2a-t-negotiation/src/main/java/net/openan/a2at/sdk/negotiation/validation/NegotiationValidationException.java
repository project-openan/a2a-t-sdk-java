package net.openan.a2at.sdk.negotiation.validation;

/**
 * Internal failure of the negotiation semantic validation step.
 *
 * <p>This exception is internal to the validation pipeline: it signals an LLM infrastructure failure or a response that
 * violates the output contract (missing required keys or wrong shapes). It must never bubble out of the public APIs;
 * the orchestration layer converts it into a parameter-extraction failure carrying the retryable LLM infrastructure
 * error code.
 *
 * @since 2026-08
 */
public class NegotiationValidationException extends RuntimeException {

    /**
     * Creates an internal semantic validation failure.
     *
     * @param message failure message
     */
    public NegotiationValidationException(String message) {
        super(message);
    }

    /**
     * Creates an internal semantic validation failure with a cause.
     *
     * @param message failure message
     * @param cause underlying failure
     */
    public NegotiationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
