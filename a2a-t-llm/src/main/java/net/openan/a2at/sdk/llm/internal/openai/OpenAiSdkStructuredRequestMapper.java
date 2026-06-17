package net.openan.a2at.sdk.llm.internal.openai;

import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import net.openan.a2at.sdk.llm.config.StructuredLlmRuntimeConfig;

/**
 * Maps one SDK structured request into one OpenAI chat completions request.
 *
 * @since 2026-06
 */
public final class OpenAiSdkStructuredRequestMapper {

    /**
     * Maps one structured generation request into one OpenAI chat completions request.
     *
     * @param request structured generation request
     * @param runtimeConfig resolved runtime config
     * @return mapped OpenAI chat completions request
     */
    public ChatCompletionCreateParams map(
            StructuredGenerationRequest request, StructuredLlmRuntimeConfig runtimeConfig) {
        return ChatCompletionCreateParams.builder()
                .model(runtimeConfig.model())
                .temperature(runtimeConfig.temperature())
                .maxTokens(runtimeConfig.maxTokens())
                .messages(mapMessages(request.messages()))
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();
    }

    private static List<ChatCompletionMessageParam> mapMessages(List<PromptMessage> messages) {
        return messages.stream()
                .map(OpenAiSdkStructuredRequestMapper::mapMessage)
                .collect(Collectors.toList());
    }

    private static ChatCompletionMessageParam mapMessage(PromptMessage message) {
        String normalizedRole = message.role() == null ? "" : message.role().toLowerCase(Locale.ROOT);
        if ("system".equals(normalizedRole)) {
            return ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder().content(message.content()).build());
        }
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(message.content()).build());
    }
}
