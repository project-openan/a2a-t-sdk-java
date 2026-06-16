package net.openan.a2at.sdk.core.model;

import java.util.Map;

/**
 * Prompt runtime configuration resolved from unified SDK config.
 *
 * @since 2026-05
 */
public record PromptRuntimeConfig(String language, String sourceType, String localRootDir) {

    private static final String DEFAULT_LANGUAGE = "en-US";

    private static final String DEFAULT_SOURCE_TYPE = "classpath";

    /**
     * Builds one prompt runtime config from raw `.env` values.
     *
     * @param values raw config values
     * @return resolved prompt runtime config
     */
    public static PromptRuntimeConfig fromMap(Map<String, String> values) {
        return new PromptRuntimeConfig(
                valueOrDefault(values.get("A2AT_LANGUAGE"), DEFAULT_LANGUAGE),
                valueOrDefault(values.get("A2AT_PROMPT_SOURCE_TYPE"), DEFAULT_SOURCE_TYPE),
                valueOrDefault(values.get("A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR"), "."));
    }

    private static String valueOrDefault(String rawValue, String defaultValue) {
        return rawValue == null || rawValue.isBlank() ? defaultValue : rawValue;
    }
}
