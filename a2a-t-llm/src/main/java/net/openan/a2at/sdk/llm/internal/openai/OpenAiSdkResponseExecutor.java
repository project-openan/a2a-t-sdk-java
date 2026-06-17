package net.openan.a2at.sdk.llm.internal.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.time.Duration;
import net.openan.a2at.sdk.llm.config.StructuredLlmRuntimeConfig;

/**
 * Executes one OpenAI chat completions request through the OpenAI Java SDK.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface OpenAiSdkResponseExecutor {

    /**
     * Executes one chat completions request.
     *
     * @param runtimeConfig resolved runtime config
     * @param requestParams mapped OpenAI chat completions request
     * @return OpenAI chat completions payload
     */
    ChatCompletion execute(StructuredLlmRuntimeConfig runtimeConfig, ChatCompletionCreateParams requestParams);

    /**
     * Creates the default executor backed by the OpenAI Java SDK.
     *
     * @return default OpenAI SDK executor
     */
    static OpenAiSdkResponseExecutor defaultExecutor() {
        return (runtimeConfig, requestParams) -> createClient(runtimeConfig)
                .chat()
                .completions()
                .create(requestParams);
    }

    private static OpenAIClient createClient(StructuredLlmRuntimeConfig runtimeConfig) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder().apiKey(runtimeConfig.apiKey());
        if (runtimeConfig.baseUrl() != null && !runtimeConfig.baseUrl().isBlank()) {
            builder.baseUrl(runtimeConfig.baseUrl());
        }
        if (runtimeConfig.timeoutSeconds() > 0.0d) {
            builder.timeout(Duration.ofMillis(Math.max(1L, Math.round(runtimeConfig.timeoutSeconds() * 1000.0d))));
        }
        return builder.build();
    }
}
