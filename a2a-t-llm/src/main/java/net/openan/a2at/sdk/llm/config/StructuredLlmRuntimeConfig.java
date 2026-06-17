package net.openan.a2at.sdk.llm.config;

import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;

/**
 * Resolved runtime config for one structured LLM request.
 *
 * @param provider resolved provider
 * @param model resolved model
 * @param apiKey resolved API key
 * @param baseUrl resolved base URL
 * @param maxTokens resolved max tokens
 * @param temperature resolved temperature
 * @param timeoutSeconds resolved timeout seconds
 * @since 2026-06
 */
public record StructuredLlmRuntimeConfig(
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int maxTokens,
        double temperature,
        double timeoutSeconds) {

    /**
     * Merges request overrides on top of one client default config.
     *
     * @param defaults client default config
     * @param request structured generation request
     * @return resolved runtime config
     */
    public static StructuredLlmRuntimeConfig from(LlmClientConfig defaults, StructuredGenerationRequest request) {
        return new StructuredLlmRuntimeConfig(
                request.provider() == null || request.provider().isBlank() ? defaults.provider() : request.provider(),
                request.model() == null || request.model().isBlank() ? defaults.model() : request.model(),
                defaults.apiKey(),
                defaults.baseUrl(),
                request.maxTokens() == null ? defaults.maxTokens() : request.maxTokens(),
                request.temperature() == null ? defaults.temperature() : request.temperature(),
                request.timeoutSeconds() == null ? defaults.timeoutSeconds() : request.timeoutSeconds());
    }
}
