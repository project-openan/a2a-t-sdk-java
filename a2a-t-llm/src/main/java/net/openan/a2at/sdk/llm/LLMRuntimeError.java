package net.openan.a2at.sdk.llm;

/**
 * Raised when an LLM provider invocation or response is invalid.
 *
 * @since 2026-06
 */
public class LLMRuntimeError extends LLMError {

    public LLMRuntimeError(String message) {
        super(message);
    }

    public LLMRuntimeError(String message, Throwable cause) {
        super(message, cause);
    }
}
