package net.openan.a2at.sdk.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.core.model.LlmConfig;
import org.junit.jupiter.api.Test;

class LLMClientConfigFromTest {

    @Test
    void derivesFullConfigCorrectly() {
        LlmConfig llmConfig = new LlmConfig(
                "openai",
                "gpt-4o",
                "sk-test",
                "https://api.example.test/v1",
                20,
                2048,
                0.5d,
                30.0d,
                500,
                200,
                false,
                true,
                false,
                "medium",
                3,
                List.of());

        LLMClientConfig config = LLMClientConfig.from(llmConfig);

        assertEquals("openai", config.provider());
        assertEquals("gpt-4o", config.model());
        assertEquals("sk-test", config.apiKey());
        assertEquals("https://api.example.test/v1", config.baseUrl());
        assertEquals(20, config.historyWindow());
        assertEquals(2048, config.maxTokens());
        assertEquals(0.5d, config.temperature());
        assertEquals(30.0d, config.timeoutSeconds());
        assertEquals(false, config.disableSystemProxy());
        assertEquals(true, config.sslVerify());
        assertEquals(false, config.detailLogEnabled());
        assertEquals("medium", config.reasoningEffort());
        assertEquals(500, config.sessionMaxTotal());
        assertEquals(200, config.sessionMaxPerProvider());
    }

    @Test
    void carriesDetailLogEnabledThroughFrom() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 100, false, true, true, null, 3,
                List.of());

        LLMClientConfig config = LLMClientConfig.from(llmConfig);

        assertEquals(true, config.detailLogEnabled());
    }

    @Test
    void carriesSslVerifyDisabledThroughFrom() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 100, false, false, false, null, 3,
                List.of());

        LLMClientConfig config = LLMClientConfig.from(llmConfig);

        assertEquals(false, config.sslVerify());
    }

    @Test
    void legacyLlmConfigConstructorKeepsSslVerifyEnabled() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 100, false, null, 3, List.of());

        LLMClientConfig config = LLMClientConfig.from(llmConfig);

        assertEquals(true, config.sslVerify());
        assertEquals(false, config.detailLogEnabled());
    }

    @Test
    void rejectsMissingProvider() {
        LlmConfig llmConfig = new LlmConfig(
                "", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 100, false, true, false, null, 3, List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("provider"));
    }

    @Test
    void rejectsMissingModel() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "", "sk-test", null, 10, null, null, null, 300, 100, false, true, false, null, 3, List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("model"));
    }

    @Test
    void rejectsMissingApiKey() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "", null, 10, null, null, null, 300, 100, false, true, false, null, 3, List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("apiKey"));
    }

    @Test
    void rejectsMultipleMissingKeysAndListsThemAll() {
        LlmConfig llmConfig =
                new LlmConfig("", "", "", null, 10, null, null, null, 300, 100, false, true, false, null, 3, List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("provider"));
        assertTrue(error.getMessage().contains("model"));
        assertTrue(error.getMessage().contains("apiKey"));
    }

    @Test
    void rejectsHistoryWindowAbove100() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 101, null, null, null, 300, 100, false, true, false, null, 3,
                List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("historyWindow"));
    }

    @Test
    void rejectsSessionMaxTotalAbove3000() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 3001, 100, false, true, false, null, 3,
                List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("sessionMaxTotal"));
    }

    @Test
    void rejectsSessionMaxPerProviderAbove1000() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 1001, false, true, false, null, 3,
                List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("sessionMaxPerProvider"));
    }

    @Test
    void rejectsSessionMaxTotalSmallerThanSessionMaxPerProvider() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 50, 100, false, true, false, null, 3,
                List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("sessionMaxTotal"));
        assertTrue(error.getMessage().contains("sessionMaxPerProvider"));
    }

    @Test
    void derivesUnconfiguredOptionalsAsNull() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 100, false, true, false, null, 3,
                List.of());

        LLMClientConfig config = LLMClientConfig.from(llmConfig);

        assertNull(config.baseUrl());
        assertNull(config.maxTokens());
        assertNull(config.temperature());
        assertNull(config.timeoutSeconds());
        assertEquals(false, config.disableSystemProxy());
        assertEquals(true, config.sslVerify());
        assertNull(config.reasoningEffort());
    }

    @Test
    void normalizesReasoningEffortCase() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 100, false, true, false, "MEDIUM", 3,
                List.of());

        LLMClientConfig config = LLMClientConfig.from(llmConfig);

        assertEquals("medium", config.reasoningEffort());
    }

    @Test
    void rejectsUnknownReasoningEffortValue() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 100, false, true, false, "middle", 3,
                List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("reasoningEffort"));
        assertTrue(error.getMessage().contains("middle"));
    }

    @Test
    void treatsBlankReasoningEffortAsNull() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 100, false, true, false, "  ", 3,
                List.of());

        LLMClientConfig config = LLMClientConfig.from(llmConfig);

        assertNull(config.reasoningEffort());
    }

    @Test
    void treatsNullReasoningEffortAsNull() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 100, false, true, false, null, 3,
                List.of());

        LLMClientConfig config = LLMClientConfig.from(llmConfig);

        assertNull(config.reasoningEffort());
    }

    @Test
    void rejectsParseErrorsAndListsThemAll() {
        LlmConfig llmConfig = new LlmConfig(
                "openai",
                "gpt-4o",
                "sk-test",
                null,
                10,
                null,
                null,
                null,
                300,
                100,
                false,
                true,
                false,
                null,
                3,
                List.of(
                        "A2AT_LLM_MAX_TOKENS: non-numeric value 'garbage'",
                        "A2AT_LLM_TEMPERATURE: non-numeric value 'xyz'"));

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("A2AT_LLM_MAX_TOKENS"));
        assertTrue(error.getMessage().contains("A2AT_LLM_TEMPERATURE"));
    }

    @Test
    void acceptsEmptyParseErrors() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, 2048, 0.5d, 30.0d, 500, 200, false, true, false, "medium", 3,
                List.of());

        LLMClientConfig config = LLMClientConfig.from(llmConfig);

        assertEquals("medium", config.reasoningEffort());
        assertEquals(2048, config.maxTokens());
    }

    @Test
    void rejectsHistoryWindowZeroOrNegative() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 0, null, null, null, 300, 100, false, true, false, null, 3,
                List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("historyWindow"));
    }

    @Test
    void rejectsSessionMaxTotalZeroOrNegative() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 0, 100, false, true, false, null, 3,
                List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("sessionMaxTotal"));
    }

    @Test
    void rejectsSessionMaxPerProviderZeroOrNegative() {
        LlmConfig llmConfig = new LlmConfig(
                "openai", "gpt-4o", "sk-test", null, 10, null, null, null, 300, 0, false, true, false, null, 3,
                List.of());

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMClientConfig.from(llmConfig));

        assertTrue(error.getMessage().contains("sessionMaxPerProvider"));
    }

    @Test
    void rejectsNullLlmConfig() {
        assertThrows(NullPointerException.class, () -> LLMClientConfig.from(null));
    }
}
