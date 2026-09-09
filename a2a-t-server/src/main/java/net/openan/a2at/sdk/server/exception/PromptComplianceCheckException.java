package net.openan.a2at.sdk.server.exception;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATBusinessException;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import org.jspecify.annotations.Nullable;

/**
 * Compliance-check failure carrying a stable error code and the compliance stage where the failure occurred.
 *
 * <p>Part of the {@link A2ATBusinessException} tree: callers can catch the {@link A2ATError} root to handle any A2A-T
 * processing failure and dispatch on its machine-readable error code. Messages are rendered from the code's message
 * template via {@link ErrorMessages}; the fact values that produced the message travel in {@link #getFacts()}.
 *
 * @since 2026-06
 */
public final class PromptComplianceCheckException extends A2ATBusinessException {

    private final String stage;

    /**
     * Creates a standardized compliance-check exception.
     *
     * @param code stable error code
     * @param message human-readable failure message
     * @param stage compliance stage where the failure occurred
     */
    public PromptComplianceCheckException(String code, String message, String stage) {
        super(code, message);
        this.stage = stage;
    }

    /**
     * Creates a standardized compliance-check exception whose message is rendered from the code's template.
     *
     * @param entry catalog entry identifying the failure
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     * @param facts fact values keyed by fact parameter name, may be null
     * @param stage compliance stage where the failure occurred
     */
    public PromptComplianceCheckException(
            ErrorCatalog entry, @Nullable String language, @Nullable Map<String, String> facts, String stage) {
        super(entry, ErrorMessages.render(entry, language, facts), facts);
        this.stage = stage;
    }

    /**
     * Returns the compliance stage where the failure occurred.
     *
     * @return failure stage
     */
    public String getStage() {
        return stage;
    }
}
