package net.openan.a2at.sdk.llm.internal.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import net.openan.a2at.sdk.llm.config.StructuredLlmRuntimeConfig;
import org.junit.jupiter.api.Test;

class OpenAiSdkStructuredRequestMapperTest {

    @Test
    void mapBuildsChatCompletionsRequestWithJsonObjectResponseFormat() throws ReflectiveOperationException {
        StructuredGenerationRequest request = new StructuredGenerationRequest(
                List.of(
                        new PromptMessage("system", "extract structured result"),
                        new PromptMessage("user", "city is shanghai")),
                Map.of(
                        "type", "object",
                        "properties", Map.of("city", Map.of("type", "string")),
                        "required", List.of("city")),
                null,
                "gpt-4.1-mini",
                0.25,
                256,
                15.0);
        StructuredLlmRuntimeConfig runtimeConfig = new StructuredLlmRuntimeConfig(
                "openai_compatible", "gpt-4.1-mini", "test-key", "https://api.openai.com/v1", 256, 0.25, 15.0);

        Class<?> mapperClass = Class.forName("net.openan.a2at.sdk.llm.internal.openai.OpenAiSdkStructuredRequestMapper");
        Object mapper = mapperClass.getDeclaredConstructor().newInstance();
        Method mapMethod = mapperClass.getDeclaredMethod(
                "map", StructuredGenerationRequest.class, StructuredLlmRuntimeConfig.class);

        ChatCompletionCreateParams params =
                (ChatCompletionCreateParams) mapMethod.invoke(mapper, request, runtimeConfig);

        assertEquals("gpt-4.1-mini", params.model().asString());
        assertEquals(0.25, params.temperature().orElseThrow());
        assertEquals(256L, params.maxTokens().orElseThrow());
        assertEquals(2, params.messages().size());
        assertTrue(params.messages().get(0).isSystem());
        assertEquals(
                "extract structured result",
                params.messages().get(0).asSystem().content().asText());
        assertTrue(params.messages().get(1).isUser());
        assertEquals(
                "city is shanghai",
                params.messages().get(1).asUser().content().asText());
        assertTrue(params.responseFormat().orElseThrow().isJsonObject());
        assertEquals(
                "json_object",
                params.responseFormat().orElseThrow().asJsonObject()._type().convert(String.class));
    }
}
