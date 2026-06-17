package net.openan.a2at.sdk.llm.model;

import java.util.Map;

/**
 * Unified response model for LLM calls.
 *
 * @param content response content
 * @param model resolved model name
 * @param usage token usage summary
 * @param metadata extra response metadata
 * @since 2026-05
 */
public record LLMResponse(String content, String model, LlmUsage usage, Map<String, Object> metadata) {}
