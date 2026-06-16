package net.openan.a2at.sdk.core.exception;

/**
 * Base exception type for SDK domain and integration failures.
 *
 * @since 2026-06
 */
public class SdkException extends RuntimeException {

    /**
     * Creates an SDK exception with one message.
     *
     * @param message failure message
     */
    public SdkException(String message) {
        super(message);
    }

    /**
     * Creates an SDK exception with one message and root cause.
     *
     * @param message failure message
     * @param cause root cause
     */
    public SdkException(String message, Throwable cause) {
        super(message, cause);
    }
}
