package net.openan.a2at.sdk.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.openan.a2at.sdk.core.model.LlmConfig;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolved default configuration for an LLM provider client.
 *
 * @param provider provider name
 * @param model model name
 * @param apiKey provider API key
 * @param baseUrl optional provider base URL
 * @param historyWindow reserved history window size
 * @param maxTokens optional default max tokens
 * @param temperature optional default temperature
 * @param timeoutSeconds optional provider timeout in seconds
 * @param sessionMaxTotal reserved total session limit
 * @param sessionMaxPerProvider reserved per-provider session limit
 * @param disableSystemProxy whether to bypass the JVM or operating-system HTTP proxy
 * @param sslVerify whether to verify the TLS certificate chain and hostname of the LLM endpoint
 * @param detailLogEnabled whether to print the full LLM request and response payloads (no truncation); summary logs
 *     (timestamp, token usage, elapsed time) are recorded at DEBUG level independently of this flag
 * @param reasoningEffort optional reasoning effort for reasoning models (none/minimal/low/medium/high/xhigh); null
 *     leaves the parameter unset
 * @since 2026-06
 */
public record LLMClientConfig(
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int historyWindow,
        Integer maxTokens,
        Double temperature,
        Double timeoutSeconds,
        int sessionMaxTotal,
        int sessionMaxPerProvider,
        boolean disableSystemProxy,
        boolean sslVerify,
        boolean detailLogEnabled,
        String reasoningEffort) {

    private static final int MAX_HISTORY_WINDOW = 100;

    private static final int MAX_SESSION_TOTAL = 3000;

    private static final int MAX_SESSION_PER_PROVIDER = 1000;

    private static final Set<String> VALID_REASONING_EFFORTS =
            Set.of("none", "minimal", "low", "medium", "high", "xhigh");

    /** Pre-{@code sslVerify} signature kept for binary compatibility; TLS peer verification stays enabled. */
    public LLMClientConfig(
            String provider,
            String model,
            String apiKey,
            String baseUrl,
            int historyWindow,
            Integer maxTokens,
            Double temperature,
            Double timeoutSeconds,
            int sessionMaxTotal,
            int sessionMaxPerProvider,
            boolean disableSystemProxy,
            String reasoningEffort) {
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
                false,
                reasoningEffort);
    }

    /**
     * Derives an LLM client configuration from the unified LLM configuration.
     *
     * <p>Validates required keys, numeric bounds, and reasoning effort values. Missing required keys, out-of-range
     * values, and invalid reasoning effort values all throw {@link LLMConfigError}.
     *
     * @param llmConfig unified LLM configuration
     * @return derived LLM client configuration
     * @throws LLMConfigError if the configuration is invalid
     */
    public static LLMClientConfig from(LlmConfig llmConfig) {
        Objects.requireNonNull(llmConfig, "llmConfig must not be null");
        validateParseErrors(llmConfig);
        validateRequiredKeys(llmConfig);
        validateBounds(llmConfig);
        validateSessionOrder(llmConfig);
        String reasoningEffort = validateReasoningEffort(llmConfig.reasoningEffort());
        return new LLMClientConfig(
                llmConfig.provider(),
                llmConfig.model(),
                llmConfig.apiKey(),
                llmConfig.baseUrl(),
                llmConfig.historyWindow(),
                llmConfig.maxTokens(),
                llmConfig.temperature(),
                llmConfig.timeoutSeconds(),
                llmConfig.sessionMaxTotal(),
                llmConfig.sessionMaxPerProvider(),
                llmConfig.disableSystemProxy(),
                llmConfig.sslVerify(),
                llmConfig.detailLogEnabled(),
                reasoningEffort);
    }

    private static void validateParseErrors(LlmConfig llmConfig) {
        List<String> errors = llmConfig.parseErrors();
        if (!errors.isEmpty()) {
            throw new LLMConfigError("LLM configuration parse errors: " + String.join("; ", errors));
        }
    }

    private static void validateRequiredKeys(LlmConfig llmConfig) {
        List<String> missing = new ArrayList<>();
        if (StringUtils.isBlank(llmConfig.provider())) {
            missing.add("provider");
        }
        if (StringUtils.isBlank(llmConfig.model())) {
            missing.add("model");
        }
        if (StringUtils.isBlank(llmConfig.apiKey())) {
            missing.add("apiKey");
        }
        if (!missing.isEmpty()) {
            throw new LLMConfigError("Missing required LLM configuration keys: " + String.join(", ", missing));
        }
    }

    private static void validateBounds(LlmConfig llmConfig) {
        if (llmConfig.historyWindow() <= 0 || llmConfig.historyWindow() > MAX_HISTORY_WINDOW) {
            throw new LLMConfigError("LLM configuration key 'historyWindow' value " + llmConfig.historyWindow()
                    + " must be between 1 and " + MAX_HISTORY_WINDOW);
        }
        if (llmConfig.sessionMaxTotal() <= 0 || llmConfig.sessionMaxTotal() > MAX_SESSION_TOTAL) {
            throw new LLMConfigError("LLM configuration key 'sessionMaxTotal' value " + llmConfig.sessionMaxTotal()
                    + " must be between 1 and " + MAX_SESSION_TOTAL);
        }
        if (llmConfig.sessionMaxPerProvider() <= 0 || llmConfig.sessionMaxPerProvider() > MAX_SESSION_PER_PROVIDER) {
            throw new LLMConfigError("LLM configuration key 'sessionMaxPerProvider' value "
                    + llmConfig.sessionMaxPerProvider() + " must be between 1 and " + MAX_SESSION_PER_PROVIDER);
        }
    }

    private static void validateSessionOrder(LlmConfig llmConfig) {
        if (llmConfig.sessionMaxTotal() < llmConfig.sessionMaxPerProvider()) {
            throw new LLMConfigError("LLM configuration key 'sessionMaxTotal' (" + llmConfig.sessionMaxTotal()
                    + ") must be >= 'sessionMaxPerProvider' (" + llmConfig.sessionMaxPerProvider() + ")");
        }
    }

    private static String validateReasoningEffort(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (!VALID_REASONING_EFFORTS.contains(normalized)) {
            throw new LLMConfigError(
                    "Invalid reasoningEffort value '" + normalized + "'. Valid values: " + VALID_REASONING_EFFORTS);
        }
        return normalized;
    }
}
