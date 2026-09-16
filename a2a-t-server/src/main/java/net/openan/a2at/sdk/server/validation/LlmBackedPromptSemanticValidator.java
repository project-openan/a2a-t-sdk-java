package net.openan.a2at.sdk.server.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM-backed semantic validator aligned with the Python server-side compliance flow.
 *
 * <p>The LLM reports semantic failures as {@code {slot_name, code, facts}} entries; the human-readable message is
 * rendered from the code's message template, never taken from the LLM response. An error code outside the catalog is
 * mapped to the slot-domain fallback {@code slot.rule_violation} and logged as a warning.
 *
 * @since 2026-06
 */
public final class LlmBackedPromptSemanticValidator implements ServerPromptSemanticValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmBackedPromptSemanticValidator.class);

    /** Step name reported in {@code llm.response_invalid} when the LLM response violates the response contract. */
    private static final String SEMANTIC_VALIDATION_STEP = "semantic_validation";

    private static final String SLOT_VALIDATION_STAGE = "slot_validation";

    private static final String SLOT_CODE_DOMAIN = "slot.";

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final LLMClient llmClient;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final String systemPrompt;

    private final String userPrompt;

    /**
     * Creates an LLM-backed semantic validator.
     *
     * @param llmClient LLM client for structured generation
     * @param slotSchemaLoader loader for slot schemas
     * @param systemPrompt system prompt for the LLM
     * @param userPrompt user prompt template for the LLM
     */
    public LlmBackedPromptSemanticValidator(
            LLMClient llmClient, PromptSlotSchemaLoader slotSchemaLoader, String systemPrompt, String userPrompt) {
        this.llmClient = llmClient;
        this.slotSchemaLoader = slotSchemaLoader;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    @Override
    public void validate(String processedPromptText, ProcessedPromptMetadata metadata) {
        PromptSlotSchema slotSchema = slotSchemaLoader.loadSlotSchema(metadata.scenarioCode(), metadata.language());
        String payload = llmClient
                .structured(
                        toStructuredMessages(List.of(
                                new PromptMessage("system", systemPrompt),
                                new PromptMessage("user", buildUserPrompt(slotSchema, metadata.slots())))),
                        schema(),
                        null,
                        null)
                .content();
        validateResponse(payload, metadata.language());
    }

    private String buildUserPrompt(PromptSlotSchema slotSchema, Map<String, String> extractedSlots) {
        try {
            return userPrompt
                    + "\n\n{\n"
                    + "  \"slot_json_schema\": "
                    + OBJECT_MAPPER.writeValueAsString(slotSchema)
                    + ",\n"
                    + "  \"extracted_slots\": "
                    + OBJECT_MAPPER.writeValueAsString(extractedSlots)
                    + "\n}";
        } catch (JsonProcessingException error) {
            throw internalError(error);
        }
    }

    private static Map<String, Object> schema() {
        Map<String, Object> factsSchema = new LinkedHashMap<>();
        factsSchema.put("type", "object");
        factsSchema.put("additionalProperties", Map.of("type", "string"));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("required", List.of("slot_name", "code", "facts"));
        itemSchema.put(
                "properties",
                Map.of(
                        "slot_name", Map.of("type", "string"),
                        "code", Map.of("type", "string"),
                        "facts", factsSchema));

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

    private static List<Map<String, String>> toStructuredMessages(List<PromptMessage> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
    }

    private void validateResponse(String payload, String language) {
        Map<String, Object> response = parseResponse(payload, language);
        boolean passed = Boolean.TRUE.equals(response.get("passed"));
        List<?> errors = response.get("errors") instanceof List<?> list ? list : List.of();
        if (passed && errors.isEmpty()) {
            return;
        }

        SemanticError firstError = firstError(errors);
        if (firstError == null) {
            throw responseInvalid(language);
        }
        throw new PromptComplianceCheckException(
                resolveErrorCode(firstError.code()), language, firstError.facts(), SLOT_VALIDATION_STAGE);
    }

    private static Map<String, Object> parseResponse(String payload, String language) {
        try {
            Map<String, Object> response =
                    OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
            return Optional.ofNullable(response).orElseGet(Map::of);
        } catch (JsonProcessingException error) {
            throw responseInvalid(language);
        }
    }

    private static SemanticError firstError(List<?> errors) {
        for (Object item : errors) {
            if (!(item instanceof Map<?, ?> errorMap)
                    || !(errorMap.get("slot_name") instanceof String slotName)
                    || !(errorMap.get("code") instanceof String code)) {
                continue;
            }
            return new SemanticError(slotName, code, stringFacts(errorMap.get("facts")));
        }
        return null;
    }

    private static Map<String, String> stringFacts(Object factsValue) {
        if (!(factsValue instanceof Map<?, ?> facts)) {
            return Map.of();
        }
        Map<String, String> rendered = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : facts.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                rendered.put(key, value);
            }
        }
        return rendered;
    }

    private static ErrorCatalog resolveErrorCode(String code) {
        Optional<ErrorCatalog> entry = ErrorCatalog.byCode(code);
        if (entry.isPresent() && entry.get().getCode().startsWith(SLOT_CODE_DOMAIN)) {
            return entry.get();
        }
        LOGGER.warn(
                "Unknown semantic validation error code '{}'; falling back to '{}'.",
                code,
                ErrorCatalog.SLOT_RULE_VIOLATION.getCode());
        return ErrorCatalog.SLOT_RULE_VIOLATION;
    }

    private static PromptComplianceCheckException responseInvalid(String language) {
        return new PromptComplianceCheckException(
                ErrorCatalog.LLM_RESPONSE_INVALID,
                language,
                Map.of("step", SEMANTIC_VALIDATION_STEP),
                SLOT_VALIDATION_STAGE);
    }

    private static PromptComplianceCheckException internalError(JsonProcessingException cause) {
        LOGGER.warn("Failed to serialize slot schema for the semantic validation prompt.", cause);
        return new PromptComplianceCheckException(ErrorCatalog.INFRA_INTERNAL_ERROR, null, null, SLOT_VALIDATION_STAGE);
    }

    /** One error entry reported by the semantic validation LLM step. */
    private record SemanticError(String slotName, String code, Map<String, String> facts) {}
}
