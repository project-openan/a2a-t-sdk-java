package net.openan.a2at.sdk.llm.providers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import java.net.Proxy;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMConfigError;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI LLM provider client.
 *
 * @since 2026-06
 */
public class OpenAIClient implements LLMClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);

    /**
     * Dedicated logger for structured {@code llm_call} request/response logging (DEBUG level). Separate from the module
     * logger so embedding applications can filter LLM call logs independently, e.g.
     * {@code logging.level.net.openan.a2at.sdk.llm.call=DEBUG}.
     */
    private static final Logger CALL_LOG = LoggerFactory.getLogger("net.openan.a2at.sdk.llm.call");

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
            LLMClientConfig config, BiFunction<LLMClientConfig, ChatCompletionCreateParams, ChatCompletion> executor) {
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
        ChatCompletionCreateParams params = buildStructuredParams(messages, jsonSchema, temperature, maxTokens);
        long startNanos = System.nanoTime();
        boolean debugEnabled = CALL_LOG.isDebugEnabled();
        if (debugEnabled) {
            logRequest(
                    messages,
                    jsonSchema,
                    params.messages().size(),
                    temperature == null ? config.temperature() : temperature,
                    maxTokens == null ? config.maxTokens() : maxTokens);
        }
        try {
            ChatCompletion response = executor.apply(config, params);
            LLMResponse parsed = parseResponse(response);
            if (debugEnabled) {
                logResponse(startNanos, response);
            }
            return parsed;
        } catch (LLMConfigError | LLMRuntimeError error) {
            if (debugEnabled) {
                logError(startNanos, error);
            }
            throw error;
        } catch (Exception error) {
            if (debugEnabled) {
                logError(startNanos, error);
            }
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
        if (config.reasoningEffort() != null && !config.reasoningEffort().isBlank()) {
            builder.reasoningEffort(ReasoningEffort.of(config.reasoningEffort()));
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

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new LLMRuntimeError("Failed to serialize JSON schema", error);
        }
    }

    private LLMResponse parseResponse(ChatCompletion response) {
        return new LLMResponse(
                extractJsonObjectString(response), response.model(), mapUsage(response), mapMetadata(response));
    }

    private String extractJsonObjectString(ChatCompletion response) {
        String rawContent = extractMessageText(response);
        try {
            if (rawContent == null || rawContent.isBlank()) {
                throw new LLMRuntimeError(config.provider()
                        + " returned empty content (finish_reason=" + extractFinishReason(response)
                        + "): typically rate limiting, a request timeout, or max_tokens exhausted by reasoning"
                        + " content - check model quotas and the MAX_TOKENS/TIMEOUT settings");
            }
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

    private static String extractFinishReason(ChatCompletion response) {
        if (response.choices().isEmpty()) {
            return "unknown";
        }
        Object finishReason = response.choices().get(0).finishReason();
        return finishReason == null ? "none" : finishReason.toString();
    }

    private String extractMessageText(ChatCompletion response) {
        if (response.choices().isEmpty()) {
            throw new LLMRuntimeError(config.provider() + " response did not include any choices");
        }
        return response.choices()
                .get(0)
                .message()
                .content()
                .orElseThrow(() -> new LLMRuntimeError(config.provider()
                        + " response did not include message content (finish_reason="
                        + extractFinishReason(response) + ")"));
    }

    private static Map<String, Integer> mapUsage(ChatCompletion response) {
        Map<String, Integer> usage = new LinkedHashMap<>();
        if (response.usage().isEmpty()) {
            usage.put("prompt_tokens", 0);
            usage.put("completion_tokens", 0);
            usage.put("total_tokens", 0);
            return usage;
        }
        usage.put(
                "prompt_tokens", Math.toIntExact(response.usage().orElseThrow().promptTokens()));
        usage.put(
                "completion_tokens",
                Math.toIntExact(response.usage().orElseThrow().completionTokens()));
        usage.put("total_tokens", Math.toIntExact(response.usage().orElseThrow().totalTokens()));
        return usage;
    }

    private static Map<String, Object> mapMetadata(ChatCompletion response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("responseId", response.id());
        return metadata;
    }

    private void logRequest(
            List<Map<String, String>> messages,
            Map<String, Object> jsonSchema,
            int messageCount,
            Double resolvedTemperature,
            Integer resolvedMaxTokens) {
        String schemaText = "Return JSON that conforms to this JSON schema: " + toJson(jsonSchema);
        List<Map<String, String>> fullMessages = new ArrayList<>(messages.size() + 2);
        fullMessages.add(Map.of("role", "system", "content", JSON_MODE_INSTRUCTION));
        fullMessages.add(Map.of("role", "system", "content", schemaText));
        fullMessages.addAll(messages);
        String messagesJson = toJson(fullMessages);
        CALL_LOG.debug(
                "llm_call event=request ts={} provider={} model={} messages={} chars={} temperature={} max_tokens={}",
                utcNow(),
                config.provider(),
                config.model(),
                messageCount,
                messagesJson.length(),
                resolvedTemperature == null ? "-" : resolvedTemperature,
                resolvedMaxTokens == null ? "-" : resolvedMaxTokens);
        if (config.detailLogEnabled()) {
            CALL_LOG.debug(
                    "llm_call event=request_body ts={} provider={} model={} messages_json={}",
                    utcNow(),
                    config.provider(),
                    config.model(),
                    messagesJson);
        }
    }

    private void logResponse(long startNanos, ChatCompletion response) {
        String contentText = extractMessageText(response);
        Map<String, Integer> usage = mapUsage(response);
        CALL_LOG.debug(
                "llm_call event=response ts={} provider={} model={} elapsed_ms={} prompt_tokens={} completion_tokens={} total_tokens={} content_chars={} response_id={}",
                utcNow(),
                config.provider(),
                response.model(),
                elapsedMs(startNanos),
                usage.get("prompt_tokens"),
                usage.get("completion_tokens"),
                usage.get("total_tokens"),
                contentText.length(),
                response.id());
        if (config.detailLogEnabled()) {
            CALL_LOG.debug(
                    "llm_call event=response_body ts={} provider={} model={} content={}",
                    utcNow(),
                    config.provider(),
                    response.model(),
                    contentText);
        }
    }

    private void logError(long startNanos, Throwable error) {
        CALL_LOG.debug(
                "llm_call event=error ts={} provider={} model={} elapsed_ms={} error_code={} error={}",
                utcNow(),
                config.provider(),
                config.model(),
                elapsedMs(startNanos),
                error.getClass().getSimpleName(),
                error.getMessage());
    }

    private static String elapsedMs(long startNanos) {
        return String.format(Locale.ROOT, "%.1f", (System.nanoTime() - startNanos) / 1_000_000.0d);
    }

    private static String utcNow() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @SuppressWarnings("deprecation")
    private ChatCompletion executeWithSdk(LLMClientConfig runtimeConfig, ChatCompletionCreateParams params) {
        return sdkClient(runtimeConfig).chat().completions().create(params);
    }

    @SuppressWarnings("deprecation")
    private synchronized com.openai.client.OpenAIClient sdkClient(LLMClientConfig runtimeConfig) {
        if (sdkClient == null) {
            OpenAIOkHttpClient.Builder builder =
                    OpenAIOkHttpClient.builder().apiKey(runtimeConfig.apiKey()).baseUrl(runtimeConfig.baseUrl());
            if (runtimeConfig.disableSystemProxy()) {
                builder.proxy(Proxy.NO_PROXY);
            }
            if (!runtimeConfig.sslVerify()) {
                applyInsecureTls(builder);
            }
            if (runtimeConfig.timeoutSeconds() != null && runtimeConfig.timeoutSeconds() > 0.0d) {
                builder.timeout(Duration.ofMillis(Math.max(1L, Math.round(runtimeConfig.timeoutSeconds() * 1000.0d))));
            }
            sdkClient = builder.build();
        }
        return sdkClient;
    }

    /**
     * Disables TLS certificate-chain and hostname verification for the provider endpoint.
     *
     * <p>Only for controlled environments whose HTTPS endpoint cannot yet present a certificate trusted by the JVM.
     * Everything else on the connection keeps the OpenAI SDK defaults.
     */
    private static void applyInsecureTls(OpenAIOkHttpClient.Builder builder) {
        X509TrustManager trustAllManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {trustAllManager}, new SecureRandom());
            builder.sslSocketFactory(sslContext.getSocketFactory());
        } catch (Exception error) {
            throw new LLMConfigError("Failed to disable TLS certificate-chain and hostname verification", error);
        }
        builder.trustManager(trustAllManager);
        builder.hostnameVerifier((hostname, session) -> true);
        log.warn("[A2AT-LLM] TLS certificate chain and hostname verification are disabled; "
                + "use only in controlled environments with trusted networks");
    }
}
