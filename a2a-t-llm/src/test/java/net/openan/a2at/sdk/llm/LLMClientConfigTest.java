package net.openan.a2at.sdk.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LLMClientConfigTest {

    @Test
    void storesResolvedDefaultLlmClientConfiguration() {
        LLMClientConfig config = new LLMClientConfig(
                "openai",
                "gpt-4o-mini",
                "sk-test",
                "https://api.example.test/v1",
                10,
                1024,
                0.2d,
                15.5d,
                300,
                100);

        assertEquals("openai", config.provider());
        assertEquals("gpt-4o-mini", config.model());
        assertEquals("sk-test", config.apiKey());
        assertEquals("https://api.example.test/v1", config.baseUrl());
        assertEquals(10, config.historyWindow());
        assertEquals(1024, config.maxTokens());
        assertEquals(0.2d, config.temperature());
        assertEquals(15.5d, config.timeoutSeconds());
        assertEquals(300, config.sessionMaxTotal());
        assertEquals(100, config.sessionMaxPerProvider());
    }
}
