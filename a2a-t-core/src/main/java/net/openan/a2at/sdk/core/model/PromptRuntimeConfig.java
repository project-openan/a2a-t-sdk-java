package net.openan.a2at.sdk.core.model;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Prompt runtime configuration resolved from unified SDK config.
 *
 * @since 2026-06
 */
public record PromptRuntimeConfig(String language, String sourceType, String localRootDir) {

    private static final String DEFAULT_LANGUAGE = "en-US";

    /** Source-type selector value for loading business content from the built-in classpath. */
    public static final String SOURCE_TYPE_CLASSPATH = "classpath";

    /** Source-type selector value for loading business content from the configured local root. */
    public static final String SOURCE_TYPE_LOCAL_FILE = "local_file";

    private static final String DEFAULT_SOURCE_TYPE = SOURCE_TYPE_CLASSPATH;

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
                values.get(A2ATConfigKeys.PromptRuntime.LOCAL_ROOT_DIR));
    }
}
