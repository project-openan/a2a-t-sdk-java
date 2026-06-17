package net.openan.a2at.sdk.server.exception;

/**
 * Internal exception used to carry standardized compliance error details.
 *
 * @since 2026-06
 */
public final class PromptComplianceCheckException extends RuntimeException {

    private final String code;

    private final String stage;

    /**
     * Creates a standardized compliance-check exception.
     *
     * @param code stable error code
     * @param message human-readable failure message
     * @param stage compliance stage where the failure occurred
     */
    public PromptComplianceCheckException(String code, String message, String stage) {
        super(message);
        this.code = code;
        this.stage = stage;
    }

    /**
     * Returns the stable compliance error code.
     *
     * @return stable error code
     */
    public String code() {
        return code;
    }

    /**
     * Returns the compliance stage where the failure occurred.
     *
     * @return failure stage
     */
    public String stage() {
        return stage;
    }
}
