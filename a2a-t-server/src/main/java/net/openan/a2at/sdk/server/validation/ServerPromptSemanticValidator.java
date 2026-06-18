package net.openan.a2at.sdk.server.validation;

import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;

/**
 * Validates that a processed task prompt is semantically consistent with extracted metadata.
 *
 * @since 2026-06
 */
public interface ServerPromptSemanticValidator {

    /**
     * Validates one processed task prompt against extracted metadata.
     *
     * @param processedPromptText processed task prompt text
     * @param metadata extracted metadata
     */
    void validate(String processedPromptText, ProcessedPromptMetadata metadata);
}
