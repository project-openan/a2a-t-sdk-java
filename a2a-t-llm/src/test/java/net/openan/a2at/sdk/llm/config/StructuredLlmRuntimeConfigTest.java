package net.openan.a2at.sdk.llm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import org.junit.jupiter.api.Test;

class StructuredLlmRuntimeConfigTest {

    @Test
    void fromMergesRequestOverridesOnTopOfClientDefaults() {
        LlmClientConfig defaults = new LlmClientConfig(
                "openai_compatible",
                "gpt-4.1",
                "default-key",
                "https://default.example.com/v1",
                1024,
                0.2d,
                30.0d);
        StructuredGenerationRequest request = new StructuredGenerationRequest(
                List.of(new PromptMessage("user", "extract slots")),
                Map.of("type", "object"),
                "deepseek",
                "deepseek-chat",
                0.7d,
                256,
                8.0d);

        StructuredLlmRuntimeConfig runtimeConfig = StructuredLlmRuntimeConfig.from(defaults, request);

        assertEquals("deepseek", runtimeConfig.provider());
        assertEquals("deepseek-chat", runtimeConfig.model());
        assertEquals("default-key", runtimeConfig.apiKey());
        assertEquals("https://default.example.com/v1", runtimeConfig.baseUrl());
        assertEquals(256, runtimeConfig.maxTokens());
        assertEquals(0.7d, runtimeConfig.temperature());
        assertEquals(8.0d, runtimeConfig.timeoutSeconds());
    }
}
