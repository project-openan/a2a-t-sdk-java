package net.openan.a2at.sdk.client.model;

/**
 * Typed result returned by client-side task prompt generation.
 *
 * @param success whether prompt generation succeeded
 * @param promptText rendered task prompt text, if generation succeeded
 * @param failure standardized failure payload, if generation failed
 * @since 2026-06
 */
public record PromptGenerationResult(boolean success, String promptText, PromptGenerationFailure failure) {

    /**
     * Creates a successful prompt-generation result.
     *
     * @param promptText rendered task prompt text
     * @return successful generation result
     */
    public static PromptGenerationResult success(String promptText) {
        return new PromptGenerationResult(true, promptText, null);
    }

    /**
     * Creates a failed prompt-generation result.
     *
     * @param failure standardized failure payload
     * @return failed generation result
     */
    public static PromptGenerationResult failure(PromptGenerationFailure failure) {
        return new PromptGenerationResult(false, null, failure);
    }
}
