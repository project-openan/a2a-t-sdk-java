package net.openan.a2at.sample.server.flow;

import net.openan.a2at.sdk.server.model.PromptComplianceResult;

/**
 * Sample-owned bridge for checking processed prompt text with the server SDK.
 *
 * @since 2026-05
 */
@FunctionalInterface
public interface PromptComplianceChecker {

    PromptComplianceResult checkTaskPrompt(String processedPromptText);
}


