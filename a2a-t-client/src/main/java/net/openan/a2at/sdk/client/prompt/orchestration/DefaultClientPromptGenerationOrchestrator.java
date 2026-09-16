package net.openan.a2at.sdk.client.prompt.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.client.model.PromptGenerationFailure;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMConfigError;
import net.openan.a2at.sdk.llm.LLMError;
import net.openan.a2at.sdk.prompt.analysis.exception.ScenarioRecognitionException;
import net.openan.a2at.sdk.prompt.analysis.impl.PromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.ScenarioRecognizer;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotValidationError;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.TaskPromptRenderer;
import net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal runnable client prompt generation orchestrator.
 *
 * @since 2026-06
 */
public final class DefaultClientPromptGenerationOrchestrator implements ClientPromptGenerationOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClientPromptGenerationOrchestrator.class);

    /** Old slot-extraction error code for a required slot that could not be extracted. */
    private static final String LEGACY_CODE_MISSING_INPUT = "missing_input";

    /** Old slot-extraction error code for a value violating a closed constraint. */
    private static final String LEGACY_CODE_INVALID_VALUE = "invalid_value";

    /** Step label used when the scenario-recognition LLM call fails. */
    private static final String STEP_SCENARIO_RECOGNITION = "scenario recognition";

    /** Step label used when the slot-extraction LLM call fails. */
    private static final String STEP_SLOT_EXTRACTION = "slot extraction";

    private static final List<String> RESPONSE_CONTRACT_MARKERS = List.of(
            "returned invalid json", "returned empty content", "must return a json object", "response did not include");

    private final ScenarioRecognizer scenarioRecognizer;

    private final List<ScenarioDefinition> scenarios;

    private final String language;

    private final String systemPrompt;

    private final String userPrompt;

    private final PromptTemplateTextLoader templateLoader;

    private final PromptSlotValueExtractor slotValueExtractor;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final TaskPromptRenderer renderer;

    private final int maxTextChars;

    /**
     * Creates a client prompt-generation orchestrator with explicit collaborators.
     *
     * @param scenarioRecognizer scenario recognizer
     * @param scenarios supported scenario definitions
     * @param language locale identifier for resource lookup
     * @param systemPrompt system prompt for scenario recognition
     * @param userPrompt user prompt for scenario recognition
     * @param templateLoader template loader
     * @param slotValueExtractor slot value extractor
     * @param renderer task prompt renderer
     * @param slotSchemaLoader slot schema loader
     */
    public DefaultClientPromptGenerationOrchestrator(
            ScenarioRecognizer scenarioRecognizer,
            List<ScenarioDefinition> scenarios,
            String language,
            String systemPrompt,
            String userPrompt,
            PromptTemplateTextLoader templateLoader,
            PromptSlotValueExtractor slotValueExtractor,
            TaskPromptRenderer renderer,
            PromptSlotSchemaLoader slotSchemaLoader) {
        this(
                scenarioRecognizer,
                scenarios,
                language,
                systemPrompt,
                userPrompt,
                templateLoader,
                slotValueExtractor,
                renderer,
                slotSchemaLoader,
                InputLimitConfig.DEFAULT_MAX_TEXT_CHARS);
    }

    /**
     * Creates a client prompt-generation orchestrator with explicit collaborators and one free-text input length limit.
     *
     * @param scenarioRecognizer scenario recognizer
     * @param scenarios supported scenario definitions
     * @param language locale identifier for resource lookup
     * @param systemPrompt system prompt for scenario recognition
     * @param userPrompt user prompt for scenario recognition
     * @param templateLoader template loader
     * @param slotValueExtractor slot value extractor
     * @param renderer task prompt renderer
     * @param slotSchemaLoader slot schema loader
     * @param maxTextChars maximum accepted length in characters for free-text inputs
     */
    public DefaultClientPromptGenerationOrchestrator(
            ScenarioRecognizer scenarioRecognizer,
            List<ScenarioDefinition> scenarios,
            String language,
            String systemPrompt,
            String userPrompt,
            PromptTemplateTextLoader templateLoader,
            PromptSlotValueExtractor slotValueExtractor,
            TaskPromptRenderer renderer,
            PromptSlotSchemaLoader slotSchemaLoader,
            int maxTextChars) {
        this.scenarioRecognizer = scenarioRecognizer;
        this.scenarios = scenarios;
        this.language = language;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.templateLoader = templateLoader;
        this.slotValueExtractor = slotValueExtractor;
        this.renderer = renderer;
        this.slotSchemaLoader = slotSchemaLoader;
        this.maxTextChars = maxTextChars;
    }

    @Override
    public PromptGenerationResult generateTaskPrompt(Object userInput) {
        if (userInput instanceof String text && InputLimitConfig.isTooLong(text, maxTextChars)) {
            return failure(
                    ErrorCatalog.INPUT_TEXT_TOO_LONG,
                    Map.of("actual_length", String.valueOf(text.length()), "max_chars", String.valueOf(maxTextChars)),
                    "input");
        }
        String normalizedInput = String.valueOf(userInput);

        final ScenarioRecognitionResult recognition;
        try {
            recognition = scenarioRecognizer.recognize(normalizedInput, scenarios, systemPrompt, userPrompt);
        } catch (ResourceNotFoundException error) {
            return failure(
                    ErrorCatalog.TEMPLATE_LOAD_FAILED, Map.of("resource_path", error.resourcePath()), "generation");
        } catch (ScenarioRecognitionException error) {
            return failure(ErrorCatalog.SCENARIO_NOT_MATCHED, Map.of("reason", error.getMessage()), "scenario");
        } catch (LLMError error) {
            return PromptGenerationResult.failure(llmFailure(error, STEP_SCENARIO_RECOGNITION));
        }
        if (!recognition.matched()
                || recognition.scenarioCode() == null
                || recognition.scenarioCode().isBlank()) {
            return failure(
                    ErrorCatalog.SCENARIO_NOT_MATCHED,
                    Map.of(
                            "reason",
                            recognition.errorMessage() == null
                                    ? "Scenario recognition failed."
                                    : recognition.errorMessage()),
                    "scenario");
        }

        final String templateText;
        try {
            templateText = templateLoader.loadTemplate(recognition.scenarioCode(), language);
        } catch (ResourceNotFoundException error) {
            return failure(
                    ErrorCatalog.TEMPLATE_NOT_FOUND,
                    Map.of("template_uri", recognition.scenarioCode(), "language", language),
                    "generation");
        } catch (A2ATError error) {
            return failure(
                    ErrorCatalog.TEMPLATE_LOAD_FAILED,
                    Map.of("resource_path", recognition.scenarioCode()),
                    "generation");
        }

        try {
            StructuredSlotExtractionResult extractionResult =
                    slotValueExtractor.extractSlots(userInput, recognition.scenarioCode(), language);
            PromptGenerationFailure slotFailure = slotExtractionFailure(extractionResult.slotErrors());
            if (slotFailure != null) {
                return PromptGenerationResult.failure(slotFailure);
            }
            Map<String, String> slots = extractionResult.slots();
            String renderedPrompt = renderer.render(templateText, slots);
            return PromptGenerationResult.success(renderedPrompt);
        } catch (ResourceNotFoundException error) {
            return failure(
                    ErrorCatalog.SLOT_SCHEMA_NOT_FOUND,
                    Map.of("template_uri", recognition.scenarioCode(), "language", language),
                    "generation");
        } catch (LLMError error) {
            return PromptGenerationResult.failure(llmFailure(error, STEP_SLOT_EXTRACTION));
        } catch (TaskPromptRenderException error) {
            return failure(
                    ErrorCatalog.TEMPLATE_RENDER_FAILED,
                    Map.of("template_uri", recognition.scenarioCode(), "reason", error.getMessage()),
                    "generation");
        } catch (A2ATError error) {
            return failure(ErrorCatalog.LLM_INVOCATION_FAILED, Map.of("reason", error.getMessage()), "generation");
        }
    }

    @Override
    public MetadataContent generateTaskPromptFromText(String text, TemplateUri templateUri) {
        return generateFromTemplateUriWithMetadata(text, templateUri, ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateTaskPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri) {
        return generateFromDataWithSchema(data, schema, templateUri, ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateAuthPromptFromText(String text, TemplateUri templateUri) {
        return generateFromTemplateUriWithMetadata(
                text, templateUri, ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateAuthPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri) {
        return generateFromDataWithSchema(
                data, schema, templateUri, ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateNotificationPromptFromText(String text, TemplateUri templateUri) {
        return generateFromTemplateUriWithMetadata(
                text, templateUri, ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI);
    }

    @Override
    public MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri) {
        return generateFromDataWithSchema(
                data, schema, templateUri, ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI);
    }

    private MetadataContent generateFromTemplateUriWithMetadata(
            String userInput, TemplateUri templateUri, String extensionUri) {
        Objects.requireNonNull(userInput, "userInput");
        if (InputLimitConfig.isTooLong(userInput, maxTextChars)) {
            throw new PromptGenerationException(
                    ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(),
                    render(
                            ErrorCatalog.INPUT_TEXT_TOO_LONG,
                            Map.of(
                                    "actual_length",
                                    String.valueOf(userInput.length()),
                                    "max_chars",
                                    String.valueOf(maxTextChars))));
        }
        return generateWithMetadata(
                templateUri,
                extensionUri,
                templateIdentifier -> slotValueExtractor.extractSlots(userInput, templateIdentifier, language));
    }

    private MetadataContent generateFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri, String extensionUri) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(schema, "schema");
        if (schema.isEmpty()) {
            throw new IllegalArgumentException(
                    "Data schema must not be empty; it describes the meaning of each input field.");
        }
        return generateWithMetadata(
                templateUri,
                extensionUri,
                templateIdentifier -> slotValueExtractor.extractSlots(data, templateIdentifier, language, schema));
    }

    private MetadataContent generateWithMetadata(
            TemplateUri templateUri,
            String extensionUri,
            Function<String, StructuredSlotExtractionResult> slotExtractor) {
        Objects.requireNonNull(templateUri, "templateUri");
        String templateIdentifier = templateUri.uri();
        final String templateText;
        try {
            templateText = templateLoader.loadTemplate(templateIdentifier, language);
        } catch (ResourceNotFoundException e) {
            throw new PromptGenerationException(
                    ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(),
                    render(
                            ErrorCatalog.TEMPLATE_NOT_FOUND,
                            Map.of("template_uri", templateIdentifier, "language", language)),
                    e);
        } catch (A2ATError e) {
            throw new PromptGenerationException(
                    ErrorCatalog.TEMPLATE_LOAD_FAILED.getCode(),
                    render(ErrorCatalog.TEMPLATE_LOAD_FAILED, Map.of("resource_path", templateIdentifier)),
                    e);
        }
        final StructuredSlotExtractionResult extractionResult;
        try {
            extractionResult = slotExtractor.apply(templateIdentifier);
        } catch (ResourceNotFoundException e) {
            throw new PromptGenerationException(
                    ErrorCatalog.SLOT_SCHEMA_NOT_FOUND.getCode(),
                    render(
                            ErrorCatalog.SLOT_SCHEMA_NOT_FOUND,
                            Map.of("template_uri", templateIdentifier, "language", language)),
                    e);
        } catch (LLMConfigError e) {
            throw new PromptGenerationException(
                    ErrorCatalog.LLM_NOT_CONFIGURED.getCode(), render(ErrorCatalog.LLM_NOT_CONFIGURED, Map.of()), e);
        } catch (LLMError e) {
            throw llmException(e, STEP_SLOT_EXTRACTION);
        } catch (A2ATError e) {
            throw new PromptGenerationException(
                    ErrorCatalog.LLM_INVOCATION_FAILED.getCode(),
                    render(ErrorCatalog.LLM_INVOCATION_FAILED, Map.of("reason", e.getMessage())),
                    e);
        }
        List<SlotValidationError> extractionErrors = mapSlotErrors(extractionResult.slotErrors());
        if (!extractionErrors.isEmpty()) {
            throw new PromptGenerationException(
                    extractionErrors.get(0).code(), joinMessages(extractionErrors), extractionErrors);
        }
        Map<String, String> slots = extractionResult.slots();
        validateRequiredSlots(slots, templateIdentifier);
        final String renderedPrompt;
        try {
            renderedPrompt = renderer.render(templateText, slots);
        } catch (TaskPromptRenderException e) {
            throw new PromptGenerationException(
                    ErrorCatalog.TEMPLATE_RENDER_FAILED.getCode(),
                    render(
                            ErrorCatalog.TEMPLATE_RENDER_FAILED,
                            Map.of("template_uri", templateIdentifier, "reason", e.getMessage())),
                    e);
        }
        return new MetadataContent(templateIdentifier, renderedPrompt, extensionUri);
    }

    private void validateRequiredSlots(Map<String, String> slots, String templateIdentifier) {
        final PromptSlotSchema schema;
        try {
            schema = slotSchemaLoader.loadSlotSchema(templateIdentifier, language);
        } catch (A2ATError e) {
            throw new PromptGenerationException(
                    ErrorCatalog.SLOT_SCHEMA_NOT_FOUND.getCode(),
                    render(
                            ErrorCatalog.SLOT_SCHEMA_NOT_FOUND,
                            Map.of("template_uri", templateIdentifier, "language", language)),
                    e);
        }
        if (schema == null) {
            throw new PromptGenerationException(
                    ErrorCatalog.SLOT_SCHEMA_NOT_FOUND.getCode(),
                    render(
                            ErrorCatalog.SLOT_SCHEMA_NOT_FOUND,
                            Map.of("template_uri", templateIdentifier, "language", language)));
        }
        List<PromptSlotDefinition> defs = schema.slotDefinitions();
        if (defs == null) {
            return;
        }
        Map<String, String> effectiveSlots = slots;
        if (effectiveSlots == null) {
            effectiveSlots = Map.of();
        }
        List<SlotValidationError> failed = new ArrayList<>();
        for (PromptSlotDefinition def : defs) {
            if (def == null) {
                continue;
            }
            if (def.required()) {
                String name = def.name();
                if (name == null) {
                    continue;
                }
                String value = effectiveSlots.get(name);
                if (value == null || value.trim().isEmpty()) {
                    failed.add(notProvidedError(name, slotLabel(def)));
                }
            }
        }
        if (!failed.isEmpty()) {
            throw new PromptGenerationException(failed.get(0).code(), joinMessages(failed), failed);
        }
    }

    /**
     * Maps the extraction-time slot errors reported by the slot-extraction step to catalog errors.
     *
     * <p>Unknown codes returned by the LLM step are mapped to the {@code slot.rule_violation} fallback and logged.
     *
     * @param slotErrors extraction-time slot errors, may be null
     * @return mapped slot validation errors, possibly empty, never null
     */
    private List<SlotValidationError> mapSlotErrors(@Nullable List<StructuredSlotValidationError> slotErrors) {
        if (slotErrors == null || slotErrors.isEmpty()) {
            return List.of();
        }
        List<SlotValidationError> mapped = new ArrayList<>();
        for (StructuredSlotValidationError error : slotErrors) {
            if (error == null) {
                continue;
            }
            mapped.add(mapSlotError(error));
        }
        return mapped;
    }

    private SlotValidationError mapSlotError(StructuredSlotValidationError error) {
        String label = error.slotName() == null ? "" : error.slotName();
        ErrorCatalog entry;
        if (ErrorCatalog.SLOT_NOT_PROVIDED.getCode().equals(error.code())
                || LEGACY_CODE_MISSING_INPUT.equals(error.code())) {
            entry = ErrorCatalog.SLOT_NOT_PROVIDED;
        } else if (ErrorCatalog.SLOT_CONSTRAINT_VIOLATED.getCode().equals(error.code())
                || LEGACY_CODE_INVALID_VALUE.equals(error.code())) {
            entry = ErrorCatalog.SLOT_CONSTRAINT_VIOLATED;
        } else {
            LOGGER.warn(
                    "Unknown slot-extraction error code '{}' mapped to '{}'.",
                    error.code(),
                    ErrorCatalog.SLOT_RULE_VIOLATION.getCode());
            entry = ErrorCatalog.SLOT_RULE_VIOLATION;
        }
        Map<String, String> facts = Map.of("slot_label", label);
        return new SlotValidationError(label, entry.getCode(), render(entry, facts), facts);
    }

    private SlotValidationError notProvidedError(String slotName, String slotLabel) {
        Map<String, String> facts = Map.of("slot_label", slotLabel);
        return new SlotValidationError(
                slotName,
                ErrorCatalog.SLOT_NOT_PROVIDED.getCode(),
                render(ErrorCatalog.SLOT_NOT_PROVIDED, facts),
                facts);
    }

    private String slotLabel(PromptSlotDefinition definition) {
        if (definition.description() != null && !definition.description().isBlank()) {
            return definition.description();
        }
        return definition.name() == null ? "" : definition.name();
    }

    private @Nullable PromptGenerationFailure slotExtractionFailure(
            @Nullable List<StructuredSlotValidationError> slotErrors) {
        List<SlotValidationError> mapped = mapSlotErrors(slotErrors);
        if (mapped.isEmpty()) {
            return null;
        }
        return new PromptGenerationFailure(mapped.get(0).code(), joinMessages(mapped), "generation");
    }

    private PromptGenerationFailure llmFailure(LLMError error, String step) {
        if (error instanceof LLMConfigError) {
            return new PromptGenerationFailure(
                    ErrorCatalog.LLM_NOT_CONFIGURED.getCode(),
                    render(ErrorCatalog.LLM_NOT_CONFIGURED, Map.of()),
                    "generation");
        }
        if (isResponseContractViolation(error)) {
            return new PromptGenerationFailure(
                    ErrorCatalog.LLM_RESPONSE_INVALID.getCode(),
                    render(ErrorCatalog.LLM_RESPONSE_INVALID, Map.of("step", step)),
                    "generation");
        }
        return new PromptGenerationFailure(
                ErrorCatalog.LLM_INVOCATION_FAILED.getCode(),
                render(ErrorCatalog.LLM_INVOCATION_FAILED, Map.of("reason", error.getMessage())),
                "generation");
    }

    private PromptGenerationException llmException(LLMError error, String step) {
        PromptGenerationFailure failure = llmFailure(error, step);
        return new PromptGenerationException(failure.code(), failure.message(), error);
    }

    private boolean isResponseContractViolation(LLMError error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        for (String marker : RESPONSE_CONTRACT_MARKERS) {
            if (message.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String joinMessages(List<SlotValidationError> errors) {
        return errors.stream().map(SlotValidationError::message).collect(Collectors.joining("; "));
    }

    private String render(ErrorCatalog entry, Map<String, String> facts) {
        return ErrorMessages.render(entry, language, facts);
    }

    private PromptGenerationResult failure(ErrorCatalog entry, Map<String, String> facts, String stage) {
        return PromptGenerationResult.failure(
                new PromptGenerationFailure(entry.getCode(), render(entry, facts), stage));
    }
}
