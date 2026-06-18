package net.openan.a2at.sdk.server.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotJsonSchemaLoader;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;

/**
 * LLM-backed semantic validator aligned with the Python server-side compliance flow.
 *
 * @since 2026-06
 */
public final class LlmBackedPromptSemanticValidator implements ServerPromptSemanticValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LLMClient llmClient;

    private final PromptSlotJsonSchemaLoader slotJsonSchemaLoader;

    private final String systemPrompt;

    private final String userPrompt;

    /**
     * Creates an LLM-backed semantic validator.
     *
     * @param llmClient LLM client for structured generation
     * @param slotJsonSchemaLoader loader for slot JSON schemas
     * @param systemPrompt system prompt for the LLM
     * @param userPrompt user prompt template for the LLM
     */
    public LlmBackedPromptSemanticValidator(
            LLMClient llmClient,
            PromptSlotJsonSchemaLoader slotJsonSchemaLoader,
            String systemPrompt,
            String userPrompt) {
        this.llmClient = llmClient;
        this.slotJsonSchemaLoader = slotJsonSchemaLoader;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    @Override
    public void validate(String processedPromptText, ProcessedPromptMetadata metadata) {
        Map<String, Object> slotJsonSchema =
                slotJsonSchemaLoader.loadSlotJsonSchema(metadata.scenarioCode(), metadata.language());
        String payload = llmClient
                .structured(new StructuredGenerationRequest(
                        List.of(
                                new PromptMessage("system", systemPrompt),
                                new PromptMessage("user", buildUserPrompt(slotJsonSchema, metadata.slots()))),
                        schema()))
                .content();
        validateResponse(payload);
    }

    private String buildUserPrompt(Map<String, Object> slotJsonSchema, Map<String, String> extractedSlots) {
        return userPrompt
                + "\n\n{\n"
                + "  \"slot_json_schema\": "
                + toJsonObject(slotJsonSchema)
                + ",\n"
                + "  \"extracted_slots\": "
                + toJsonObject(new LinkedHashMap<>(extractedSlots))
                + "\n}";
    }

    private static Map<String, Object> schema() {
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("required", List.of("slot_name", "code", "message"));
        itemSchema.put(
                "properties",
                Map.of(
                        "slot_name", Map.of("type", "string"),
                        "code", Map.of("type", "string"),
                        "message", Map.of("type", "string")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("passed", "errors"));
        schema.put(
                "properties",
                Map.of(
                        "passed", Map.of("type", "boolean"),
                        "errors", Map.of("type", "array", "items", itemSchema)));
        return schema;
    }

    private void validateResponse(String payload) {
        Map<String, Object> response = parseResponse(payload);
        Object passedValue = response.get("passed");
        Object errorsValue = response.get("errors");
        if (Boolean.TRUE.equals(passedValue) && errorsValue instanceof List<?>) {
            return;
        }

        String message = extractFirstMessage(errorsValue);
        throw new PromptComplianceCheckException(
                "slot_validation_error",
                message == null || message.isBlank() ? "Slot semantic validation failed." : message,
                "slot_validation");
    }

    private static Map<String, Object> parseResponse(String payload) {
        try {
            Map<String, Object> response =
                    OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
            return response == null ? Map.of() : response;
        } catch (Exception error) {
            throw new PromptComplianceCheckException(
                    "slot_validation_error", "semantic validation returned invalid JSON", "slot_validation");
        }
    }

    private static String extractFirstMessage(Object errorsValue) {
        if (!(errorsValue instanceof List<?> errors) || errors.isEmpty()) {
            return null;
        }
        Object firstError = errors.get(0);
        if (!(firstError instanceof Map<?, ?> errorMap)) {
            return null;
        }
        Object message = errorMap.get("message");
        return message instanceof String text ? text : null;
    }

    private static String toJsonObject(Map<String, ?> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(escape(entry.getKey())).append('"').append(':');
            builder.append(toJsonValue(entry.getValue()));
            first = false;
        }
        builder.append('}');
        return builder.toString();
    }

    private static String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return '"' + escape(text) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            mapValue.forEach((key, item) -> normalized.put(String.valueOf(key), item));
            return toJsonObject(normalized);
        }
        if (value instanceof List<?> listValue) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < listValue.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(toJsonValue(listValue.get(index)));
            }
            builder.append(']');
            return builder.toString();
        }
        return '"' + escape(String.valueOf(value)) + '"';
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
