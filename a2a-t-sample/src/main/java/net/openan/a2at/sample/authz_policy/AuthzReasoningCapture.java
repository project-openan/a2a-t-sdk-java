package net.openan.a2at.sample.authz_policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;

/**
 * Reasoning capture apparatus for the Authorization-T demo.
 *
 * <p>When enabled ({@code -Dauthz.reasoning=true}), it decorates the underlying {@link LLMClient} to:
 *
 * <ul>
 *   <li>append a short reasoning-instruction appendix to the {@code slot_extraction} and {@code content_validation}
 *       system prompts (identified by exact fingerprint match against the classpath {@code system.md} resources);
 *   <li>extend the {@code content_validation} output schema with an optional {@code reasoning} property (so the
 *       instruction does not contradict {@code additionalProperties:false});
 *   <li>best-effort parse the returned raw content for the {@code reasoning} field and record it, keyed by scenario
 *       label.
 * </ul>
 *
 * <p>Unmatched stages (negotiation and other extensions) are passed through untouched with no capture. Captures are
 * associated to scenarios through a {@link ThreadLocal} double-slot (label + entry list), which is correct because one
 * executor task thread runs the complete client→server flow for one scenario.
 *
 * @since 2026-08
 */
public final class AuthzReasoningCapture {

    private static final String SLOT_EXTRACTION_SYSTEM = "/prompt_resources/prompts/slot_extraction/zh-CN/system.md";
    private static final String CONTENT_VALIDATION_SYSTEM =
            "/prompt_resources/prompts/content_validation/zh-CN/system.md";

    private static final String REASONING_APPENDIX = "\n\n[reasoning_appendix]\n"
            + "除上述要求的 JSON 字段外，请额外输出一个 \"reasoning\" 字段（字符串），"
            + "简要说明本次提参/校验每个结论的依据与推理过程。"
            + "该字段仅供调用方分析使用，不会影响对其他字段的判定与使用。";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, StageCapture> registry = new ConcurrentHashMap<>();

    private final ThreadLocal<String> currentLabel = new ThreadLocal<>();

    private final ThreadLocal<List<CaptureEntry>> currentCaptures = new ThreadLocal<>();

    private String slotExtractionFingerprint;

    private String contentValidationFingerprint;

    /**
     * Resolves the reasoning-capture flag from the {@code authz.reasoning} system property.
     *
     * @return {@code true} when reasoning capture is enabled, {@code false} by default
     * @throws IllegalArgumentException if the property is set to a value other than {@code true} or {@code false}
     *     (case-insensitive)
     * @since 2026-08
     */
    public static boolean resolveReasoningFlag() {
        String raw = System.getProperty("authz.reasoning");
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Invalid authz.reasoning value: '" + raw + "'. Expected true or false (case-insensitive).");
    }

    /**
     * Wraps the provided client with a reasoning-capturing decorator.
     *
     * @param real underlying LLM client
     * @return decorated client, never {@code null}
     * @since 2026-08
     */
    public LLMClient wrap(LLMClient real) {
        return (messages, jsonSchema, temperature, maxTokens) -> {
            Stage stage = identifyStage(messages);
            if (stage == null) {
                return real.structured(messages, jsonSchema, temperature, maxTokens);
            }
            List<Map<String, String>> modifiedMessages = appendAppendix(messages);
            Map<String, Object> modifiedSchema = jsonSchema;
            if (stage == Stage.CONTENT_VALIDATION) {
                modifiedSchema = extendSchema(jsonSchema);
            }
            LLMResponse response = real.structured(modifiedMessages, modifiedSchema, temperature, maxTokens);
            List<CaptureEntry> entries = currentCaptures.get();
            if (entries != null) {
                entries.add(new CaptureEntry(stage, extractReasoning(response.content()), response.content()));
            }
            return response;
        };
    }

    /**
     * Begins capture for a scenario on the current thread.
     *
     * @param label scenario label used as the registry key
     * @since 2026-08
     */
    public void beginScenario(String label) {
        currentLabel.set(label);
        currentCaptures.set(new ArrayList<>());
    }

    /**
     * Ends capture for the current thread's scenario, merging the recorded stages into the registry (the last response
     * per stage wins across infra retries) and clearing the thread-local state.
     *
     * @since 2026-08
     */
    public void endScenario() {
        String label = currentLabel.get();
        List<CaptureEntry> entries = currentCaptures.get();
        if (label != null && entries != null) {
            registry.put(label, mergeByStage(entries));
        }
        currentLabel.remove();
        currentCaptures.remove();
    }

    /**
     * Returns the captured reasoning for a scenario, if any.
     *
     * @param label scenario label
     * @return the merged stage captures, or {@link Optional#empty()} when none was recorded
     * @since 2026-08
     */
    public Optional<StageCapture> capture(String label) {
        return Optional.ofNullable(registry.get(label));
    }

    /**
     * Merged per-stage reasoning capture for one scenario.
     *
     * @param clientReasoning reasoning from the slot_extraction stage, or {@code null}
     * @param serverReasoning reasoning from the content_validation stage, or {@code null}
     * @param clientRaw raw LLM response content from the slot_extraction stage, or {@code null}
     * @param serverRaw raw LLM response content from the content_validation stage, or {@code null}
     * @since 2026-08
     */
    public record StageCapture(String clientReasoning, String serverReasoning, String clientRaw, String serverRaw) {}

    private enum Stage {
        SLOT_EXTRACTION,
        CONTENT_VALIDATION
    }

    private record CaptureEntry(Stage stage, String reasoning, String content) {}

    private Stage identifyStage(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        String content = messages.get(0).get("content");
        if (content == null) {
            return null;
        }
        ensureLoaded();
        if (content.equals(slotExtractionFingerprint)) {
            return Stage.SLOT_EXTRACTION;
        }
        if (content.equals(contentValidationFingerprint)) {
            return Stage.CONTENT_VALIDATION;
        }
        return null;
    }

    private static List<Map<String, String>> appendAppendix(List<Map<String, String>> messages) {
        List<Map<String, String>> modified = new ArrayList<>(messages);
        Map<String, String> first = new LinkedHashMap<>(messages.get(0));
        first.put("content", first.get("content") + REASONING_APPENDIX);
        modified.set(0, first);
        return modified;
    }

    private static Map<String, Object> extendSchema(Map<String, Object> jsonSchema) {
        Map<String, Object> copy = new LinkedHashMap<>(jsonSchema);
        Object properties = jsonSchema.get("properties");
        Map<String, Object> propsCopy = properties instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        propsCopy.put("reasoning", Map.of("type", "string"));
        copy.put("properties", propsCopy);
        return copy;
    }

    private static String extractReasoning(String content) {
        if (content == null) {
            return null;
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(content, new TypeReference<>() {});
            Object reasoning = parsed.get("reasoning");
            return reasoning instanceof String text ? text : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static StageCapture mergeByStage(List<CaptureEntry> entries) {
        String clientReasoning = null;
        String serverReasoning = null;
        String clientRaw = null;
        String serverRaw = null;
        for (CaptureEntry entry : entries) {
            if (entry.stage == Stage.SLOT_EXTRACTION) {
                clientReasoning = entry.reasoning();
                clientRaw = entry.content();
            } else {
                serverReasoning = entry.reasoning();
                serverRaw = entry.content();
            }
        }
        return new StageCapture(clientReasoning, serverReasoning, clientRaw, serverRaw);
    }

    private void ensureLoaded() {
        if (slotExtractionFingerprint == null) {
            synchronized (this) {
                if (slotExtractionFingerprint == null) {
                    slotExtractionFingerprint = readResource(SLOT_EXTRACTION_SYSTEM);
                    contentValidationFingerprint = readResource(CONTENT_VALIDATION_SYSTEM);
                }
            }
        }
    }

    private static String readResource(String path) {
        try (InputStream in = AuthzReasoningCapture.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }
}
