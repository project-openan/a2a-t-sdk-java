package net.openan.a2at.sample.service_recovery.shared.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.service_recovery.shared.error.ValueErrorException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMResponse;

/**
 * Deterministic LLM client used by the sample when no real API key is available.
 *
 * <p>Unlike a call-order replay, this client classifies every structured call by its request signature and returns the
 * matching canned response, so the sample flow stays deterministic no matter how many LLM steps run or in which order.
 * The service-recovery flow makes exactly four LLM calls (two slot extractions on the client, two content validations
 * on the server):
 *
 * <ul>
 *   <li>slot extraction — the JSON schema carries a {@code slotNames} key;
 *   <li>content validation — the schema requires {@code semantic_verdict}.
 * </ul>
 *
 * <p>Any other structured call fails loudly instead of returning an unrelated canned response — scenario recognition
 * and task-prompt compliance validation never happen in this sample (the template URI is fixed and the server only
 * calls {@code validateAndFillingNotificationData}). The canned responses are loaded from {@code mock_responses} JSON
 * resources on the classpath and mirror what a real LLM returned for the bundled service-recovery resources, so the
 * end-to-end sample flow runs without any external LLM service.
 *
 * @since 2026-08
 */
public final class SampleMockLLMClient implements LLMClient {

    private static final String DEFAULT_RESOURCE_ROOT = "sample/service-recovery/mock_responses";

    private static final String DEFAULT_LANGUAGE = "zh-CN";

    private static volatile String resourceRoot = DEFAULT_RESOURCE_ROOT;

    private static volatile String language = DEFAULT_LANGUAGE;

    /**
     * Creates a mock LLM client. The supplied config is ignored because responses are canned.
     *
     * @param config resolved LLM provider config (unused)
     */
    public SampleMockLLMClient(LLMClientConfig config) {
        // Responses are canned; the config is not needed.
    }

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
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema, Double temperature, Integer maxTokens) {
        String resource = responseResource(jsonSchema);
        return new LLMResponse(
                loadResource(resource),
                "mock-llm",
                Map.of("prompt_tokens", 0, "completion_tokens", 0, "total_tokens", 0),
                Map.of());
    }

    static String responseResource(Map<String, Object> jsonSchema) {
        if (jsonSchema != null && jsonSchema.containsKey("slotNames")) {
            return "slot_extraction.json";
        }
        Object required = jsonSchema == null ? null : jsonSchema.get("required");
        if (required instanceof List<?> requiredNames && requiredNames.contains("semantic_verdict")) {
            return "content_validation.json";
        }
        throw new ValueErrorException("Mock LLM cannot classify the structured call: " + jsonSchema);
    }

    private static String loadResource(String resourcePath) {
        String path = resourceRoot + "/" + language + "/" + resourcePath;
        try (InputStream inputStream =
                SampleMockLLMClient.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new ValueErrorException("Mock LLM response resource not found: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ValueErrorException("Failed to read mock LLM response resource: " + path, exception);
        }
    }
}
