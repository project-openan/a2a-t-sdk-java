package net.openan.a2at.sdk.prompt.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.json.JacksonJsonValueParser;
import net.openan.a2at.sdk.core.json.JsonValueParser;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.SemanticValidator;
import net.openan.a2at.sdk.core.validation.ValidationResult;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMError;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default LLM-backed semantic validator that delegates to a structured LLM call for content validation and parameter
 * extraction.
 *
 * <p>The LLM reports each error as {@code {slot_name, code, facts}} from the closed {@code content.*} code set; the
 * human-readable message is rendered by the SDK from the code's message template. Codes outside the {@code content.}
 * domain are mapped to the {@code content.rule_violation} fallback and logged as a warning.
 *
 * <p>The content_validation prompt resources are internal LLM instructions. Like the negotiation semantic validation
 * prompts, they are always loaded from the classpath regardless of the configured prompt source type — the local
 * resource root only overrides business resources (templates, slots, scenarios).
 *
 * @since 2026-08
 */
final class DefaultSemanticValidator implements SemanticValidator<TemplateUri> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSemanticValidator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PROMPT_RESOURCE_ROOT = "prompt_resources/prompts/content_validation/";

    private static final String CONTENT_CODE_DOMAIN = "content.";

    private static final String SEMANTIC_VALIDATION_STEP = "semantic_validation";

    private final LLMClient llmClient;

    private final String language;

    private final String systemPrompt;

    private final String userPromptTemplate;

    private final JsonValueParser jsonValueParser;

    /**
     * Creates a semantic validator backed by the given LLM client and prompt resources for the specified language.
     *
     * @param llmClient LLM client for structured calls; may be {@code null}, in which case {@link #validate} fails with
     *     {@code ContentValidationException} carrying {@code llm.not_configured}; there is no late injection point
     * @param language language code for prompt resource loading and message rendering
     * @throws ResourceNotFoundException if the content_validation prompt resources of the given language are missing on
     *     the classpath
     */
    DefaultSemanticValidator(@Nullable LLMClient llmClient, @NonNull String language) {
        this(llmClient, language, new JacksonJsonValueParser());
    }

    /**
     * Creates a semantic validator backed by the given LLM client, prompt resources for the specified language and a
     * shared JSON value parser abstraction.
     *
     * @param llmClient LLM client for structured calls; may be {@code null}, in which case {@link #validate} fails with
     *     {@code ContentValidationException} carrying {@code llm.not_configured}; there is no late injection point
     * @param language language code for prompt resource loading and message rendering
     * @param jsonValueParser shared JSON value parser abstraction
     * @throws ResourceNotFoundException if the content_validation prompt resources of the given language are missing on
     *     the classpath
     */
    DefaultSemanticValidator(
            @Nullable LLMClient llmClient, @NonNull String language, @NonNull JsonValueParser jsonValueParser) {
        this.llmClient = llmClient;
        this.language = Objects.requireNonNull(language, "language");
        this.systemPrompt = loadPromptResource("system.md", language);
        this.userPromptTemplate = loadPromptResource("user.md", language);
        this.jsonValueParser = jsonValueParser;
    }

    @Override
    public ValidationResult validate(
            @NonNull String prompt,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri reference,
            @NonNull String templateContent) {
        if (llmClient == null) {
            throw new ContentValidationException(
                    ErrorCatalog.LLM_NOT_CONFIGURED.getCode(),
                    ErrorMessages.render(ErrorCatalog.LLM_NOT_CONFIGURED, language, null));
        }

        String userPrompt = fillUserPrompt(prompt, schema, reference, templateContent);
        Map<String, Object> outputSchema = buildOutputSchema();
        List<Map<String, String>> messages = toStructuredMessages(
                List.of(new PromptMessage("system", systemPrompt), new PromptMessage("user", userPrompt)));

        LLMResponse response;
        try {
            response = llmClient.structured(messages, outputSchema, null, null);
        } catch (LLMError error) {
            Map<String, String> facts = Map.of(
                    "provider", llmClient.getClass().getSimpleName(),
                    "reason", String.valueOf(error.getMessage()));
            throw new ContentValidationException(
                    ErrorCatalog.LLM_INVOCATION_FAILED.getCode(),
                    ErrorMessages.render(ErrorCatalog.LLM_INVOCATION_FAILED, language, facts),
                    error);
        }
        Map<String, Object> parsed;
        try {
            parsed = jsonValueParser.parseObject(response.content());
        } catch (RuntimeException exception) {
            throw responseInvalid("Semantic validation LLM response is not valid JSON: " + exception.getMessage());
        }

        boolean verdict = parseVerdict(parsed);
        List<SlotValidationError> errors = parseErrors(parsed.get("errors"));
        Map<String, Object> params = parseParams(parsed.get("params"));

        LOGGER.atDebug().log(
                "semantic_validation_completed verdict={} error_count={} param_count={}",
                verdict,
                errors.size(),
                params.size());
        return new ValidationResult(verdict, errors, params);
    }

    private static String loadPromptResource(String fileName, String language) {
        String classpathPath = PROMPT_RESOURCE_ROOT + language + "/" + fileName;
        InputStream stream = ClasspathResourceStreams.open(classpathPath);
        if (stream == null) {
            throw new ResourceNotFoundException(
                    "Content validation prompt resource does not exist for language " + language
                            + "; set A2AT_LANGUAGE to a language with bundled prompt resources (zh-CN or en-US).",
                    classpathPath);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new A2ATError(
                    ErrorCatalog.INFRA_RESOURCE_READ_FAILED.getCode(),
                    ErrorMessages.render(
                            ErrorCatalog.INFRA_RESOURCE_READ_FAILED, language, Map.of("resource_path", classpathPath)),
                    exception);
        }
    }

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\[(extension_name|input|template_uri|template_content|schema)\\]");

    private String fillUserPrompt(
            String prompt, Map<String, Object> schema, TemplateUri reference, String templateContent) {
        String schemaJson;
        try {
            schemaJson = OBJECT_MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new A2ATError("Failed to serialize schema to JSON.", exception);
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(userPromptTemplate);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String replacement =
                    switch (matcher.group(1)) {
                        case "extension_name" -> Matcher.quoteReplacement(reference.extensionName());
                        case "input" -> Matcher.quoteReplacement(prompt);
                        case "template_uri" -> Matcher.quoteReplacement(reference.uri());
                        case "template_content" -> Matcher.quoteReplacement(templateContent);
                        case "schema" -> Matcher.quoteReplacement(schemaJson);
                        default -> Matcher.quoteReplacement(matcher.group());
                    };
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static Map<String, Object> buildOutputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> verdictProp = new LinkedHashMap<>();
        verdictProp.put("type", "boolean");
        properties.put("semantic_verdict", verdictProp);

        Map<String, Object> factsProperties = new LinkedHashMap<>();
        factsProperties.put("type", "object");
        factsProperties.put("additionalProperties", Map.of("type", "string"));
        Map<String, Object> errorItemProperties = new LinkedHashMap<>();
        errorItemProperties.put("slot_name", Map.of("type", "string"));
        errorItemProperties.put("code", Map.of("type", "string"));
        errorItemProperties.put("facts", factsProperties);
        Map<String, Object> errorItem = new LinkedHashMap<>();
        errorItem.put("type", "object");
        errorItem.put("properties", errorItemProperties);
        errorItem.put("required", List.of("slot_name", "code", "facts"));
        Map<String, Object> errorsProp = new LinkedHashMap<>();
        errorsProp.put("type", "array");
        errorsProp.put("items", errorItem);
        properties.put("errors", errorsProp);

        Map<String, Object> paramsProp = new LinkedHashMap<>();
        paramsProp.put("type", "object");
        properties.put("params", paramsProp);

        schema.put("properties", properties);
        schema.put("required", List.of("semantic_verdict", "errors", "params"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static List<Map<String, String>> toStructuredMessages(List<PromptMessage> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
    }

    private boolean parseVerdict(Map<String, Object> parsed) {
        if (!(parsed.get("semantic_verdict") instanceof Boolean verdict)) {
            throw contractViolation("semantic_verdict must be a boolean.");
        }
        return verdict;
    }

    private List<SlotValidationError> parseErrors(Object errorsValue) {
        if (!(errorsValue instanceof List<?> errors)) {
            throw contractViolation("errors must be an array.");
        }
        List<SlotValidationError> normalized = new ArrayList<>();
        for (Object errorValue : errors) {
            if (!(errorValue instanceof Map<?, ?> errorMap)) {
                throw contractViolation("errors must be objects with slot_name, code and facts.");
            }
            if (!(errorMap.get("slot_name") instanceof String slotName)
                    || !(errorMap.get("code") instanceof String code)) {
                throw contractViolation("errors must carry string slot_name and code values.");
            }
            if (!(errorMap.get("facts") instanceof Map<?, ?> rawFacts)) {
                throw contractViolation("errors must carry a facts object.");
            }
            Map<String, String> facts = stringFacts(rawFacts);
            ErrorCatalog entry = resolveCode(code);
            if (!entry.getCode().equals(code)) {
                LOGGER.atWarn()
                        .log(
                                "semantic_validation_unknown_code original_code={} fallback_code={}",
                                code,
                                entry.getCode());
                facts = Map.of("section_label", slotName);
            }
            normalized.add(new SlotValidationError(
                    slotName, entry.getCode(), ErrorMessages.render(entry, language, facts), facts));
        }
        return List.copyOf(normalized);
    }

    /** Resolves one LLM-reported code to its catalog entry, falling back to {@code content.rule_violation}. */
    private static ErrorCatalog resolveCode(String code) {
        Optional<ErrorCatalog> entry = ErrorCatalog.byCode(code);
        if (entry.isPresent() && entry.get().getCode().startsWith(CONTENT_CODE_DOMAIN)) {
            return entry.get();
        }
        return ErrorCatalog.CONTENT_RULE_VIOLATION;
    }

    private static Map<String, String> stringFacts(Map<?, ?> rawFacts) {
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

    /**
     * Normalizes the LLM-extracted parameter map. Keys with a {@code null} value are preserved: a {@code null}
     * parameter is the semantic validator's explicit signal that a schema slot is missing from the content, and
     * downstream missing-parameter detection (negotiation triggering) relies on the key being present with a null
     * value. Dropping the key would make a missing slot indistinguishable from an absent one.
     */
    private Map<String, Object> parseParams(Object paramsValue) {
        if (!(paramsValue instanceof Map<?, ?> params)) {
            throw contractViolation("params must be an object.");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : params.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw contractViolation("params keys must be strings.");
            }
            normalized.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(normalized);
    }

    private ContentValidationException responseInvalid(String detail) {
        Map<String, String> facts = Map.of("step", SEMANTIC_VALIDATION_STEP);
        String message = ErrorMessages.render(ErrorCatalog.LLM_RESPONSE_INVALID, language, facts);
        return new ContentValidationException(
                ErrorCatalog.LLM_RESPONSE_INVALID.getCode(),
                message,
                List.of(new SlotValidationError("_llm", ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), message, facts)),
                new RuntimeException(detail));
    }

    private ContentValidationException contractViolation(String message) {
        Map<String, String> facts = Map.of("step", SEMANTIC_VALIDATION_STEP);
        String rendered = ErrorMessages.render(ErrorCatalog.LLM_RESPONSE_INVALID, language, facts);
        return new ContentValidationException(
                ErrorCatalog.LLM_RESPONSE_INVALID.getCode(),
                rendered,
                List.of(new SlotValidationError("_llm", ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), rendered, facts)),
                new RuntimeException(message));
    }
}
