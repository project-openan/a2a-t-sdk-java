package net.openan.a2at.sdk.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LLMConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsRequiredValuesAndDefaultsFromEnvFile() throws IOException {
        Path envFile = writeEnv("""
                A2AT_LLM_PROVIDER=openai
                A2AT_LLM_MODEL=gpt-4o-mini
                A2AT_LLM_API_KEY=sk-test
                """);

        LLMClientConfig config = LLMConfigLoader.load(envFile);

        assertEquals("openai", config.provider());
        assertEquals("gpt-4o-mini", config.model());
        assertEquals("sk-test", config.apiKey());
        assertNull(config.baseUrl());
        assertEquals(10, config.historyWindow());
        assertNull(config.maxTokens());
        assertNull(config.temperature());
        assertNull(config.timeoutSeconds());
        assertEquals(300, config.sessionMaxTotal());
        assertEquals(100, config.sessionMaxPerProvider());
    }

    @Test
    void loadsOptionalValuesFromEnvFile() throws IOException {
        Path envFile = writeEnv("""
                A2AT_LLM_PROVIDER=openai
                A2AT_LLM_MODEL=gpt-4o-mini
                A2AT_LLM_API_KEY=sk-test
                A2AT_LLM_BASE_URL=https://api.example.test/v1
                A2AT_LLM_MAX_TOKENS=2048
                A2AT_LLM_TEMPERATURE=0.25
                A2AT_LLM_TIMEOUT_SECONDS=45.5
                A2AT_LLM_HISTORY_WINDOW=20
                A2AT_LLM_SESSION_MAX_TOTAL=500
                A2AT_LLM_SESSION_MAX_PER_PROVIDER=50
                """);

        LLMClientConfig config = LLMConfigLoader.load(envFile);

        assertEquals("https://api.example.test/v1", config.baseUrl());
        assertEquals(2048, config.maxTokens());
        assertEquals(0.25d, config.temperature());
        assertEquals(45.5d, config.timeoutSeconds());
        assertEquals(20, config.historyWindow());
        assertEquals(500, config.sessionMaxTotal());
        assertEquals(50, config.sessionMaxPerProvider());
    }

    @Test
    void rejectsMissingRequiredValues() throws IOException {
        Path envFile = writeEnv("""
                A2AT_LLM_PROVIDER=openai
                A2AT_LLM_API_KEY=sk-test
                """);

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMConfigLoader.load(envFile));

        assertTrue(error.getMessage().contains("A2AT_LLM_PROVIDER"));
        assertTrue(error.getMessage().contains("A2AT_LLM_MODEL"));
    }

    @Test
    void rejectsInvalidOptionalNumber() throws IOException {
        Path envFile = writeEnv("""
                A2AT_LLM_PROVIDER=openai
                A2AT_LLM_MODEL=gpt-4o-mini
                A2AT_LLM_API_KEY=sk-test
                A2AT_LLM_MAX_TOKENS=many
                """);

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMConfigLoader.load(envFile));

        assertTrue(error.getMessage().contains("A2AT_LLM_MAX_TOKENS"));
        assertTrue(error.getMessage().contains("integer"));
    }

    @Test
    void rejectsOutOfRangeReservedValues() throws IOException {
        Path envFile = writeEnv("""
                A2AT_LLM_PROVIDER=openai
                A2AT_LLM_MODEL=gpt-4o-mini
                A2AT_LLM_API_KEY=sk-test
                A2AT_LLM_HISTORY_WINDOW=101
                """);

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMConfigLoader.load(envFile));

        assertTrue(error.getMessage().contains("A2AT_LLM_HISTORY_WINDOW"));
        assertTrue(error.getMessage().contains("100"));
    }

    @Test
    void rejectsSessionTotalSmallerThanPerProviderLimit() throws IOException {
        Path envFile = writeEnv("""
                A2AT_LLM_PROVIDER=openai
                A2AT_LLM_MODEL=gpt-4o-mini
                A2AT_LLM_API_KEY=sk-test
                A2AT_LLM_SESSION_MAX_TOTAL=50
                A2AT_LLM_SESSION_MAX_PER_PROVIDER=100
                """);

        LLMConfigError error = assertThrows(LLMConfigError.class, () -> LLMConfigLoader.load(envFile));

        assertTrue(error.getMessage().contains("A2AT_LLM_SESSION_MAX_TOTAL"));
        assertTrue(error.getMessage().contains("A2AT_LLM_SESSION_MAX_PER_PROVIDER"));
    }

    private Path writeEnv(String content) throws IOException {
        Path envFile = tempDir.resolve("llm.env");
        Files.writeString(envFile, content);
        return envFile;
    }
}
