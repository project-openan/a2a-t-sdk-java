package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LlmConfig}.
 *
 * <p>Tests cover the following scenarios:
 * <ul>
 *   <li>Default values when configuration keys are missing</li>
 *   <li>Overriding defaults with environment variable values</li>
 * </ul>
 *
 * @since 2026-06
 */
class LlmConfigTest {

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} applies default values when
     * no configuration keys are provided.
     *
     * <p>Scenario: An empty map is passed to fromMap().
     * Expected result: All fields use predefined defaults:
     * - provider: "openai"
     * - model, apiKey, baseUrl: empty strings
     * - historyWindow: 12
     * - maxTokens: 2048
     * - temperature: 0.2
     * - timeoutSeconds: 30.0
     * - sessionMaxTotal: 300
     * - sessionMaxPerProvider: 100
     */
    @Test
    void should_useDefaults_When_keysAreMissing() {
        Map<String, String> values = Map.of();

        LlmConfig config = LlmConfig.fromMap(values);

        assertEquals("openai", config.provider());
        assertEquals("", config.model());
        assertEquals("", config.apiKey());
        assertEquals("", config.baseUrl());
        assertEquals(12, config.historyWindow());
        assertEquals(2048, config.maxTokens());
        assertEquals(0.2d, config.temperature());
        assertEquals(30.0d, config.timeoutSeconds());
        assertEquals(300, config.sessionMaxTotal());
        assertEquals(100, config.sessionMaxPerProvider());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} overrides default values with
     * values from the provided map.
     *
     * <p>Scenario: A map containing all LLM configuration keys with custom values.
     * Expected result: All fields use the values from the map instead of defaults.
     */
    @Test
    void should_overrideDefaults_When_keysAreProvided() {
        Map<String, String> values = Map.of(
                "A2AT_LLM_PROVIDER", "example_provider",
                "A2AT_LLM_MODEL", "example-model",
                "A2AT_LLM_API_KEY", "test-api-key",
                "A2AT_LLM_BASE_URL", "https://llm.example.test/v1",
                "A2AT_LLM_HISTORY_WINDOW", "20",
                "A2AT_LLM_MAX_TOKENS", "4096",
                "A2AT_LLM_TEMPERATURE", "0.5",
                "A2AT_LLM_TIMEOUT_SECONDS", "60",
                "A2AT_LLM_SESSION_MAX_TOTAL", "500",
                "A2AT_LLM_SESSION_MAX_PER_PROVIDER", "150");

        LlmConfig config = LlmConfig.fromMap(values);

        assertEquals("example_provider", config.provider());
        assertEquals("example-model", config.model());
        assertEquals("test-api-key", config.apiKey());
        assertEquals("https://llm.example.test/v1", config.baseUrl());
        assertEquals(20, config.historyWindow());
        assertEquals(4096, config.maxTokens());
        assertEquals(0.5d, config.temperature());
        assertEquals(60.0d, config.timeoutSeconds());
        assertEquals(500, config.sessionMaxTotal());
        assertEquals(150, config.sessionMaxPerProvider());
    }
}
