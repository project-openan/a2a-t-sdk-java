package net.openan.a2at.sdk.negotiation.types.model;

/**
 * Minimal task prompt compliance result shared with server-side negotiation flows.
 *
 * @param passed prompt compliance result
 * @param failure prompt compliance failure payload
 * @since 2026-06
 */
public record TaskPromptComplianceResult(boolean passed, TaskPromptComplianceFailure failure) {

    /**
     * Creates a successful compliance result.
     *
     * @return successful compliance result
     */
    public static TaskPromptComplianceResult success() {
        return new TaskPromptComplianceResult(true, null);
    }

    /**
     * Creates a failed compliance result.
     *
     * @param failure standardized failure payload
     * @return failed compliance result
     */
    public static TaskPromptComplianceResult failure(TaskPromptComplianceFailure failure) {
        return new TaskPromptComplianceResult(false, failure);
    }
}
