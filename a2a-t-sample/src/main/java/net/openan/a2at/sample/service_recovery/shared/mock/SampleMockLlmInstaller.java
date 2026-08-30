package net.openan.a2at.sample.service_recovery.shared.mock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sample.service_recovery.shared.error.ValueErrorException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientFactory;

/**
 * Installs a mock LLM mode for the sample when no real LLM API key is available.
 *
 * <p>When {@code A2AT_LLM_API_KEY} is missing, blank, or a placeholder template such as {@code ${your_api_key}}, the
 * sample installs {@link SampleMockLLMClient} as the provider used by the SDK and rewrites the environment file with a
 * non-empty placeholder key. The mock client returns canned structured responses loaded from the specified classpath
 * resource root, so the end-to-end sample flow runs deterministically on CI environments that have no LLM API key. When
 * a real API key is present, the original environment file is used unchanged and no mock is installed.
 *
 * @since 2026-08
 */
public final class SampleMockLlmInstaller {

    private static final String API_KEY_VAR = "A2AT_LLM_API_KEY";

    private static final String PROVIDER_VAR = "A2AT_LLM_PROVIDER";

    private static final String MODEL_VAR = "A2AT_LLM_MODEL";

    private static final String BASE_URL_VAR = "A2AT_LLM_BASE_URL";

    private static final String LANGUAGE_VAR = "A2AT_LANGUAGE";

    private static final String OPENAI_PROVIDER = "openai";

    private static final String MOCK_MODEL = "mock-model";

    private static final String MOCK_API_KEY = "mock-key-not-real";

    private static final String MOCK_BASE_URL = "http://localhost:0";

    private static volatile boolean mockInstalled;

    private SampleMockLlmInstaller() {}

    /**
     * Returns whether the sample should fall back to the mock LLM provider.
     *
     * <p>The mock fallback is needed when no usable real LLM configuration is present: a missing, blank, or placeholder
     * {@code A2AT_LLM_API_KEY}, or a missing/blank {@code A2AT_LLM_MODEL} or {@code A2AT_LLM_BASE_URL}. When the
     * environment file lacks any of these, the SDK's {@code LLMConfigLoader} would fail at startup, so the mock is
     * installed instead.
     *
     * @param envPath sample environment file path
     * @return {@code true} when no usable real LLM configuration is present
     */
    public static boolean isMockNeeded(Path envPath) {
        Map<String, String> values = readEnv(envPath);
        String apiKey = values.get(API_KEY_VAR);
        String model = values.get(MODEL_VAR);
        String baseUrl = values.get(BASE_URL_VAR);
        boolean keyMissing = apiKey == null || apiKey.isBlank() || apiKey.contains("${");
        boolean modelMissing = model == null || model.isBlank();
        boolean baseUrlMissing = baseUrl == null || baseUrl.isBlank();
        return keyMissing || modelMissing || baseUrlMissing;
    }

    /**
     * Installs the logging-aware LLM client wrapper as the {@code openai} provider.
     *
     * <p>This method must be called once at startup, before any {@code A2ATClient} or {@code A2ATServer} is
     * constructed, so that every LLM-structured call is logged. The wrapper delegates to the real {@code OpenAIClient}
     * when {@code mockEnabled} is false, or to {@link SampleMockLLMClient} when it is true.
     *
     * @param mockEnabled whether the delegate should be the mock client
     * @param roleLabel log role label (for example {@code client} or {@code server})
     */
    public static synchronized void installLlmLogger(boolean mockEnabled, String roleLabel) {
        if (mockInstalled) {
            return;
        }
        SampleLoggingLLMClient.configure(mockEnabled, roleLabel, System.out::println);
        try {
            Field clientsField = LLMClientFactory.class.getDeclaredField("CLIENTS");
            clientsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Class<? extends LLMClient>> clients =
                    (Map<String, Class<? extends LLMClient>>) clientsField.get(null);
            clients.put(OPENAI_PROVIDER, SampleLoggingLLMClient.class);
            mockInstalled = true;
        } catch (ReflectiveOperationException exception) {
            throw new ValueErrorException("Failed to install logging LLM client", exception);
        }
    }

    /**
     * Resolves the environment file path that should be used for the current run.
     *
     * <p>When a mock fallback is needed, the mock client is configured with the supplied classpath resource root (and
     * the language read from the env file), then a temporary environment file is written next to the original file (so
     * relative prompt-resource paths keep resolving correctly) with a non-empty placeholder API key. Otherwise the
     * original path is returned unchanged.
     *
     * @param envPath sample environment file path
     * @param mockResourceRoot classpath root for mock LLM response JSON files
     * @return the environment file path to use for this run
     */
    public static Path resolveEnvPath(Path envPath, String mockResourceRoot) {
        if (envPath == null || !isMockNeeded(envPath)) {
            return envPath;
        }
        Map<String, String> env = readEnv(envPath);
        String language = env.get(LANGUAGE_VAR);
        SampleMockLLMClient.configure(mockResourceRoot, language);
        return writeMockEnvFile(envPath);
    }

    private static String stripBom(String text) {
        return text != null && text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static Map<String, String> readEnv(Path envPath) {
        if (envPath == null || !Files.exists(envPath)) {
            return Map.of();
        }
        try {
            Map<String, String> values = new LinkedHashMap<>();
            for (String rawLine : Files.readAllLines(envPath)) {
                String line = stripBom(rawLine.trim());
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int separatorIndex = line.indexOf('=');
                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    values.put(key, value);
                }
            }
            return values;
        } catch (IOException exception) {
            throw new ValueErrorException("Failed to read env file for mock LLM fallback: " + envPath, exception);
        }
    }

    private static Path writeMockEnvFile(Path originalEnvPath) {
        Map<String, String> values = new LinkedHashMap<>(readEnv(originalEnvPath));
        values.putIfAbsent(PROVIDER_VAR, OPENAI_PROVIDER);
        values.putIfAbsent(MODEL_VAR, MOCK_MODEL);
        values.put(API_KEY_VAR, MOCK_API_KEY);
        values.putIfAbsent(BASE_URL_VAR, MOCK_BASE_URL);
        Path parent = originalEnvPath.toAbsolutePath().getParent();
        try {
            Path tempFile = Files.createTempFile(parent, "a2a-t-sample-mock-", ".env");
            StringBuilder content = new StringBuilder();
            content.append("# Mock LLM environment for sample runs without a real API key.\n");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                content.append(entry.getKey())
                        .append('=')
                        .append(entry.getValue())
                        .append('\n');
            }
            Files.writeString(tempFile, content.toString(), StandardCharsets.UTF_8);
            return tempFile;
        } catch (IOException exception) {
            throw new ValueErrorException("Failed to create mock LLM environment file", exception);
        }
    }
}
