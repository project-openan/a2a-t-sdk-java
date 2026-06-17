package net.openan.a2at.sdk.llm.config;

import java.util.Map;
import net.openan.a2at.sdk.llm.exception.LlmConfigException;

/**
 * Default LLM client configuration resolved from one `.env` file.
 *
 * @param provider default provider
 * @param model default model
 * @param apiKey default API key
 * @param baseUrl default base URL
 * @param maxTokens default max tokens
 * @param temperature default temperature
 * @param timeoutSeconds default timeout seconds
 * @since 2026-06
 */
public record LlmClientConfig(
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int maxTokens,
        double temperature,
        double timeoutSeconds) {

    private static final int DEFAULT_MAX_TOKENS = 2048;

    private static final double DEFAULT_TEMPERATURE = 0.2d;

    private static final double DEFAULT_TIMEOUT_SECONDS = 30.0d;

    /**
     * Builds one client config from raw `.env` values.
     *
     * @param values raw `.env` values
     * @return resolved client config
     */
    public static LlmClientConfig fromMap(Map<String, String> values) {
        String provider = required(values, "A2AT_LLM_PROVIDER");
        String model = required(values, "A2AT_LLM_MODEL");
        String apiKey = required(values, "A2AT_LLM_API_KEY");
        String baseUrl = optional(values.get("A2AT_LLM_BASE_URL"));

        return new LlmClientConfig(
                provider,
                model,
                apiKey,
                baseUrl,
                parseInt(values.get("A2AT_LLM_MAX_TOKENS"), DEFAULT_MAX_TOKENS, "A2AT_LLM_MAX_TOKENS"),
                parseDouble(values.get("A2AT_LLM_TEMPERATURE"), DEFAULT_TEMPERATURE, "A2AT_LLM_TEMPERATURE"),
                parseDouble(values.get("A2AT_LLM_TIMEOUT_SECONDS"), DEFAULT_TIMEOUT_SECONDS, "A2AT_LLM_TIMEOUT_SECONDS"));
    }

    private static String required(Map<String, String> values, String key) {
        String value = optional(values.get(key));
        if (value == null) {
            throw new LlmConfigException(key + " must be set");
        }
        return value;
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int parseInt(String rawValue, int defaultValue, String key) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            throw new LlmConfigException(key + " must be an integer", exception);
        }
    }

    private static double parseDouble(String rawValue, double defaultValue, String key) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException exception) {
            throw new LlmConfigException(key + " must be a number", exception);
        }
    }
}
