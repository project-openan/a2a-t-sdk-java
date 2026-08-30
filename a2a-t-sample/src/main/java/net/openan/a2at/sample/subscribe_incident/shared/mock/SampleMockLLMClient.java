package net.openan.a2at.sample.subscribe_incident.shared.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sample.subscribe_incident.shared.error.ValueErrorException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMResponse;

/**
 * Deterministic LLM client used by the sample when no real API key is available.
 *
 * <p>Returns canned structured responses for the three SDK LLM steps, in call order: scenario recognition, slot
 * extraction, then semantic validation. The canned responses are loaded from {@code mock_responses} JSON resources on
 * the classpath and mirror what a real LLM would return for the bundled subscribe-incident templates, so the end-to-end
 * sample flow runs without any external LLM service. Each process keeps its own call cursor, which matches the separate
 * client and server runtimes used by the sample e2e flow.
 *
 * @since 2026-08
 */
public final class SampleMockLLMClient implements LLMClient {

    private static final String DEFAULT_RESOURCE_ROOT = "sample/subscribe-incident/mock_responses";

    private static final String DEFAULT_LANGUAGE = "zh-CN";

    private static volatile String resourceRoot = DEFAULT_RESOURCE_ROOT;

    private static volatile String language = DEFAULT_LANGUAGE;

    private static volatile List<String> responses;

    private final AtomicInteger callIndex = new AtomicInteger();

    /**
     * Configures the classpath resource root and language used to load canned responses. Called by
     * {@link SampleMockLlmInstaller} before the mock client is first used.
     *
     * @param resourceRoot classpath directory containing {@code <language>} subdirectories
     * @param language locale identifier used to select the response files
     */
    public static synchronized void configure(String resourceRoot, String language) {
        SampleMockLLMClient.resourceRoot =
                resourceRoot == null || resourceRoot.isBlank() ? DEFAULT_RESOURCE_ROOT : resourceRoot;
        SampleMockLLMClient.language = language == null || language.isBlank() ? DEFAULT_LANGUAGE : language.trim();
        SampleMockLLMClient.responses = null;
    }

    /**
     * Creates a mock LLM client. The supplied config is ignored because responses are canned.
     *
     * @param config resolved LLM provider config (unused)
     */
    public SampleMockLLMClient(LLMClientConfig config) {
        // Responses are canned; the config is not needed.
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema, Double temperature, Integer maxTokens) {
        List<String> mockResponses = responses();
        String content = mockResponses.get(Math.floorMod(callIndex.getAndIncrement(), mockResponses.size()));
        return new LLMResponse(
                content, "mock-llm", Map.of("prompt_tokens", 0, "completion_tokens", 0, "total_tokens", 0), Map.of());
    }

    private static List<String> responses() {
        List<String> current = responses;
        if (current == null) {
            synchronized (SampleMockLLMClient.class) {
                current = responses;
                if (current == null) {
                    current = loadResponses();
                    responses = current;
                }
            }
        }
        return current;
    }

    private static List<String> loadResponses() {
        String directory = resourceRoot + "/" + language;
        return List.of(
                loadResource(directory + "/scenario_recognition.json"),
                loadResource(directory + "/slot_extraction.json"),
                loadResource(directory + "/semantic_validation.json"));
    }

    private static String loadResource(String resourcePath) {
        try (InputStream inputStream =
                SampleMockLLMClient.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new ValueErrorException("Mock LLM response resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ValueErrorException("Failed to read mock LLM response resource: " + resourcePath, exception);
        }
    }
}
