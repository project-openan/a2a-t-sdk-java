package net.openan.a2at.sdk.negotiation.types.checker;

import net.openan.a2at.sdk.negotiation.types.model.TaskPromptComplianceResult;

/**
 * Checks whether a server-received task prompt is acceptable for information negotiation.
 *
 * @since 2026-06
 */
public interface TaskPromptComplianceChecker {

    /**
     * Checks one processed task prompt for negotiation-time acceptance.
     *
     * @param processedPromptText processed task prompt text
     * @return compliance result
     */
    TaskPromptComplianceResult check(String processedPromptText);
}
