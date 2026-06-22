package net.openan.a2at.sdk.llm.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.TextNode;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMConfigError;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class OpenAIClientTest {

    @Test
    void rejectsBlankApiKeyOnConstruction() {
        LLMConfigError error = assertThrows(LLMConfigError.class, () -> new OpenAIClient(config("   ", "https://api.example.test/v1")));

        assertTrue(error.getMessage().contains("api_key"));
    }

    @Test
    void allowsMissingBaseUrlUntilStructuredInvocation() {
        OpenAIClient client = new OpenAIClient(config("sk-test", null), (runtimeConfig, requestParams) -> {
            throw new AssertionError("executor should not run without baseUrl");
        });

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> client.structured(
                List.of(Map.of("role", "user", "content", "extract")),
                Map.of("type", "object"),
                null,
                null));

        assertTrue(error.getMessage().contains("base_url"));
    }

    @Test
    void structuredBuildsJsonModePayloadAndParsesJsonObjectResponse() {
        AtomicReference<ChatCompletionCreateParams> capturedParams = new AtomicReference<>();
        OpenAIClient client = new OpenAIClient(
                config("sk-test", "https://api.example.test/v1"),
                (runtimeConfig, requestParams) -> {
                    capturedParams.set(requestParams);
                    return chatCompletion("{\"device_type\":\"router\"}");
                });

        LLMResponse response = client.structured(
                List.of(Map.of("role", "user", "content", "extract router")),
                Map.of(
                        "type", "object",
                        "properties", Map.of("device_type", Map.of("type", "string")),
                        "required", List.of("device_type")),
                0.25d,
                9);

        assertEquals("{\"device_type\":\"router\"}", response.content());
        assertEquals("gpt-4o-mini", response.model());
        assertEquals(7, response.usage().get("prompt_tokens"));
        assertEquals(2, response.usage().get("completion_tokens"));
        assertEquals(9, response.usage().get("total_tokens"));
        assertEquals("chatcmpl_123", response.metadata().get("responseId"));

        ChatCompletionCreateParams params = capturedParams.get();
        assertEquals("gpt-4o-mini", params.model().asString());
        assertEquals(0.25d, params.temperature().orElseThrow());
        assertEquals(9L, params.maxTokens().orElseThrow());
        assertTrue(params.responseFormat().orElseThrow().isJsonObject());
        assertEquals("json_object", params.responseFormat().orElseThrow().asJsonObject()._type().convert(String.class));
        assertEquals(3, params.messages().size());
        assertTrue(params.messages().get(0).isSystem());
        assertTrue(params.messages().get(0).asSystem().content().asText().contains("JSON"));
        assertTrue(params.messages().get(1).isSystem());
        assertTrue(params.messages().get(1).asSystem().content().asText().contains("device_type"));
        assertTrue(params.messages().get(2).isUser());
        assertEquals("extract router", params.messages().get(2).asUser().content().asText());
    }

    @Test
    void structuredFallsBackToConfigTemperatureAndMaxTokens() {
        AtomicReference<ChatCompletionCreateParams> capturedParams = new AtomicReference<>();
        LLMClientConfig config = new LLMClientConfig(
                "openai",
                "gpt-4o-mini",
                "sk-test",
                "https://api.example.test/v1",
                10,
                128,
                0.4d,
                null,
                300,
                100);
        OpenAIClient client = new OpenAIClient(config, (runtimeConfig, requestParams) -> {
            capturedParams.set(requestParams);
            return chatCompletion("{}");
        });

        client.structured(List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null);

        assertEquals(0.4d, capturedParams.get().temperature().orElseThrow());
        assertEquals(128L, capturedParams.get().maxTokens().orElseThrow());
    }

    @Test
    void structuredOmitsTemperatureAndMaxTokensWhenUnset() {
        AtomicReference<ChatCompletionCreateParams> capturedParams = new AtomicReference<>();
        OpenAIClient client = new OpenAIClient(config("sk-test", "https://api.example.test/v1"), (runtimeConfig, requestParams) -> {
            capturedParams.set(requestParams);
            return chatCompletion("{}");
        });

        client.structured(List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null);

        assertTrue(capturedParams.get().temperature().isEmpty());
        assertTrue(capturedParams.get().maxTokens().isEmpty());
    }

    @Test
    void wrapsProviderFailuresAsRuntimeErrors() {
        OpenAIClient client = new OpenAIClient(
                config("sk-test", "https://api.example.test/v1"),
                (runtimeConfig, requestParams) -> {
                    throw new IllegalStateException("provider unavailable");
                });

        LLMRuntimeError error = assertThrows(LLMRuntimeError.class, () -> client.structured(
                List.of(Map.of("role", "user", "content", "extract")),
                Map.of("type", "object"),
                null,
                null));

        assertTrue(error.getMessage().contains("openai"));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    void rejectsNonJsonObjectResponses() {
        OpenAIClient client = new OpenAIClient(
                config("sk-test", "https://api.example.test/v1"),
                (runtimeConfig, requestParams) -> chatCompletion("[\"not-object\"]"));

        assertThrows(LLMRuntimeError.class, () -> client.structured(
                List.of(Map.of("role", "user", "content", "extract")),
                Map.of("type", "object"),
                null,
                null));
    }

    private static LLMClientConfig config(String apiKey, String baseUrl) {
        return new LLMClientConfig("openai", "gpt-4o-mini", apiKey, baseUrl, 10, null, null, null, 300, 100);
    }

    private static ChatCompletion chatCompletion(String content) {
        return ChatCompletion.builder()
                .id("chatcmpl_123")
                .addChoice(ChatCompletion.Choice.builder()
                        .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .index(0)
                        .logprobs(Optional.empty())
                        .message(ChatCompletionMessage.builder()
                                .role(jsonString("assistant"))
                                .content(content)
                                .refusal(Optional.empty())
                                .build())
                        .build())
                .created(1L)
                .model("gpt-4o-mini")
                .object_(jsonString("chat.completion"))
                .usage(CompletionUsage.builder()
                        .promptTokens(7)
                        .completionTokens(2)
                        .totalTokens(9)
                        .build())
                .build();
    }

    private static JsonValue jsonString(String value) {
        return JsonValue.fromJsonNode(TextNode.valueOf(value));
    }
}
