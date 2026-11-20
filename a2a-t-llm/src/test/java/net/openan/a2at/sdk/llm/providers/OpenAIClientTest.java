package net.openan.a2at.sdk.llm.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.TextNode;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMConfigError;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class OpenAIClientTest {

    private final ListAppender<ILoggingEvent> callLogAppender = new ListAppender<>();

    private final Logger callLogger = (Logger) LoggerFactory.getLogger("net.openan.a2at.sdk.llm.call");

    @BeforeEach
    void attachCallLogAppender() {
        callLogAppender.start();
        callLogger.addAppender(callLogAppender);
    }

    @AfterEach
    void detachCallLogAppender() {
        callLogger.detachAppender(callLogAppender);
        callLogger.setLevel(null);
    }

    @Test
    void rejectsBlankApiKeyOnConstruction() {
        LLMConfigError error = assertThrows(
                LLMConfigError.class, () -> new OpenAIClient(config("   ", "https://api.example.test/v1")));

        assertTrue(error.getMessage().contains("api_key"));
    }

    @Test
    void allowsMissingBaseUrlUntilStructuredInvocation() {
        OpenAIClient client = new OpenAIClient(config("sk-test", null), (runtimeConfig, requestParams) -> {
            throw new AssertionError("executor should not run without baseUrl");
        });

        LLMConfigError error = assertThrows(
                LLMConfigError.class,
                () -> client.structured(
                        List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null));

        assertTrue(error.getMessage().contains("base_url"));
    }

    @Test
    void structuredBuildsJsonModePayloadAndParsesJsonObjectResponse() {
        AtomicReference<ChatCompletionCreateParams> capturedParams = new AtomicReference<>();
        OpenAIClient client =
                new OpenAIClient(config("sk-test", "https://api.example.test/v1"), (runtimeConfig, requestParams) -> {
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
        assertEquals(9L, maxTokens(params).orElseThrow());
        assertTrue(params.responseFormat().orElseThrow().isJsonObject());
        assertEquals(
                "json_object",
                params.responseFormat().orElseThrow().asJsonObject()._type().convert(String.class));
        assertEquals(3, params.messages().size());
        assertTrue(params.messages().get(0).isSystem());
        assertTrue(params.messages().get(0).asSystem().content().asText().contains("JSON"));
        assertTrue(params.messages().get(1).isSystem());
        assertTrue(params.messages().get(1).asSystem().content().asText().contains("device_type"));
        assertTrue(params.messages().get(2).isUser());
        assertEquals(
                "extract router", params.messages().get(2).asUser().content().asText());
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
                100,
                false,
                true,
                false,
                null);
        OpenAIClient client = new OpenAIClient(config, (runtimeConfig, requestParams) -> {
            capturedParams.set(requestParams);
            return chatCompletion("{}");
        });

        client.structured(List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null);

        assertEquals(0.4d, capturedParams.get().temperature().orElseThrow());
        assertEquals(128L, maxTokens(capturedParams.get()).orElseThrow());
    }

    @Test
    void structuredOmitsTemperatureAndMaxTokensWhenUnset() {
        AtomicReference<ChatCompletionCreateParams> capturedParams = new AtomicReference<>();
        OpenAIClient client =
                new OpenAIClient(config("sk-test", "https://api.example.test/v1"), (runtimeConfig, requestParams) -> {
                    capturedParams.set(requestParams);
                    return chatCompletion("{}");
                });

        client.structured(List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null);

        assertTrue(capturedParams.get().temperature().isEmpty());
        assertTrue(!maxTokens(capturedParams.get()).isPresent());
    }

    @Test
    void wrapsProviderFailuresAsRuntimeErrors() {
        OpenAIClient client =
                new OpenAIClient(config("sk-test", "https://api.example.test/v1"), (runtimeConfig, requestParams) -> {
                    throw new IllegalStateException("provider unavailable");
                });

        LLMRuntimeError error = assertThrows(
                LLMRuntimeError.class,
                () -> client.structured(
                        List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null));

        assertTrue(error.getMessage().contains("openai"));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    void rejectsNonJsonObjectResponses() {
        OpenAIClient client = new OpenAIClient(
                config("sk-test", "https://api.example.test/v1"),
                (runtimeConfig, requestParams) -> chatCompletion("[\"not-object\"]"));

        assertThrows(
                LLMRuntimeError.class,
                () -> client.structured(
                        List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null));
    }

    @Test
    void concurrentStructuredCallsAllSucceed() throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        OpenAIClient client =
                new OpenAIClient(config("sk-test", "https://api.example.test/v1"), (runtimeConfig, requestParams) -> {
                    if (invocations.getAndIncrement() == 0) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(error);
                        }
                    }
                    return chatCompletion("{}");
                });

        int threadCount = 8;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        client.structured(
                                List.of(Map.of("role", "user", "content", "hi")), Map.of("type", "object"), null, null);
                    } catch (Throwable error) {
                        firstFailure.compareAndSet(null, error);
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(null, firstFailure.get());
        assertEquals(8, invocations.get());
    }

    private static LLMClientConfig config(String apiKey, String baseUrl) {
        return new LLMClientConfig(
                "openai", "gpt-4o-mini", apiKey, baseUrl, 10, null, null, null, 300, 100, false, true, false, null);
    }

    private static LLMClientConfig config(String apiKey, String baseUrl, boolean detailLogEnabled) {
        return new LLMClientConfig(
                "openai",
                "gpt-4o-mini",
                apiKey,
                baseUrl,
                10,
                null,
                null,
                null,
                300,
                100,
                false,
                true,
                detailLogEnabled,
                null);
    }

    // ---- Detailed call logging (dedicated logger net.openan.a2at.sdk.llm.call at DEBUG) ----

    @Test
    void summaryLogsRecordedAtDebugWithoutPayloadWhenDetailLogDisabled() {
        OpenAIClient client = new OpenAIClient(
                config("sk-secret-summary", "https://api.example.test/v1", false),
                (runtimeConfig, requestParams) -> chatCompletion("{\"device_type\":\"router\"}"));

        client.structured(
                List.of(Map.of("role", "user", "content", "extract router")), Map.of("type", "object"), 0.25d, 9);

        List<String> messages = recordedMessages();
        assertTrue(messages.stream().anyMatch(line -> line.startsWith("llm_call event=request ")));
        assertTrue(messages.stream().anyMatch(line -> line.startsWith("llm_call event=response ")));
        assertTrue(messages.stream().noneMatch(line -> line.contains("event=request_body")));
        assertTrue(messages.stream().noneMatch(line -> line.contains("event=response_body")));

        String requestLine = messages.stream()
                .filter(line -> line.startsWith("llm_call event=request "))
                .findFirst()
                .orElseThrow();
        assertContains(
                requestLine,
                "provider=openai",
                "model=gpt-4o-mini",
                "messages=3",
                "chars=",
                "temperature=0.25",
                "max_tokens=9");

        String responseLine = messages.stream()
                .filter(line -> line.startsWith("llm_call event=response "))
                .findFirst()
                .orElseThrow();
        assertContains(
                responseLine,
                "provider=openai",
                "model=gpt-4o-mini",
                "elapsed_ms=",
                "prompt_tokens=7",
                "completion_tokens=2",
                "total_tokens=9",
                "content_chars=",
                "response_id=chatcmpl_123");

        assertTrue(
                messages.stream().noneMatch(line -> line.contains("sk-secret-summary")),
                "logs must not leak the API key");
    }

    @Test
    void payloadLinesRecordedWithoutTruncationWhenDetailLogEnabled() {
        OpenAIClient client = new OpenAIClient(
                config("sk-test", "https://api.example.test/v1", true),
                (runtimeConfig, requestParams) -> chatCompletion("{\"device_type\":\"router\"}"));

        client.structured(
                List.of(Map.of("role", "user", "content", "extract the device type here please")),
                Map.of("type", "object", "properties", Map.of("device_type", Map.of("type", "string"))),
                null,
                null);

        List<String> messages = recordedMessages();
        String requestBodyLine = messages.stream()
                .filter(line -> line.contains("event=request_body"))
                .findFirst()
                .orElseThrow();
        assertContains(
                requestBodyLine,
                "extract the device type here please",
                "device_type",
                "Return a valid JSON object string");
        String responseBodyLine = messages.stream()
                .filter(line -> line.contains("event=response_body"))
                .findFirst()
                .orElseThrow();
        assertContains(responseBodyLine, "{\"device_type\":\"router\"}");
    }

    @Test
    void errorLineRecordedOnProviderFailure() {
        OpenAIClient client = new OpenAIClient(
                config("sk-test", "https://api.example.test/v1", false), (runtimeConfig, requestParams) -> {
                    throw new IllegalStateException("provider unavailable");
                });

        assertThrows(
                LLMRuntimeError.class,
                () -> client.structured(
                        List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null));

        List<String> messages = recordedMessages();
        String errorLine = messages.stream()
                .filter(line -> line.startsWith("llm_call event=error "))
                .findFirst()
                .orElseThrow();
        assertContains(errorLine, "elapsed_ms=", "error_code=IllegalStateException", "error=provider unavailable");
        assertTrue(!errorLine.contains("prompt_tokens"), "token fields must not appear on the error line");
        assertTrue(messages.stream().noneMatch(line -> line.contains("event=response")));
    }

    @Test
    void noCallLogsWhenDedicatedLoggerNotEnabledForDebug() {
        callLogger.setLevel(Level.INFO);

        OpenAIClient client = new OpenAIClient(
                config("sk-test", "https://api.example.test/v1", true), (runtimeConfig, requestParams) -> {
                    return chatCompletion("{}");
                });

        client.structured(List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null);

        assertTrue(
                recordedMessages().isEmpty(),
                "no llm_call logs expected when the dedicated logger is not enabled for DEBUG");
    }

    private List<String> recordedMessages() {
        return callLogAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static void assertContains(String message, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), "expected [" + message + "] to contain [" + fragment + "]");
        }
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

    @SuppressWarnings("deprecation")
    private static OptionalLong maxTokens(ChatCompletionCreateParams params) {
        var maxTokens = params.maxTokens();
        return maxTokens.isPresent() ? OptionalLong.of(maxTokens.orElseThrow()) : OptionalLong.empty();
    }
}
