package net.openan.a2at.sdk.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Structured LLM runtime configuration resolved from unified SDK config.
 *
 * @since 2026-06
 */
@Slf4j
public record LlmConfig(
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int historyWindow,
        @Nullable Integer maxTokens,
        @Nullable Double temperature,
        @Nullable Double timeoutSeconds,
        int sessionMaxTotal,
        int sessionMaxPerProvider,
        boolean disableSystemProxy,
        boolean sslVerify,
        @Nullable String reasoningEffort,
        int maxAttempts,
        List<String> parseErrors) {

    private static final String DEFAULT_PROVIDER = "openai";

    private static final int DEFAULT_HISTORY_WINDOW = 10;

    private static final int DEFAULT_SESSION_MAX_TOTAL = 300;

    private static final int DEFAULT_SESSION_MAX_PER_PROVIDER = 100;

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private static final int MAX_ATTEMPTS_LOWER_BOUND = 1;

    private static final int MAX_ATTEMPTS_UPPER_BOUND = 10;

    private static final String NON_NUMERIC_MESSAGE_FORMAT = "%s: non-numeric value '%s'";

    private static final String INVALID_BOOLEAN_MESSAGE_FORMAT = "%s: invalid boolean value '%s'";

    public LlmConfig {
        parseErrors = List.copyOf(parseErrors);
    }

    /** Pre-{@code sslVerify} signature kept for binary compatibility; TLS peer verification stays enabled. */
    public LlmConfig(
            String provider,
            String model,
            String apiKey,
            String baseUrl,
            int historyWindow,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable Double timeoutSeconds,
            int sessionMaxTotal,
            int sessionMaxPerProvider,
            boolean disableSystemProxy,
            @Nullable String reasoningEffort,
            int maxAttempts,
            List<String> parseErrors) {
        this(
                provider,
                model,
                apiKey,
                baseUrl,
                historyWindow,
                maxTokens,
                temperature,
                timeoutSeconds,
                sessionMaxTotal,
                sessionMaxPerProvider,
                disableSystemProxy,
                true,
                reasoningEffort,
                maxAttempts,
                parseErrors);
    }

    /**
     * Builds one LLM config from raw {@code .env} values.
     *
     * @param values raw config values
     * @return resolved LLM config
     */
    public static LlmConfig fromMap(Map<String, String> values) {
        List<String> parseErrors = new ArrayList<>();
        return new LlmConfig(
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.PROVIDER), DEFAULT_PROVIDER),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.MODEL), ""),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.API_KEY), ""),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.Llm.BASE_URL), ""),
                parseInt(values.get(A2ATConfigKeys.Llm.HISTORY_WINDOW),
                        A2ATConfigKeys.Llm.HISTORY_WINDOW, DEFAULT_HISTORY_WINDOW, parseErrors),
                parseOptionalNumeric(values.get(A2ATConfigKeys.Llm.MAX_TOKENS),
                        A2ATConfigKeys.Llm.MAX_TOKENS, parseErrors, Integer::parseInt),
                parseOptionalNumeric(values.get(A2ATConfigKeys.Llm.TEMPERATURE),
                        A2ATConfigKeys.Llm.TEMPERATURE, parseErrors, Double::parseDouble),
                parseOptionalNumeric(values.get(A2ATConfigKeys.Llm.TIMEOUT_SECONDS),
                        A2ATConfigKeys.Llm.TIMEOUT_SECONDS, parseErrors, Double::parseDouble),
                parseInt(values.get(A2ATConfigKeys.Llm.SESSION_MAX_TOTAL),
                        A2ATConfigKeys.Llm.SESSION_MAX_TOTAL, DEFAULT_SESSION_MAX_TOTAL, parseErrors),
                parseInt(values.get(A2ATConfigKeys.Llm.SESSION_MAX_PER_PROVIDER),
                        A2ATConfigKeys.Llm.SESSION_MAX_PER_PROVIDER, DEFAULT_SESSION_MAX_PER_PROVIDER, parseErrors),
                parseBoolean(values.get(A2ATConfigKeys.Llm.DISABLE_SYSTEM_PROXY),
                        A2ATConfigKeys.Llm.DISABLE_SYSTEM_PROXY, false, parseErrors),
                parseBoolean(values.get(A2ATConfigKeys.Llm.SSL_VERIFY),
                        A2ATConfigKeys.Llm.SSL_VERIFY, true, parseErrors),
                parseReasoningEffort(values.get(A2ATConfigKeys.Llm.REASONING_EFFORT)),
                parseMaxAttempts(values.get(A2ATConfigKeys.Llm.MAX_ATTEMPTS)),
                parseErrors);
    }

    private static int parseMaxAttempts(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        try {
            int parsed = Integer.parseInt(rawValue.trim());
            if (parsed < MAX_ATTEMPTS_LOWER_BOUND) {
                log.atWarn()
                        .log(
                        "LLM max attempts value is below the allowed minimum, clamped to bound. key={} raw_value={} clamped_value={}",
                        A2ATConfigKeys.Llm.MAX_ATTEMPTS,
                        rawValue.trim(),
                                MAX_ATTEMPTS_LOWER_BOUND);
                return MAX_ATTEMPTS_LOWER_BOUND;
            }
            if (parsed > MAX_ATTEMPTS_UPPER_BOUND) {
                log.atWarn()
                        .log(
"LLM max attempts value is above the allowed maximum, clamped to bound. key={} raw_value={} clamped_value={}",
                        A2ATConfigKeys.Llm.MAX_ATTEMPTS,
                        rawValue.trim(),
                                MAX_ATTEMPTS_UPPER_BOUND);
                return MAX_ATTEMPTS_UPPER_BOUND;
            }
            return parsed;
        } catch (NumberFormatException error) {
            log.atWarn()
                    .log(
"LLM max attempts value is not a valid integer, falling back to default. key={} raw_value={} default_value={}",
                        A2ATConfigKeys.Llm.MAX_ATTEMPTS,
                        rawValue.trim(),
                            DEFAULT_MAX_ATTEMPTS);
            return DEFAULT_MAX_ATTEMPTS;
        }
    }

    private static <T> T parseOptionalNumeric(
            String rawValue, String key, List<String> parseErrors, Function<String, T> parser) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        try {
            return parser.apply(rawValue.trim());
        } catch (NumberFormatException error) {
            parseErrors.add(String.format(NON_NUMERIC_MESSAGE_FORMAT, key, rawValue.trim()));
            return null;
        }
    }

    private static int parseInt(String rawValue, String key, int defaultValue, List<String> parseErrors) {
        if (StringUtils.isBlank(rawValue)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException error) {
            parseErrors.add(String.format(NON_NUMERIC_MESSAGE_FORMAT, key, rawValue.trim()));
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String rawValue, String key, boolean defaultValue, List<String> parseErrors) {
        if (StringUtils.isBlank(rawValue)) {
            return defaultValue;
        }
        String trimmed = rawValue.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        parseErrors.add(String.format(INVALID_BOOLEAN_MESSAGE_FORMAT, key, trimmed));
        return defaultValue;
    }

    private static String parseReasoningEffort(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        return rawValue.trim();
    }
}