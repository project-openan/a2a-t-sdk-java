package net.openan.a2at.sdk.prompt.analysis.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotValidationError;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared LLM-backed structured slot extractor.
 *
 * <p>The LLM reports each slot error as {@code {slot_name, code, facts}} from the closed {@code slot.*} code set; the
 * human-readable message is rendered by the SDK from the code's message template. Codes outside the {@code slot.}
 * domain are mapped to the {@code slot.rule_violation} fallback and logged as a warning.
 *
 * @since 2026-06
 */
public final class DefaultStructuredPromptSlotValueExtractor implements PromptSlotValueExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStructuredPromptSlotValueExtractor.class);

    private static final String SLOT_CODE_DOMAIN = "slot.";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ObjectWriter SLOT_SERIALIZER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .writerWithDefaultPrettyPrinter();

    private final LLMClient llmClient;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final String systemPrompt;

    private final String userPrompt;

    public DefaultStructuredPromptSlotValueExtractor(
            LLMClient llmClient, PromptSlotSchemaLoader slotSchemaLoader, String systemPrompt, String userPrompt) {
        this.llmClient = llmClient;
        this.slotSchemaLoader = slotSchemaLoader;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    @Override
    public StructuredSlotExtractionResult extractSlots(Object userInput, String scenarioCode, String language) {
        return doExtractSlots(userInput, scenarioCode, language, null);
    }

    @Override
    public StructuredSlotExtractionResult extractSlots(
            Object userInput, String scenarioCode, String language, Map<String, Object> dataSchema) {
        return doExtractSlots(userInput, scenarioCode, language, dataSchema);
    }

    private StructuredSlotExtractionResult doExtractSlots(
            Object userInput, String scenarioCode, String language, Map<String, Object> dataSchema) {
        PromptSlotSchema slotSchema = slotSchemaLoader.loadSlotSchema(scenarioCode, language);
        String payload = llmClient
                .structured(
                        toStructuredMessages(buildMessages(userInput, scenarioCode, language, slotSchema, dataSchema)),
                        buildSchema(slotSchema),
                        null,
                        null)
                .content();
        return parseExtractionResult(slotSchema, payload, language);
    }

    private List<PromptMessage> buildMessages(
            Object userInput,
            String scenarioCode,
            String language,
            PromptSlotSchema slotSchema,
            Map<String, Object> dataSchema) {
        String slotLines;
        try {
            slotLines = SLOT_SERIALIZER.writeValueAsString(slotSchema.slotDefinitions());
        } catch (JsonProcessingException error) {
            throw new A2ATError("Failed to serialize slot definitions.", error);
        }
        String content = userPrompt
                + "\n\n[scenario_code]\n"
                + scenarioCode
                + "\n\n[language]\n"
                + language
                + "\n\n[input]\n"
                + String.valueOf(userInput)
                + "\n\n[slots]\n"
                + slotLines;
        if (dataSchema != null && !dataSchema.isEmpty()) {
            content += "\n\n[data_schema]\n";
            try {
                content += OBJECT_MAPPER.writeValueAsString(dataSchema);
            } catch (JsonProcessingException e) {
                throw new A2ATError("Failed to serialize data schema to JSON.", e);
            }
        }
        return List.of(new PromptMessage("system", systemPrompt), new PromptMessage("user", content));
    }

    private Map<String, Object> buildSchema(PromptSlotSchema slotSchema) {
        List<String> slotNames = slotSchema.slotDefinitions().stream()
                .map(PromptSlotDefinition::name)
                .toList();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("slots", "slot_errors"));
        schema.put("slotNames", slotNames);
        return schema;
    }

    private static List<Map<String, String>> toStructuredMessages(List<PromptMessage> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
    }

    private StructuredSlotExtractionResult parseExtractionResult(
            PromptSlotSchema slotSchema, String payload, String language) {
        Map<String, Object> response = parseObject(payload);
        Map<String, String> normalized = normalizeSlots(response.get("slots"), slotSchema);
        List<StructuredSlotValidationError> slotErrors = normalizeSlotErrors(response.get("slot_errors"), language);
        return new StructuredSlotExtractionResult(Map.copyOf(normalized), List.copyOf(slotErrors));
    }

    private static Map<String, Object> parseObject(String payload) {
        try {
            Map<String, Object> response =
                    OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
            return response == null ? Map.of() : response;
        } catch (JsonProcessingException error) {
            throw new A2ATError("Structured LLM payload must be a JSON object.", error);
        }
    }

    private static Map<String, String> normalizeSlots(Object slotsValue, PromptSlotSchema slotSchema) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Object> rawSlots = slotsValue instanceof Map<?, ?> mapValue ? normalizeMap(mapValue) : Map.of();
        for (PromptSlotDefinition definition : slotSchema.slotDefinitions()) {
            values.put(definition.name(), normalizeSlotValue(rawSlots.get(definition.name())));
        }
        return values;
    }

    private static List<StructuredSlotValidationError> normalizeSlotErrors(Object errorsValue, String language) {
        if (!(errorsValue instanceof List<?> errors)) {
            return List.of();
        }
        List<StructuredSlotValidationError> normalized = new ArrayList<>();
        for (Object errorValue : errors) {
            if (!(errorValue instanceof Map<?, ?> errorMap)) {
                continue;
            }
            Map<String, Object> fields = normalizeMap(errorMap);
            String slotName = asString(fields.get("slot_name"));
            String code = asString(fields.get("code"));
            Map<String, String> facts = stringFacts(fields.get("facts"));
            ErrorCatalog entry = resolveCode(code);
            if (!entry.getCode().equals(code)) {
                LOGGER.atWarn()
                        .log("slot_extraction_unknown_code original_code={} fallback_code={}", code, entry.getCode());
                facts = Map.of("slot_label", slotName);
            } else if (!facts.containsKey("slot_label") && entry.hasFactParameter("slot_label")) {
                facts = withSlotLabel(facts, slotName);
            }
            normalized.add(new StructuredSlotValidationError(
                    slotName, entry.getCode(), ErrorMessages.render(entry, language, facts), facts));
        }
        return normalized;
    }

    /** Resolves one LLM-reported code to its catalog entry, falling back to {@code slot.rule_violation}. */
    private static ErrorCatalog resolveCode(String code) {
        Optional<ErrorCatalog> entry = ErrorCatalog.byCode(code);
        if (entry.isPresent() && entry.get().getCode().startsWith(SLOT_CODE_DOMAIN)) {
            return entry.get();
        }
        return ErrorCatalog.SLOT_RULE_VIOLATION;
    }

    private static Map<String, String> stringFacts(@Nullable Object factsValue) {
        if (!(factsValue instanceof Map<?, ?> rawFacts)) {
            return Map.of();
        }
        Map<String, String> facts = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawFacts.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String text) {
                facts.put(key, text);
            } else if (value instanceof Number || value instanceof Boolean) {
                facts.put(key, String.valueOf(value));
            }
        }
        return Map.copyOf(facts);
    }

    private static Map<String, String> withSlotLabel(Map<String, String> facts, String slotName) {
        Map<String, String> enriched = new LinkedHashMap<>(facts);
        enriched.put("slot_label", slotName);
        return Map.copyOf(enriched);
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }

    private static String normalizeSlotValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        return String.valueOf(value);
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : "";
    }
}
