package net.openan.a2at.sdk.llm.exception;

import net.openan.a2at.sdk.core.exception.SdkException;

/**
 * Thrown when LLM client configuration is missing or invalid.
 *
 * @since 2026-06
 */
public final class LlmConfigException extends SdkException {

    /**
     * Creates one configuration exception with one message.
     *
     * @param message error message
     */
    public LlmConfigException(String message) {
        super(message);
    }

    /**
     * Creates one configuration exception with one message and cause.
     *
     * @param message error message
     * @param cause original cause
     */
    public LlmConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
