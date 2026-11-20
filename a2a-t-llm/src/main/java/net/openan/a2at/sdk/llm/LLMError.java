package net.openan.a2at.sdk.llm;

import net.openan.a2at.sdk.core.exception.A2ATError;

/**
 * Base unchecked error for LLM integration failures.
 *
 * <p>LLM failures are part of the A2A-T processing-failure tree: catching {@link A2ATError} also catches this error and
 * its subclasses ({@link LLMConfigError}, {@link LLMRuntimeError}).
 *
 * @since 2026-06
 */
public class LLMError extends A2ATError {

    public LLMError(String message) {
        super(message);
    }

    public LLMError(String message, Throwable cause) {
        super(message, cause);
    }
}
