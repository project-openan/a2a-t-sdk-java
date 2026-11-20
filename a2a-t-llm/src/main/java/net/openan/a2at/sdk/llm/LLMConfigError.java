package net.openan.a2at.sdk.llm;

/**
 * Raised when LLM configuration or provider registration is invalid.
 *
 * @since 2026-06
 */
public class LLMConfigError extends LLMError {

    public LLMConfigError(String message) {
        super(message);
    }

    public LLMConfigError(String message, Throwable cause) {
        super(message, cause);
    }
}
