package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PromptRuntimeConfig}.
 *
 * <p>Tests cover the following scenarios:
 *
 * <ul>
 *   <li>Default values when configuration keys are missing
 *   <li>Overriding defaults with environment variable values
 * </ul>
 *
 * @since 2026-06
 */
class PromptRuntimeConfigTest {

    /**
     * Verifies that {@link PromptRuntimeConfig#fromMap(Map)} applies default values when no configuration keys are
     * provided.
     *
     * <p>Scenario: An empty map is passed to fromMap(). Expected result: All fields use predefined defaults: -
     * language: "en-US" - sourceType: "classpath" - localRootDir: null (unset)
     */
    @Test
    void should_useDefaults_When_keysAreMissing() {
        Map<String, String> values = Map.of();

        PromptRuntimeConfig config = PromptRuntimeConfig.fromMap(values);

        assertEquals("en-US", config.language());
        assertEquals("classpath", config.sourceType());
        assertNull(config.localRootDir());
    }

    /**
     * Verifies that {@link PromptRuntimeConfig#fromMap(Map)} overrides default values with values from the provided
     * map.
     *
     * <p>Scenario: A map containing language, source type, and local root directory. Expected result: All fields use
     * the values from the map instead of defaults.
     */
    @Test
    void should_overrideDefaults_When_keysAreProvided() {
        Map<String, String> values = Map.of(
                "A2AT_LANGUAGE", "zh-CN",
                "A2AT_PROMPT_SOURCE_TYPE", "local_file",
                "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR", "/path/to/prompts");

        PromptRuntimeConfig config = PromptRuntimeConfig.fromMap(values);

        assertEquals("zh-CN", config.language());
        assertEquals("local_file", config.sourceType());
        assertEquals("/path/to/prompts", config.localRootDir());
    }
}
