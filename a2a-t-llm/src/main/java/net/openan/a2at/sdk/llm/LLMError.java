package net.openan.a2at.sdk.llm;

/**
 * Base unchecked error for LLM integration failures.
 *
 * @since 2026-06
 */
public class LLMError extends RuntimeException {

    public LLMError(String message) {
        super(message);
    }

    public LLMError(String message, Throwable cause) {
        super(message, cause);
    }
}
