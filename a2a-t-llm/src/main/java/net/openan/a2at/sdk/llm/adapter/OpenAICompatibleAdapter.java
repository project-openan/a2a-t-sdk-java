package net.openan.a2at.sdk.llm.adapter;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.util.function.BiFunction;
import net.openan.a2at.sdk.llm.model.LLMResponse;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import net.openan.a2at.sdk.llm.config.LlmClientConfig;
import net.openan.a2at.sdk.llm.config.StructuredLlmRuntimeConfig;
import net.openan.a2at.sdk.llm.internal.openai.OpenAiSdkResponseExecutor;
import net.openan.a2at.sdk.llm.internal.openai.OpenAiSdkStructuredRequestMapper;
import net.openan.a2at.sdk.llm.internal.openai.OpenAiSdkStructuredResponseMapper;

/**
 * Default public OpenAI-compatible adapter backed by the OpenAI Java SDK.
 *
 * @since 2026-06
 */
public class OpenAICompatibleAdapter implements LLMAdapter {

    private final LlmClientConfig clientConfig;

    private final OpenAiSdkStructuredRequestMapper requestMapper;

    private final OpenAiSdkStructuredResponseMapper responseMapper;

    private final BiFunction<StructuredLlmRuntimeConfig, ChatCompletionCreateParams, ChatCompletion>
            chatCompletionExecutor;

    /**
     * Creates one unconfigured adapter placeholder.
     */
    public OpenAICompatibleAdapter() {
        this(null, new OpenAiSdkStructuredRequestMapper(), new OpenAiSdkStructuredResponseMapper(), null);
    }

    /**
     * Creates one configured adapter with one client default config.
     *
     * @param clientConfig client default config
     */
    public OpenAICompatibleAdapter(LlmClientConfig clientConfig) {
        this(
                clientConfig,
                new OpenAiSdkStructuredRequestMapper(),
                new OpenAiSdkStructuredResponseMapper(),
                (runtimeConfig, requestParams) ->
                        OpenAiSdkResponseExecutor.defaultExecutor().execute(runtimeConfig, requestParams));
    }

    OpenAICompatibleAdapter(
            LlmClientConfig clientConfig,
            OpenAiSdkStructuredRequestMapper requestMapper,
            OpenAiSdkStructuredResponseMapper responseMapper,
            BiFunction<StructuredLlmRuntimeConfig, ChatCompletionCreateParams, ChatCompletion> chatCompletionExecutor) {
        this.clientConfig = clientConfig;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.chatCompletionExecutor = chatCompletionExecutor;
    }

    @Override
    public LLMResponse structured(StructuredGenerationRequest request) {
        if (clientConfig == null || chatCompletionExecutor == null) {
            throw new UnsupportedOperationException("OpenAI-compatible adapter is not configured");
        }
        StructuredLlmRuntimeConfig runtimeConfig = StructuredLlmRuntimeConfig.from(clientConfig, request);
        return responseMapper.map(chatCompletionExecutor.apply(runtimeConfig, requestMapper.map(request, runtimeConfig)));
    }
}
