package net.openan.a2at.sdk.negotiation.generation;

/**
 * Internal failure raised when rendering a negotiation template cannot proceed.
 *
 * <p>This exception never bubbles out of the public API; the orchestration layer maps it to a typed negotiation failure
 * instead.
 *
 * @since 2026-08
 */
class NegotiationRenderException extends RuntimeException {

    /**
     * Creates a render failure.
     *
     * @param message failure message
     */
    public NegotiationRenderException(String message) {
        super(message);
    }

    /**
     * Creates a render failure with a root cause.
     *
     * @param message failure message
     * @param cause root cause
     */
    public NegotiationRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
