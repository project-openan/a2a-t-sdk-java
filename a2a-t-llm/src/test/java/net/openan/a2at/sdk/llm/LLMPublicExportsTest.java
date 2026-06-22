package net.openan.a2at.sdk.llm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class LLMPublicExportsTest {

    @Test
    void simplifiedProviderExtensionSurfaceIsPubliclyLoadable() {
        List<String> publicTypes = List.of(
                "net.openan.a2at.sdk.llm.LLMClient",
                "net.openan.a2at.sdk.llm.LLMClientFactory",
                "net.openan.a2at.sdk.llm.LLMConfigLoader",
                "net.openan.a2at.sdk.llm.LLMClientConfig",
                "net.openan.a2at.sdk.llm.LLMResponse",
                "net.openan.a2at.sdk.llm.LLMError",
                "net.openan.a2at.sdk.llm.LLMConfigError",
                "net.openan.a2at.sdk.llm.LLMRuntimeError",
                "net.openan.a2at.sdk.llm.providers.OpenAIClient");

        for (String publicType : publicTypes) {
            assertDoesNotThrow(() -> Class.forName(publicType), publicType);
        }
    }

    @Test
    void legacyAdapterBuilderParserTransportMapperAndStructuredRequestTypesAreNotPublic() {
        List<String> legacyTypes = List.of(
                "net.openan.a2at.sdk.llm.adapter.LLMAdapter",
                "net.openan.a2at.sdk.llm.adapter.OpenAICompatibleAdapter",
                "net.openan.a2at.sdk.llm.PayloadBuilder",
                "net.openan.a2at.sdk.llm.ResponseParser",
                "net.openan.a2at.sdk.llm.TransportAdapter",
                "net.openan.a2at.sdk.llm.OpenAICompatiblePayloadBuilder",
                "net.openan.a2at.sdk.llm.OpenAICompatibleResponseParser",
                "net.openan.a2at.sdk.llm.OpenAICompatibleTransportAdapter",
                "net.openan.a2at.sdk.llm.model.StructuredGenerationRequest",
                "net.openan.a2at.sdk.llm.model.LlmUsage",
                "net.openan.a2at.sdk.llm.exception.LlmConfigException",
                "net.openan.a2at.sdk.llm.internal.openai.OpenAiSdkResponseExecutor",
                "net.openan.a2at.sdk.llm.internal.openai.OpenAiSdkStructuredRequestMapper",
                "net.openan.a2at.sdk.llm.internal.openai.OpenAiSdkStructuredResponseMapper",
                "net.openan.a2at.sdk.llm.internal.parsing.JsonObjectResponseParser");

        for (String legacyType : legacyTypes) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(legacyType), legacyType);
        }
    }
}
