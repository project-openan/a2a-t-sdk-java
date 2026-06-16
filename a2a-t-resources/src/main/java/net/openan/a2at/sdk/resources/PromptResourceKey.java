package net.openan.a2at.sdk.resources;

import java.io.File;

/**
 * Identifies one resource bundled with the SDK prompt resource tree.
 *
 * @param category resource category such as prompts or templates
 * @param subject scenario code or prompt action
 * @param language locale identifier
 * @param fileName target file name
 * @since 2026-06
 */
public record PromptResourceKey(String category, String subject, String language, String fileName) {

    public PromptResourceKey {
        validateSegment("category", category);
        validateSegment("subject", subject);
        validateSegment("language", language);
        validateSegment("fileName", fileName);
    }

    /**
     * Creates a prompt resource key for one prompt action bundle.
     *
     * @param action prompt action name
     * @param language locale identifier
     * @param fileName target file name
     * @return prompt resource key
     */
    public static PromptResourceKey prompt(String action, String language, String fileName) {
        return new PromptResourceKey("prompts", action, language, fileName);
    }

    /**
     * Creates a template resource key for one scenario bundle.
     *
     * @param scenarioCode scenario code
     * @param language locale identifier
     * @param fileName target file name
     * @return template resource key
     */
    public static PromptResourceKey template(String scenarioCode, String language, String fileName) {
        return new PromptResourceKey("templates", scenarioCode, language, fileName);
    }

    /**
     * Resolves the relative classpath path for the current resource key.
     *
     * @return relative path under {@code prompt_resources/}
     */
    public String relativePath() {
        if ("scenarios".equals(category)) {
            return String.join(File.separator, "prompt_resources", category, language, fileName);
        }
        return String.join(File.separator, "prompt_resources", category, subject, language, fileName);
    }

    private static void validateSegment(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.contains("..") || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException(fieldName + " must be a simple path segment");
        }
    }
}
