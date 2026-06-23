package net.openan.a2at.sdk.llm.providers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMConfigError;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;

/**
 * OpenAI LLM provider client.
 *
 * @since 2026-06
 */
public class OpenAIClient implements LLMClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String JSON_MODE_INSTRUCTION =
            "Return a valid JSON object string. The output must be valid json. "
                    + "Do not wrap the response in markdown code fences. "
                    + "Do not include any explanation outside the JSON object.";

    private final LLMClientConfig config;

    private final BiFunction<LLMClientConfig, ChatCompletionCreateParams, ChatCompletion> executor;

    @SuppressWarnings("deprecation")
    private com.openai.client.OpenAIClient sdkClient;

    public OpenAIClient(LLMClientConfig config) {
        this(config, null);
    }

    OpenAIClient(
            LLMClientConfig config,
            BiFunction<LLMClientConfig, ChatCompletionCreateParams, ChatCompletion> executor) {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new LLMConfigError(config.provider() + " client requires a non-empty api_key");
        }
        this.config = config;
        this.executor = executor == null ? this::executeWithSdk : executor;
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema, Double temperature, Integer maxTokens) {
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw new LLMConfigError(config.provider() + " client requires a non-empty base_url");
        }
        try {
            return parseResponse(executor.apply(config, buildStructuredParams(messages, jsonSchema, temperature, maxTokens)));
        } catch (LLMConfigError | LLMRuntimeError error) {
            throw error;
        } catch (Exception error) {
            throw new LLMRuntimeError(config.provider() + " invocation failed: " + error.getMessage(), error);
        }
    }

    @SuppressWarnings("deprecation")
    private ChatCompletionCreateParams buildStructuredParams(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema, Double temperature, Integer maxTokens) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(config.model())
                .messages(buildStructuredMessages(messages, jsonSchema))
                .responseFormat(ResponseFormatJsonObject.builder().build());
        Double resolvedTemperature = temperature == null ? config.temperature() : temperature;
        Integer resolvedMaxTokens = maxTokens == null ? config.maxTokens() : maxTokens;
        if (resolvedTemperature != null) {
            builder.temperature(resolvedTemperature);
        }
        if (resolvedMaxTokens != null) {
            builder.maxTokens(resolvedMaxTokens);
        }
        return builder.build();
    }

    private List<ChatCompletionMessageParam> buildStructuredMessages(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema) {
        List<ChatCompletionMessageParam> mappedMessages = new ArrayList<>();
        mappedMessages.add(systemMessage(JSON_MODE_INSTRUCTION));
        mappedMessages.add(systemMessage("Return JSON that conforms to this JSON schema: " + toJson(jsonSchema)));
        for (Map<String, String> message : messages) {
            mappedMessages.add(mapMessage(message));
        }
        return mappedMessages;
    }

    private static ChatCompletionMessageParam mapMessage(Map<String, String> message) {
        String role = message.getOrDefault("role", "").toLowerCase(Locale.ROOT);
        String content = message.getOrDefault("content", "");
        if ("system".equals(role)) {
            return systemMessage(content);
        }
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(content).build());
    }

    private static ChatCompletionMessageParam systemMessage(String content) {
        return ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(content).build());
    }

    private static String toJson(Map<String, Object> jsonSchema) {
        try {
            return OBJECT_MAPPER.writeValueAsString(jsonSchema);
        } catch (JsonProcessingException error) {
            throw new LLMRuntimeError("Failed to serialize JSON schema", error);
        }
    }

    private LLMResponse parseResponse(ChatCompletion response) {
        return new LLMResponse(extractJsonObjectString(response), response.model(), mapUsage(response), mapMetadata(response));
    }

    private String extractJsonObjectString(ChatCompletion response) {
        String rawContent = extractMessageText(response);
        try {
            Object parsed = OBJECT_MAPPER.readValue(rawContent, Object.class);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new LLMRuntimeError(config.provider() + " must return a JSON object string");
            }
            return rawContent;
        } catch (LLMRuntimeError error) {
            throw error;
        } catch (JsonProcessingException error) {
            throw new LLMRuntimeError(config.provider() + " returned invalid json: " + error.getMessage(), error);
        }
    }

    private String extractMessageText(ChatCompletion response) {
        if (response.choices().isEmpty()) {
            throw new LLMRuntimeError(config.provider() + " response did not include any choices");
        }
        return response.choices().get(0).message().content().orElseThrow(
                () -> new LLMRuntimeError(config.provider() + " response did not include message content"));
    }

    private static Map<String, Integer> mapUsage(ChatCompletion response) {
        Map<String, Integer> usage = new LinkedHashMap<>();
        if (response.usage().isEmpty()) {
            usage.put("prompt_tokens", 0);
            usage.put("completion_tokens", 0);
            usage.put("total_tokens", 0);
            return usage;
        }
        usage.put("prompt_tokens", Math.toIntExact(response.usage().orElseThrow().promptTokens()));
        usage.put("completion_tokens", Math.toIntExact(response.usage().orElseThrow().completionTokens()));
        usage.put("total_tokens", Math.toIntExact(response.usage().orElseThrow().totalTokens()));
        return usage;
    }

    private static Map<String, Object> mapMetadata(ChatCompletion response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("responseId", response.id());
        return metadata;
    }

    @SuppressWarnings("deprecation")
    private ChatCompletion executeWithSdk(LLMClientConfig runtimeConfig, ChatCompletionCreateParams params) {
        return sdkClient(runtimeConfig).chat().completions().create(params);
    }

    @SuppressWarnings("deprecation")
    private com.openai.client.OpenAIClient sdkClient(LLMClientConfig runtimeConfig) {
        if (sdkClient != null) {
            return sdkClient;
        }
        OpenAIOkHttpClient.Builder builder =
                OpenAIOkHttpClient.builder().apiKey(runtimeConfig.apiKey()).baseUrl(runtimeConfig.baseUrl());
        if (runtimeConfig.timeoutSeconds() != null && runtimeConfig.timeoutSeconds() > 0.0d) {
            builder.timeout(Duration.ofMillis(Math.max(1L, Math.round(runtimeConfig.timeoutSeconds() * 1000.0d))));
        }
        sdkClient = builder.build();
        return sdkClient;
    }
}
