package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.openan.a2at.sdk.core.exception.ConfigFileNotFoundException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link A2ATConfig}.
 *
 * <p>Tests cover the following scenarios:
 *
 * <ul>
 *   <li>Loading unified SDK configuration from .env files
 *   <li>Default values for missing configuration keys
 *   <li>Error handling for missing configuration files
 * </ul>
 *
 * @since 2026-06
 */
class A2ATConfigTest {

    /**
     * Verifies that {@link A2ATConfig#load(Path)} correctly builds a unified configuration from a complete .env file
     * with all keys specified.
     *
     * <p>Scenario: A .env file contains all configuration keys for prompt, LLM, and negotiation. Expected result: All
     * configuration values are correctly parsed and accessible via {@link A2ATConfig#prompt()},
     * {@link A2ATConfig#llm()}, and {@link A2ATConfig#negotiation()}.
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
     * Verifies that {@link A2ATConfig#load(Path)} applies default values for missing configuration keys.
     *
     * <p>Scenario: A .env file contains only minimal required keys (LLM provider and negotiation). Expected result:
     * Default values are applied for prompt configuration (classpath source type, unset local root directory).
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
                A2AT_LLM_PROVIDER=openai
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """);

        A2ATConfig config = A2ATConfig.load(envFile);

        assertEquals("classpath", config.prompt().sourceType());
        assertNull(config.prompt().localRootDir());
    }

    /**
     * Verifies that {@link A2ATConfig#load(Path)} throws {@link ConfigFileNotFoundException} when the specified file
     * does not exist.
     *
     * <p>Scenario: Attempt to load configuration from a non-existent file path. Expected result:
     * ConfigFileNotFoundException is thrown.
     */
    @Test
    void should_throwConfigFileNotFoundException_When_envFileDoesNotExist() {
        Path missing = Path.of("build", "missing", "a2at.env");

        assertThrows(ConfigFileNotFoundException.class, () -> A2ATConfig.load(missing));
    }

    /**
     * Verifies that {@link A2ATConfig#resolvePromptResourceLocalRootDir(A2ATConfig, Path)} resolves a relative local
     * root directory to an absolute normalized path against the environment file parent.
     *
     * <p>Scenario: A .env file configures a relative local root directory. Expected result: the returned config carries
     * the local root resolved to an absolute normalized path relative to the .env file parent.
     *
     * @throws IOException if temp directory creation fails
     */
    @Test
    void should_resolveRelativeLocalRoot_When_resolvingPromptResourceLocalRootDir() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-resolve-relative");
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_PROMPT_SOURCE_TYPE=local_file
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=prompt_resources
                A2AT_LLM_PROVIDER=openai
                """);

        A2ATConfig resolved = A2ATConfig.resolvePromptResourceLocalRootDir(A2ATConfig.load(envFile), envFile);

        assertEquals(
                tempDir.resolve("prompt_resources").toAbsolutePath().normalize().toString(),
                resolved.prompt().localRootDir());
    }

    /**
     * Verifies that {@link A2ATConfig#resolvePromptResourceLocalRootDir(A2ATConfig, Path)} normalizes an
     * already-absolute local root directory without re-resolving it against the environment file parent.
     *
     * <p>Scenario: A .env file configures an absolute local root directory. Expected result: the returned config
     * carries the normalized absolute path unchanged.
     *
     * @throws IOException if temp directory creation fails
     */
    @Test
    void should_normalizeAbsoluteLocalRoot_When_resolvingPromptResourceLocalRootDir() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-resolve-absolute");
        Path absoluteRoot = tempDir.resolve(".").resolve("prompt_resources").toAbsolutePath();
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                "A2AT_PROMPT_SOURCE_TYPE=local_file\n"
                        + "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=" + absoluteRoot + "\n"
                        + "A2AT_LLM_PROVIDER=openai\n");

        A2ATConfig resolved = A2ATConfig.resolvePromptResourceLocalRootDir(A2ATConfig.load(envFile), envFile);

        assertEquals(absoluteRoot.normalize().toString(), resolved.prompt().localRootDir());
    }

    /**
     * Verifies that {@link A2ATConfig#resolvePromptResourceLocalRootDir(A2ATConfig, Path)} preserves an unset (null)
     * local root directory instead of throwing.
     *
     * <p>Scenario: A .env file does not configure a local root directory (classpath source). Expected result: the
     * returned config keeps the local root as null.
     *
     * @throws IOException if temp directory creation fails
     */
    @Test
    void should_preserveNullLocalRoot_When_resolvingPromptResourceLocalRootDir() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-resolve-null");
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(envFile, """
                A2AT_LLM_PROVIDER=openai
                """);

        A2ATConfig resolved = A2ATConfig.resolvePromptResourceLocalRootDir(A2ATConfig.load(envFile), envFile);

        assertNull(resolved.prompt().localRootDir());
    }

    /**
     * Verifies that {@link A2ATConfig#resolvePromptResourceLocalRootDir(A2ATConfig, Path)} preserves a blank local root
     * directory instead of resolving it to a legal absolute path.
     *
     * <p>Scenario: A config carries a blank local root directory. Expected result: the method returns the original
     * config unchanged, treating a blank root like an unset root (matching the downstream {@code PromptResourceAccess}
     * and {@code PromptTemplateCatalog} "blank == unset" convention).
     *
     * @throws IOException if temp directory creation fails
     */
    @Test
    void should_preserveBlankLocalRoot_When_resolvingPromptResourceLocalRootDir() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-resolve-blank");
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(envFile, "A2AT_LLM_PROVIDER=openai\n");

        A2ATConfig loaded = A2ATConfig.load(envFile);
        A2ATConfig config = new A2ATConfig(
                new PromptRuntimeConfig(
                        loaded.prompt().language(), loaded.prompt().sourceType(), "   "),
                loaded.llm(),
                loaded.inputLimits(),
                loaded.negotiation(),
                loaded.promptCompliance());

        A2ATConfig resolved = A2ATConfig.resolvePromptResourceLocalRootDir(config, envFile);

        assertSame(config, resolved);
        assertEquals("   ", resolved.prompt().localRootDir());
    }

    /**
     * Verifies that {@link A2ATConfig#resolvePromptResourceLocalRootDir(A2ATConfig, Path)} does not throw when the
     * {@code envPath} is a single-segment relative path with no parent, resolving the relative local root against the
     * current working directory instead of a missing parent.
     *
     * <p>Scenario: A single-segment relative {@code envPath} (e.g. {@code Path.of(".env")}) has no parent. Expected
     * result: the returned config carries the local root resolved against the current working directory.
     *
     * @throws IOException if temp directory creation fails
     */
    @Test
    void should_resolveRelativeLocalRootAgainstCwd_When_envPathIsSingleSegment() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-resolve-single-segment");
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(envFile, "A2AT_LLM_PROVIDER=openai\n");

        A2ATConfig loaded = A2ATConfig.load(envFile);
        A2ATConfig config = new A2ATConfig(
                new PromptRuntimeConfig(loaded.prompt().language(), "local_file", "prompt_resources"),
                loaded.llm(),
                loaded.inputLimits(),
                loaded.negotiation(),
                loaded.promptCompliance());

        A2ATConfig resolved = A2ATConfig.resolvePromptResourceLocalRootDir(config, Path.of(".env"));

        assertEquals(
                Path.of("")
                        .toAbsolutePath()
                        .normalize()
                        .resolve("prompt_resources")
                        .normalize()
                        .toString(),
                resolved.prompt().localRootDir());
    }

    /**
     * Verifies that the repository's env.example file exists and optionally matches an upstream template.
     *
     * <p>Scenario: Check for the existence of env.example in the repository root. If an upstream template exists,
     * verify they match (ignoring line ending differences).
     *
     * @throws IOException if file reading fails
     */
    @Test
    void should_matchUpstreamTemplate_When_envExampleExists() throws IOException {
        Path repoEnvExample = Path.of("..", "env.example").normalize();
        Path upstreamEnvExample =
                Path.of("..", ".upstream-src", "package_data", "env.example").normalize();

        assertTrue(Files.exists(repoEnvExample), "repo root env.example should exist");
        if (Files.exists(upstreamEnvExample)) {
            assertEquals(
                    Files.readString(upstreamEnvExample).replace("\r\n", "\n"),
                    Files.readString(repoEnvExample).replace("\r\n", "\n"));
        }
    }
}
