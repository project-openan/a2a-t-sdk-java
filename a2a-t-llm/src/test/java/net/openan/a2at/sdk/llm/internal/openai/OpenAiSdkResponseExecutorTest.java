package net.openan.a2at.sdk.llm.internal.openai;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import net.openan.a2at.sdk.llm.config.StructuredLlmRuntimeConfig;
import org.junit.jupiter.api.Test;

class OpenAiSdkResponseExecutorTest {

    @Test
    void defaultExecutorHonorsRuntimeTimeoutForSlowChatCompletionsEndpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", this::writeDelayedChatCompletionResponse);
        server.start();

        try {
            StructuredLlmRuntimeConfig runtimeConfig = new StructuredLlmRuntimeConfig(
                    "deepseek",
                    "deepseek-chat",
                    "test-key",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    64,
                    0.0d,
                    0.2d);
            StructuredGenerationRequest request = new StructuredGenerationRequest(
                    List.of(new PromptMessage("user", "respond with json")),
                    Map.of("type", "object"));

            assertThrows(
                    RuntimeException.class,
                    () -> OpenAiSdkResponseExecutor.defaultExecutor()
                            .execute(runtimeConfig, new OpenAiSdkStructuredRequestMapper().map(request, runtimeConfig)));
        } finally {
            server.stop(0);
        }
    }

    private void writeDelayedChatCompletionResponse(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(Duration.ofSeconds(1).toMillis());
            byte[] responseBody = """
                    {
                      "id": "chatcmpl_slow",
                      "choices": [
                        {
                          "finish_reason": "stop",
                          "index": 0,
                          "logprobs": null,
                          "message": {
                            "role": "assistant",
                            "content": "{\\"ok\\":true}",
                            "refusal": null
                          }
                        }
                      ],
                      "created": 1,
                      "model": "deepseek-chat",
                      "object": "chat.completion",
                      "usage": {
                        "prompt_tokens": 5,
                        "completion_tokens": 3,
                        "total_tokens": 8
                      }
                    }
                    """
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while delaying response", exception);
        } finally {
            exchange.close();
        }
    }
}
