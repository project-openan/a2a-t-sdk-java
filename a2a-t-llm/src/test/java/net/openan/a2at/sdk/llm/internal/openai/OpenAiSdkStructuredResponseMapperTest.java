package net.openan.a2at.sdk.llm.internal.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import java.lang.reflect.Method;
import java.util.Optional;
import net.openan.a2at.sdk.llm.model.LLMResponse;
import org.junit.jupiter.api.Test;

class OpenAiSdkStructuredResponseMapperTest {

    @Test
    void mapBuildsUnifiedResponseModelFromChatCompletionsPayload() throws ReflectiveOperationException {
        ChatCompletion response = ChatCompletion.builder()
                .id("chatcmpl_123")
                .addChoice(ChatCompletion.Choice.builder()
                        .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .index(0)
                        .logprobs(Optional.empty())
                        .message(ChatCompletionMessage.builder()
                                .role(JsonValue.from("assistant"))
                                .content("{\"city\":\"shanghai\"}")
                                .refusal(Optional.empty())
                                .build())
                        .build())
                .created(1L)
                .model("gpt-4.1")
                .object_(JsonValue.from("chat.completion"))
                .usage(CompletionUsage.builder()
                        .promptTokens(11)
                        .completionTokens(7)
                        .totalTokens(18)
                        .build())
                .build();

        Class<?> mapperClass = Class.forName("net.openan.a2at.sdk.llm.internal.openai.OpenAiSdkStructuredResponseMapper");
        Object mapper = mapperClass.getDeclaredConstructor().newInstance();
        Method mapMethod = mapperClass.getDeclaredMethod("map", ChatCompletion.class);

        LLMResponse mapped = (LLMResponse) mapMethod.invoke(mapper, response);

        assertEquals("{\"city\":\"shanghai\"}", mapped.content());
        assertEquals("gpt-4.1", mapped.model());
        assertEquals(11, mapped.usage().promptTokens());
        assertEquals(7, mapped.usage().completionTokens());
        assertEquals(18, mapped.usage().totalTokens());
        assertEquals("chatcmpl_123", mapped.metadata().get("responseId"));
    }
}
