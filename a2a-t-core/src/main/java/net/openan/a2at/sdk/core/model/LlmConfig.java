package net.openan.a2at.sdk.core.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

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
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.PROVIDER), DEFAULT_PROVIDER),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.MODEL), ""),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.API_KEY), ""),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.BASE_URL), ""),
                NumberUtils.toInt(values.get(A2ATConfigKeys.Llm.HISTORY_WINDOW), DEFAULT_HISTORY_WINDOW),
                NumberUtils.toInt(values.get(A2ATConfigKeys.Llm.MAX_TOKENS), DEFAULT_MAX_TOKENS),
                NumberUtils.toDouble(values.get(A2ATConfigKeys.Llm.TEMPERATURE), DEFAULT_TEMPERATURE),
                NumberUtils.toDouble(values.get(A2ATConfigKeys.Llm.TIMEOUT_SECONDS), DEFAULT_TIMEOUT_SECONDS),
                NumberUtils.toInt(values.get(A2ATConfigKeys.Llm.SESSION_MAX_TOTAL), DEFAULT_SESSION_MAX_TOTAL),
                NumberUtils.toInt(values.get(A2ATConfigKeys.Llm.SESSION_MAX_PER_PROVIDER), DEFAULT_SESSION_MAX_PER_PROVIDER));
    }
}
