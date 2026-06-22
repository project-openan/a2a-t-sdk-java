package net.openan.a2at.sdk.llm;

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
        int sessionMaxPerProvider) {}
