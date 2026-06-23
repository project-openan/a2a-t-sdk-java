package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ConfigFileNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DotEnvConfigSource}.
 *
 * <p>Tests cover the following scenarios:
 * <ul>
 *   <li>Reading valid key/value pairs from .env files</li>
 *   <li>Skipping comments and empty lines</li>
 *   <li>Whitespace trimming around keys and values</li>
 *   <li>Error handling for missing files</li>
 *   <li>Maximum entries limit enforcement</li>
 * </ul>
 *
 * @since 2026-06
 */
class DotEnvConfigSourceTest {

    /**
     * Verifies that {@link DotEnvConfigSource#load(Path)} correctly reads non-empty
     * key/value pairs from a .env file.
     *
     * <p>Scenario: A .env file contains valid entries and an empty value entry.
     * Expected result: Only non-empty key/value pairs are returned; empty values are skipped.
     *
     * @throws IOException if temp file creation fails
     */
    @Test
    void should_returnNonEmptyKeyValuePairs_When_envFileContainsValidEntries() throws IOException {
        Path envFile = Files.createTempFile("a2at-dotenv", ".env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                EMPTY_VALUE=
                A2AT_LLM_MODEL=gpt-4.1
                """);

        Map<String, String> values = DotEnvConfigSource.load(envFile);

        assertEquals(Map.of("A2AT_LANGUAGE", "zh-CN", "A2AT_LLM_MODEL", "gpt-4.1"), values);
    }

    /**
     * Verifies that {@link DotEnvConfigSource#load(Path)} skips comment lines (starting with #)
     * and blank lines.
     *
     * <p>Scenario: A .env file contains comments, blank lines, and valid entries.
     * Expected result: Only valid entries are returned; comments and blank lines are ignored.
     *
     * @throws IOException if temp file creation fails
     */
    @Test
    void should_skipCommentsAndEmptyLines_When_envFileContainsCommentsAndBlanks() throws IOException {
        Path envFile = Files.createTempFile("a2at-dotenv-comments", ".env");
        Files.writeString(
                envFile,
                """
                # This is a comment
                A2AT_LANGUAGE=en-US

                A2AT_LLM_PROVIDER=openai
                # Another comment
                """);

        Map<String, String> values = DotEnvConfigSource.load(envFile);

        assertEquals(Map.of("A2AT_LANGUAGE", "en-US", "A2AT_LLM_PROVIDER", "openai"), values);
    }

    /**
     * Verifies that {@link DotEnvConfigSource#load(Path)} trims whitespace around keys and values.
     *
     * <p>Scenario: A .env file contains entries with extra whitespace around keys and values.
     * Expected result: Whitespace is trimmed from both keys and values.
     *
     * @throws IOException if temp file creation fails
     */
    @Test
    void should_trimWhitespace_When_keysAndValuesHaveExtraSpaces() throws IOException {
        Path envFile = Files.createTempFile("a2at-dotenv-whitespace", ".env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE   =   zh-CN
                A2AT_LLM_MODEL = gpt-4.1
                """);

        Map<String, String> values = DotEnvConfigSource.load(envFile);

        assertEquals(Map.of("A2AT_LANGUAGE", "zh-CN", "A2AT_LLM_MODEL", "gpt-4.1"), values);
    }

    /**
     * Verifies that {@link DotEnvConfigSource#load(Path)} throws {@link ConfigFileNotFoundException}
     * when the specified file does not exist.
     *
     * <p>Scenario: Attempt to load a non-existent file path.
     * Expected result: ConfigFileNotFoundException is thrown.
     */
    @Test
    void should_throwConfigFileNotFoundException_When_fileDoesNotExist() {
        assertThrows(
                ConfigFileNotFoundException.class,
                () -> DotEnvConfigSource.load(Path.of("build", "missing", "config.env")));
    }

    /**
     * Verifies that {@link DotEnvConfigSource#load(Path)} throws {@link SdkException}
     * when the number of entries exceeds the maximum limit (200).
     *
     * <p>Scenario: A .env file contains more than 200 valid entries.
     * Expected result: SdkException is thrown with message indicating the limit was exceeded.
     *
     * @throws IOException if temp file creation fails
     */
    @Test
    void should_throwSdkException_When_entriesExceedMaxLimit() throws IOException {
        Path envFile = Files.createTempFile("a2at-dotenv-max", ".env");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 201; i++) {
            content.append("A2AT_KEY_").append(i).append("=value").append(i).append("\n");
        }
        Files.writeString(envFile, content.toString());

        SdkException exception = assertThrows(SdkException.class, () -> DotEnvConfigSource.load(envFile));

        assertEquals("Config file exceeds maximum allowed entries: 200", exception.getMessage());
    }
}