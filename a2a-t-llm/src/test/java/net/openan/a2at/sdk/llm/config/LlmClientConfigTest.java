package net.openan.a2at.sdk.llm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmClientConfigTest {

    @Test
    void fromMapLoadsRequiredAndOptionalLlmValues() {
        LlmClientConfig config = LlmClientConfig.fromMap(Map.of(
                "A2AT_LLM_PROVIDER", "openai_compatible",
                "A2AT_LLM_MODEL", "gpt-4.1",
                "A2AT_LLM_API_KEY", "test-key",
                "A2AT_LLM_BASE_URL", "https://api.openai.com/v1",
                "A2AT_LLM_MAX_TOKENS", "512",
                "A2AT_LLM_TEMPERATURE", "0.6",
                "A2AT_LLM_TIMEOUT_SECONDS", "9.5"));

        assertEquals("openai_compatible", config.provider());
        assertEquals("gpt-4.1", config.model());
        assertEquals("test-key", config.apiKey());
        assertEquals("https://api.openai.com/v1", config.baseUrl());
        assertEquals(512, config.maxTokens());
        assertEquals(0.6d, config.temperature());
        assertEquals(9.5d, config.timeoutSeconds());
    }
}
