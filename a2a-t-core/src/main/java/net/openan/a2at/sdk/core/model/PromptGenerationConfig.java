package net.openan.a2at.sdk.core.model;

/**
 * Shared prompt-generation configuration bundle for client-side orchestration defaults.
 *
 * @since 2026-06
 */
public record PromptGenerationConfig(
        String language,
        String scenarioSystemPrompt,
        String scenarioUserPrompt,
        String slotSystemPrompt,
        String slotUserPrompt) {}
