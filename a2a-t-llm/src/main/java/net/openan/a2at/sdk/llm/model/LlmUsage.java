package net.openan.a2at.sdk.llm.model;

/**
 * Token usage summary for one LLM response.
 *
 * @param promptTokens prompt token count
 * @param completionTokens completion token count
 * @param totalTokens total token count
 * @since 2026-06
 */
public record LlmUsage(int promptTokens, int completionTokens, int totalTokens) {}
