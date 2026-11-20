package net.openan.a2at.sdk.llm;

import java.util.Map;

/**
 * Normalized response from an LLM provider.
 *
 * @param content response content
 * @param model resolved model name
 * @param usage token usage summary
 * @param metadata extra response metadata
 * @since 2026-06
 */
public record LLMResponse(String content, String model, Map<String, Integer> usage, Map<String, Object> metadata) {}
