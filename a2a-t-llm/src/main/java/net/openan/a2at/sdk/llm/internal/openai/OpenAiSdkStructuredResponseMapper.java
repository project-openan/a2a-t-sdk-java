package net.openan.a2at.sdk.llm.internal.openai;

import com.openai.models.chat.completions.ChatCompletion;
import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.llm.model.LLMResponse;
import net.openan.a2at.sdk.llm.model.LlmUsage;

/**
 * Maps one OpenAI chat completions payload into the SDK unified response model.
 *
 * @since 2026-06
 */
public final class OpenAiSdkStructuredResponseMapper {

    /**
     * Maps one OpenAI chat completions payload into one unified response model.
     *
     * @param response OpenAI chat completions payload
     * @return unified SDK response
     */
    public LLMResponse map(ChatCompletion response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("responseId", response.id());
        return new LLMResponse(extractContent(response), response.model(), mapUsage(response), metadata);
    }

    private static String extractContent(ChatCompletion response) {
        if (response.choices().isEmpty()) {
            return "";
        }
        return response.choices().get(0).message().content().orElse("");
    }

    private static LlmUsage mapUsage(ChatCompletion response) {
        if (response.usage().isEmpty()) {
            return new LlmUsage(0, 0, 0);
        }
        return new LlmUsage(
                Math.toIntExact(response.usage().orElseThrow().promptTokens()),
                Math.toIntExact(response.usage().orElseThrow().completionTokens()),
                Math.toIntExact(response.usage().orElseThrow().totalTokens()));
    }
}
