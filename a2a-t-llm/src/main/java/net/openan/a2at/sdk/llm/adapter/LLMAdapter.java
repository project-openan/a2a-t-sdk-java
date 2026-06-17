package net.openan.a2at.sdk.llm.adapter;

import net.openan.a2at.sdk.llm.model.LLMResponse;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;

/**
 * Public extension point for structured LLM adapters.
 *
 * @since 2026-06
 */
public interface LLMAdapter {

    /**
     * Executes one structured generation request.
     *
     * @param request structured generation request
     * @return structured generation response
     */
    LLMResponse structured(StructuredGenerationRequest request);
}
