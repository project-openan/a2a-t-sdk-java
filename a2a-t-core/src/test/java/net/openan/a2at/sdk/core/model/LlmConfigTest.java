package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LlmConfig}.
 *
 * <p>Tests cover the following scenarios:
 *
 * <ul>
 *   <li>Default values when configuration keys are missing
 *   <li>Overriding defaults with environment variable values
 *   <li>Max attempts parsing: in-range values, clamping, fallback, and blank values
 *   <li>Reasoning effort parsing: valid values, case normalization, blank values
 * </ul>
 *
 * @since 2026-06
 */
class LlmConfigTest {

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} applies default values when no configuration keys are provided.
     *
     * <p>Scenario: An empty map is passed to fromMap(). Expected result: All fields use predefined defaults: -
     * provider: "openai" - model, apiKey, baseUrl: empty strings - historyWindow: 10 - maxTokens: null - temperature:
     * null - timeoutSeconds: null - reasoningEffort: null - sessionMaxTotal: 300 - sessionMaxPerProvider: 100 -
     * maxAttempts: 3
     */
    @Test
    void should_useDefaults_When_keysAreMissing() {
        Map<String, String> values = Map.of();

        LlmConfig config = LlmConfig.fromMap(values);

        assertEquals("openai", config.provider());
        assertEquals("", config.model());
        assertEquals("", config.apiKey());
        assertEquals("", config.baseUrl());
        assertEquals(10, config.historyWindow());
        assertNull(config.maxTokens());
        assertNull(config.temperature());
        assertNull(config.timeoutSeconds());
        assertEquals(false, config.disableSystemProxy());
        assertNull(config.reasoningEffort());
        assertEquals(300, config.sessionMaxTotal());
        assertEquals(100, config.sessionMaxPerProvider());
        assertEquals(3, config.maxAttempts());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} overrides default values with values from the provided map.
     *
     * <p>Scenario: A map containing all LLM configuration keys with custom values. Expected result: All fields use the
     * values from the map instead of defaults.
     */
    @Test
    void should_overrideDefaults_When_keysAreProvided() {
        Map<String, String> values = Map.ofEntries(
                Map.entry("A2AT_LLM_PROVIDER", "example_provider"),
                Map.entry("A2AT_LLM_MODEL", "example-model"),
                Map.entry("A2AT_LLM_API_KEY", "test-api-key"),
                Map.entry("A2AT_LLM_BASE_URL", "https://llm.example.test/v1"),
                Map.entry("A2AT_LLM_HISTORY_WINDOW", "20"),
                Map.entry("A2AT_LLM_MAX_TOKENS", "4096"),
                Map.entry("A2AT_LLM_TEMPERATURE", "0.5"),
                Map.entry("A2AT_LLM_TIMEOUT_SECONDS", "60"),
                Map.entry("A2AT_LLM_DISABLE_SYSTEM_PROXY", "true"),
                Map.entry("A2AT_LLM_SESSION_MAX_TOTAL", "500"),
                Map.entry("A2AT_LLM_SESSION_MAX_PER_PROVIDER", "150"),
                Map.entry("A2AT_LLM_MAX_ATTEMPTS", "5"),
                Map.entry("A2AT_LLM_REASONING_EFFORT", "HIGH"));

        LlmConfig config = LlmConfig.fromMap(values);

        assertEquals("example_provider", config.provider());
        assertEquals("example-model", config.model());
        assertEquals("test-api-key", config.apiKey());
        assertEquals("https://llm.example.test/v1", config.baseUrl());
        assertEquals(20, config.historyWindow());
        assertEquals(4096, config.maxTokens());
        assertEquals(0.5d, config.temperature());
        assertEquals(60.0d, config.timeoutSeconds());
        assertEquals(true, config.disableSystemProxy());
        assertEquals(500, config.sessionMaxTotal());
        assertEquals(150, config.sessionMaxPerProvider());
        assertEquals(5, config.maxAttempts());
        assertEquals("HIGH", config.reasoningEffort());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} clamps an out-of-range max attempts value to the nearest allowed
     * bound.
     *
     * <p>Scenario: A2AT_LLM_MAX_ATTEMPTS is set to "0" (below the lower bound of 1) and to "99" (above the upper bound
     * of 10). Expected result: maxAttempts resolves to 1 and to 10 respectively.
     */
    @Test
    void should_clampMaxAttempts_When_valueIsOutOfRange() {
        LlmConfig tooSmall = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "0"));
        LlmConfig tooLarge = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "99"));

        assertEquals(1, tooSmall.maxAttempts());
        assertEquals(10, tooLarge.maxAttempts());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} falls back to the default max attempts value when the configured
     * value is not a valid integer.
     *
     * <p>Scenario: A2AT_LLM_MAX_ATTEMPTS is set to a non-numeric value. Expected result: maxAttempts resolves to the
     * default of 3.
     */
    @Test
    void should_fallBackToDefaultMaxAttempts_When_valueIsUnparseable() {
        LlmConfig config = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "garbage"));

        assertEquals(3, config.maxAttempts());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} uses the default max attempts value when the key is absent or blank.
     *
     * <p>Scenario: A2AT_LLM_MAX_ATTEMPTS is missing from the map, or set to a blank value. Expected result: maxAttempts
     * resolves to the default of 3 in both cases.
     */
    @Test
    void should_useDefaultMaxAttempts_When_valueIsAbsentOrBlank() {
        LlmConfig absent = LlmConfig.fromMap(Map.of());
        LlmConfig blank = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", ""));

        assertEquals(3, absent.maxAttempts());
        assertEquals(3, blank.maxAttempts());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} parses reasoning effort with case normalization.
     *
     * <p>Scenario: A2AT_LLM_REASONING_EFFORT is set to "MEDIUM". Expected result: reasoningEffort resolves to "MEDIUM"
     * (preserved as-is after trimming).
     */
    @Test
    void should_normalizeReasoningEffort_When_configured() {
        LlmConfig config = LlmConfig.fromMap(Map.of("A2AT_LLM_REASONING_EFFORT", "MEDIUM"));

        assertEquals("MEDIUM", config.reasoningEffort());
    }

    /**
     * Verifies that {@link LlmConfig#fromMap(Map)} returns null reasoning effort when the key is absent or blank.
     *
     * <p>Scenario: A2AT_LLM_REASONING_EFFORT is missing or set to blank. Expected result: reasoningEffort is null.
     */
    @Test
    void should_returnNullReasoningEffort_When_valueIsAbsentOrBlank() {
        LlmConfig absent = LlmConfig.fromMap(Map.of());
        LlmConfig blank = LlmConfig.fromMap(Map.of("A2AT_LLM_REASONING_EFFORT", ""));

        assertNull(absent.reasoningEffort());
        assertNull(blank.reasoningEffort());
    }

    @Test
    void shouldRecordParseErrors_When_numericValuesAreNonNumeric() {
        Map<String, String> values = Map.of(
                "A2AT_LLM_MAX_TOKENS", "garbage",
                "A2AT_LLM_TEMPERATURE", "xyz",
                "A2AT_LLM_TIMEOUT_SECONDS", "abc",
                "A2AT_LLM_HISTORY_WINDOW", "not_a_number",
                "A2AT_LLM_SESSION_MAX_TOTAL", "bad",
                "A2AT_LLM_SESSION_MAX_PER_PROVIDER", "nope");

        LlmConfig config = LlmConfig.fromMap(values);

        List<String> errors = config.parseErrors();
        assertEquals(6, errors.size());
        assertTrue(errors.contains("A2AT_LLM_MAX_TOKENS: non-numeric value 'garbage'"));
        assertTrue(errors.contains("A2AT_LLM_TEMPERATURE: non-numeric value 'xyz'"));
        assertTrue(errors.contains("A2AT_LLM_TIMEOUT_SECONDS: non-numeric value 'abc'"));
        assertTrue(errors.contains("A2AT_LLM_HISTORY_WINDOW: non-numeric value 'not_a_number'"));
        assertTrue(errors.contains("A2AT_LLM_SESSION_MAX_TOTAL: non-numeric value 'bad'"));
        assertTrue(errors.contains("A2AT_LLM_SESSION_MAX_PER_PROVIDER: non-numeric value 'nope'"));

        assertNull(config.maxTokens());
        assertNull(config.temperature());
        assertNull(config.timeoutSeconds());
        assertEquals(10, config.historyWindow());
        assertEquals(300, config.sessionMaxTotal());
        assertEquals(100, config.sessionMaxPerProvider());
    }

    @Test
    void shouldNotRecordMaxAttemptsInParseErrors_When_maxAttemptsIsNonNumeric() {
        LlmConfig config = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "garbage"));

        assertTrue(config.parseErrors().isEmpty());
        assertEquals(3, config.maxAttempts());
    }

    @Test
    void shouldReturnEmptyParseErrors_When_noErrors() {
        Map<String, String> values = Map.of(
                "A2AT_LLM_PROVIDER", "openai",
                "A2AT_LLM_MAX_TOKENS", "4096",
                "A2AT_LLM_TEMPERATURE", "0.5",
                "A2AT_LLM_HISTORY_WINDOW", "20",
                "A2AT_LLM_MAX_ATTEMPTS", "5");

        LlmConfig config = LlmConfig.fromMap(values);

        assertTrue(config.parseErrors().isEmpty());
        assertEquals(4096, config.maxTokens());
        assertEquals(0.5d, config.temperature());
        assertEquals(20, config.historyWindow());
        assertEquals(5, config.maxAttempts());
    }

    @Test
    void shouldReturnEmptyParseErrors_When_noValuesProvided() {
        LlmConfig config = LlmConfig.fromMap(Map.of());

        assertTrue(config.parseErrors().isEmpty());
    }

    @Test
    void shouldRecordParseError_When_booleanValueIsInvalid() {
        LlmConfig config = LlmConfig.fromMap(Map.of("A2AT_LLM_DISABLE_SYSTEM_PROXY", "maybe"));

        List<String> errors = config.parseErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.contains("A2AT_LLM_DISABLE_SYSTEM_PROXY: invalid boolean value 'maybe'"));
        assertEquals(false, config.disableSystemProxy());
    }

    @Test
    void shouldParseBooleanValueCaseInsensitive() {
        LlmConfig trueConfig = LlmConfig.fromMap(Map.of("A2AT_LLM_DISABLE_SYSTEM_PROXY", "TRUE"));
        LlmConfig falseConfig = LlmConfig.fromMap(Map.of("A2AT_LLM_DISABLE_SYSTEM_PROXY", "False"));

        assertEquals(true, trueConfig.disableSystemProxy());
        assertEquals(false, falseConfig.disableSystemProxy());
        assertTrue(trueConfig.parseErrors().isEmpty());
        assertTrue(falseConfig.parseErrors().isEmpty());
    }
}
