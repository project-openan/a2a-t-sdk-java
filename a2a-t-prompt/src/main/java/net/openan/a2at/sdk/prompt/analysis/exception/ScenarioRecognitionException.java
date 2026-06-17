package net.openan.a2at.sdk.prompt.analysis.exception;

import net.openan.a2at.sdk.core.exception.SdkException;

/**
 * Raised when a structured scenario recognition payload is invalid.
 *
 * @since 2026-06
 */
public final class ScenarioRecognitionException extends SdkException {

    /**
     * Creates a scenario-recognition exception.
     *
     * @param message failure message
     */
    public ScenarioRecognitionException(String message) {
        super(message);
    }
}
