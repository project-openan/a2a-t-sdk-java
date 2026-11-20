package net.openan.a2at.sdk.llm;

import java.util.List;
import java.util.Map;

/**
 * Provider-facing LLM client interface.
 *
 * @since 2026-06
 */
public interface LLMClient {

    /**
     * Generates a structured response constrained by the provided JSON schema.
     *
     * @param messages ordered prompt messages
     * @param jsonSchema output JSON schema
     * @param temperature optional temperature override
     * @param maxTokens optional max token override
     * @return provider response
     */
    LLMResponse structured(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema, Double temperature, Integer maxTokens);
}
