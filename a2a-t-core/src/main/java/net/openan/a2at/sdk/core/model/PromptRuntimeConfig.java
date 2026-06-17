package net.openan.a2at.sdk.core.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Prompt runtime configuration resolved from unified SDK config.
 *
 * @since 2026-06
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
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.PromptRuntime.LANGUAGE), DEFAULT_LANGUAGE),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.PromptRuntime.SOURCE_TYPE), DEFAULT_SOURCE_TYPE),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.PromptRuntime.LOCAL_ROOT_DIR), "."));
    }
}
