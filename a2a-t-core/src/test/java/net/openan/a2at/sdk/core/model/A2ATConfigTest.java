package net.openan.a2at.sdk.core.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.openan.a2at.sdk.core.exception.ConfigFileNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link A2ATConfig}.
 *
 * <p>Tests cover the following scenarios:
 * <ul>
 *   <li>Loading unified SDK configuration from .env files</li>
 *   <li>Default values for missing configuration keys</li>
 *   <li>Error handling for missing configuration files</li>
 * </ul>
 *
 * @since 2026-06
 */
class A2ATConfigTest {

    /**
     * Verifies that {@link A2ATConfig#load(Path)} correctly builds a unified configuration
     * from a complete .env file with all keys specified.
     *
     * <p>Scenario: A .env file contains all configuration keys for prompt, LLM, and negotiation.
     * Expected result: All configuration values are correctly parsed and accessible via
     * {@link A2ATConfig#prompt()}, {@link A2ATConfig#llm()}, and {@link A2ATConfig#negotiation()}.
     *
     * @throws IOException if temp directory creation fails
     */
    @Test
    void should_buildUnifiedConfig_When_envFileContainsAllKeys() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-config");
        Path promptRoot = tempDir.resolve("prompt_resources");
        Files.createDirectories(promptRoot);
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=local_file
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=prompt_resources
                A2AT_LLM_PROVIDER=openai
                A2AT_LLM_MODEL=gpt-4.1
                A2AT_LLM_API_KEY=test-key
                A2AT_LLM_BASE_URL=https://api.openai.com/v1
                A2AT_LLM_HISTORY_WINDOW=12
                A2AT_LLM_MAX_TOKENS=1024
                A2AT_LLM_TEMPERATURE=0.3
                A2AT_LLM_TIMEOUT_SECONDS=15
                A2AT_LLM_SESSION_MAX_TOTAL=300
                A2AT_LLM_SESSION_MAX_PER_PROVIDER=100
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """);

        A2ATConfig config = A2ATConfig.load(envFile);

        assertEquals("zh-CN", config.prompt().language());
        assertEquals("local_file", config.prompt().sourceType());
        assertEquals("prompt_resources", config.prompt().localRootDir());
        assertEquals("openai", config.llm().provider());
        assertEquals("gpt-4.1", config.llm().model());
        assertEquals("test-key", config.llm().apiKey());
        assertEquals("https://api.openai.com/v1", config.llm().baseUrl());
        assertEquals(12, config.llm().historyWindow());
        assertEquals(1024, config.llm().maxTokens());
        assertEquals(0.3d, config.llm().temperature());
        assertEquals(15.0d, config.llm().timeoutSeconds());
        assertEquals(300, config.llm().sessionMaxTotal());
        assertEquals(100, config.llm().sessionMaxPerProvider());
        assertEquals("in_memory", config.negotiation().stateStoreType());
    }

    /**
     * Verifies that {@link A2ATConfig#load(Path)} applies default values for missing
     * configuration keys.
     *
     * <p>Scenario: A .env file contains only minimal required keys (LLM provider and negotiation).
     * Expected result: Default values are applied for prompt configuration (classpath source type,
     * current directory root).
     *
     * @throws IOException if temp directory creation fails
     */
    @Test
    void should_useClasspathDefaults_When_promptKeysAreMissing() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-config-default");
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LLM_PROVIDER=local_rule
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """);

        A2ATConfig config = A2ATConfig.load(envFile);

        assertEquals("classpath", config.prompt().sourceType());
        assertEquals(".", config.prompt().localRootDir());
    }

    /**
     * Verifies that {@link A2ATConfig#load(Path)} throws {@link ConfigFileNotFoundException}
     * when the specified file does not exist.
     *
     * <p>Scenario: Attempt to load configuration from a non-existent file path.
     * Expected result: ConfigFileNotFoundException is thrown.
     */
    @Test
    void should_throwConfigFileNotFoundException_When_envFileDoesNotExist() {
        Path missing = Path.of("build", "missing", "a2at.env");

        assertThrows(ConfigFileNotFoundException.class, () -> A2ATConfig.load(missing));
    }

    /**
     * Verifies that the repository's env.example file exists and optionally matches
     * an upstream template.
     *
     * <p>Scenario: Check for the existence of env.example in the repository root.
     * If an upstream template exists, verify they match (ignoring line ending differences).
     *
     * @throws IOException if file reading fails
     */
    @Test
    void should_matchUpstreamTemplate_When_envExampleExists() throws IOException {
        Path repoEnvExample = Path.of("..", "env.example").normalize();
        Path upstreamEnvExample = Path.of("..", ".upstream-src", "package_data", "env.example").normalize();

        assertTrue(Files.exists(repoEnvExample), "repo root env.example should exist");
        if (Files.exists(upstreamEnvExample)) {
            assertEquals(Files.readString(upstreamEnvExample).replace("\r\n", "\n"),
                    Files.readString(repoEnvExample).replace("\r\n", "\n"));
        }
    }

}
