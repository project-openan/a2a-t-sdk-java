package net.openan.a2at.sdk.llm.model;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.PromptMessage;

/**
 * Structured generation request with optional runtime overrides.
 *
 * @param messages ordered prompt messages
 * @param jsonSchema output JSON schema
 * @param provider optional provider override
 * @param model optional model override
 * @param temperature optional temperature override
 * @param maxTokens optional max-tokens override
 * @param timeoutSeconds optional timeout override
 * @since 2026-05
 */
public record StructuredGenerationRequest(
        List<PromptMessage> messages,
        Map<String, Object> jsonSchema,
        String provider,
        String model,
        Double temperature,
        Integer maxTokens,
        Double timeoutSeconds) {

    /**
     * Creates one request without runtime overrides.
     *
     * @param messages ordered prompt messages
     * @param jsonSchema output JSON schema
     */
    public StructuredGenerationRequest(List<PromptMessage> messages, Map<String, Object> jsonSchema) {
        this(messages, jsonSchema, null, null, null, null, null);
    }
}
