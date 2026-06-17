package net.openan.a2at.sdk.server.model;

import java.util.Map;

/**
 * Extracted metadata exposed by successful server prompt-compliance checks.
 *
 * @param scenarioCode scenario code resolved from the processed prompt
 * @param language language resolved from the processed prompt
 * @param templateText canonical template text matched during compliance
 * @param slots extracted slot values
 * @since 2026-06
 */
public record ProcessedPromptMetadata(
        String scenarioCode, String language, String templateText, Map<String, String> slots) {}
