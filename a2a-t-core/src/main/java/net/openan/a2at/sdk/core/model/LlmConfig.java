package net.openan.a2at.sdk.core.model;

import java.util.Map;

/**
 * Structured LLM runtime configuration resolved from unified SDK config.
 *
 * @since 2026-06
 */
public record LlmConfig(
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int historyWindow,
        int maxTokens,
        double temperature,
        double timeoutSeconds,
        int sessionMaxTotal,
        int sessionMaxPerProvider) {

    private static final String DEFAULT_PROVIDER = "openai_compatible";

    private static final int DEFAULT_HISTORY_WINDOW = 12;

    private static final int DEFAULT_MAX_TOKENS = 2048;

    private static final double DEFAULT_TEMPERATURE = 0.2d;

    private static final double DEFAULT_TIMEOUT_SECONDS = 30.0d;

    private static final int DEFAULT_SESSION_MAX_TOTAL = 300;

    private static final int DEFAULT_SESSION_MAX_PER_PROVIDER = 100;

    /**
     * Builds one LLM config from raw `.env` values.
     *
     * @param values raw config values
     * @return resolved LLM config
     */
    public static LlmConfig fromMap(Map<String, String> values) {
        return new LlmConfig(
                valueOrDefault(values.get(A2ATConfigKeys.Llm.PROVIDER), DEFAULT_PROVIDER),
                valueOrDefault(values.get(A2ATConfigKeys.Llm.MODEL), ""),
                valueOrDefault(values.get(A2ATConfigKeys.Llm.API_KEY), ""),
                valueOrDefault(values.get(A2ATConfigKeys.Llm.BASE_URL), ""),
                parseInt(values.get(A2ATConfigKeys.Llm.HISTORY_WINDOW), DEFAULT_HISTORY_WINDOW),
                parseInt(values.get(A2ATConfigKeys.Llm.MAX_TOKENS), DEFAULT_MAX_TOKENS),
                parseDouble(values.get(A2ATConfigKeys.Llm.TEMPERATURE), DEFAULT_TEMPERATURE),
                parseDouble(values.get(A2ATConfigKeys.Llm.TIMEOUT_SECONDS), DEFAULT_TIMEOUT_SECONDS),
                parseInt(values.get(A2ATConfigKeys.Llm.SESSION_MAX_TOTAL), DEFAULT_SESSION_MAX_TOTAL),
                parseInt(values.get(A2ATConfigKeys.Llm.SESSION_MAX_PER_PROVIDER), DEFAULT_SESSION_MAX_PER_PROVIDER));
    }

    private static String valueOrDefault(String rawValue, String defaultValue) {
        return rawValue == null || rawValue.isBlank() ? defaultValue : rawValue;
    }

    private static int parseInt(String rawValue, int defaultValue) {
        return rawValue == null || rawValue.isBlank() ? defaultValue : Integer.parseInt(rawValue);
    }

    private static double parseDouble(String rawValue, double defaultValue) {
        return rawValue == null || rawValue.isBlank() ? defaultValue : Double.parseDouble(rawValue);
    }
}
