package net.openan.a2at.sdk.server.metadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
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
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side metadata extractor that mirrors the Python flow by resolving scenario and slots from the processed prompt
 * text with LLM-backed analysis steps.
 *
 * @since 2026-06
 */
public final class LlmBackedPromptMetadataExtractor implements ServerPromptMetadataExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmBackedPromptMetadataExtractor.class);

    /** Default reason rendered into {@code scenario.not_matched} when the recognizer reports none. */
    private static final String DEFAULT_SCENARIO_REASON = "scenario recognition failed";

    private final ScenarioRecognizer scenarioRecognizer;

    private final List<ScenarioDefinition> scenarios;

    private final String language;

    private final String systemPrompt;

    private final String userPrompt;

    private final PromptTemplateTextLoader templateLoader;

    private final PromptSlotSchemaLoader slotSchemaLoader;

    private final PromptSlotValueExtractor slotValueExtractor;

    public LlmBackedPromptMetadataExtractor(
            ScenarioRecognizer scenarioRecognizer,
            List<ScenarioDefinition> scenarios,
            String language,
            String systemPrompt,
            String userPrompt,
            PromptTemplateTextLoader templateLoader,
            PromptSlotSchemaLoader slotSchemaLoader,
            PromptSlotValueExtractor slotValueExtractor) {
        this.scenarioRecognizer = scenarioRecognizer;
        this.scenarios = List.copyOf(scenarios);
        this.language = language;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.templateLoader = templateLoader;
        this.slotSchemaLoader = slotSchemaLoader;
        this.slotValueExtractor = slotValueExtractor;
    }

    @Override
    public ProcessedPromptMetadata extract(String processedPromptText) {
        ScenarioRecognitionResult recognitionResult = resolveScenario(processedPromptText);
        String scenarioCode = recognitionResult.scenarioCode();
        String templateText = loadTemplate(scenarioCode);
        PromptSlotSchema slotSchema = slotSchemaLoader.loadSlotSchema(scenarioCode, language);
        StructuredSlotExtractionResult extractionResult =
                slotValueExtractor.extractSlots(processedPromptText, scenarioCode, language);
        validateExtractionResult(extractionResult, slotSchema);
        return new ProcessedPromptMetadata(scenarioCode, language, templateText, Map.copyOf(extractionResult.slots()));
    }

    private ScenarioRecognitionResult resolveScenario(String processedPromptText) {
        try {
            ScenarioRecognitionResult result =
                    scenarioRecognizer.recognize(processedPromptText, scenarios, systemPrompt, userPrompt);
            if (!result.matched()
                    || result.scenarioCode() == null
                    || result.scenarioCode().isBlank()) {
                throw scenarioNotMatched(result.errorMessage());
            }
            return result;
        } catch (ScenarioRecognitionException error) {
            throw scenarioNotMatched(error.getMessage());
        }
    }

    private PromptComplianceCheckException scenarioNotMatched(String reason) {
        String effectiveReason = reason == null || reason.isBlank() ? DEFAULT_SCENARIO_REASON : reason;
        return new PromptComplianceCheckException(
                ErrorCatalog.SCENARIO_NOT_MATCHED, language, Map.of("reason", effectiveReason), "prompt_parse");
    }

    private String loadTemplate(String scenarioCode) {
        try {
            return templateLoader.loadTemplate(scenarioCode, language);
        } catch (ResourceNotFoundException error) {
            throw new PromptComplianceCheckException(
                    ErrorCatalog.TEMPLATE_NOT_FOUND,
                    language,
                    Map.of("template_uri", scenarioCode, "language", language),
                    "generation");
        } catch (A2ATError error) {
            LOGGER.warn(
                    "Template load failed for scenario '{}' and language '{}': {}",
                    scenarioCode,
                    language,
                    error.getMessage());
            throw new PromptComplianceCheckException(
                    ErrorCatalog.INFRA_RESOURCE_READ_FAILED,
                    language,
                    Map.of("resource_path", scenarioCode),
                    "generation");
        }
    }

    private void validateExtractionResult(
            StructuredSlotExtractionResult extractionResult, PromptSlotSchema slotSchema) {
        if (!extractionResult.slotErrors().isEmpty()) {
            throw slotFailure(extractionResult.slotErrors().get(0));
        }

        for (PromptSlotDefinition definition : slotSchema.slotDefinitions()) {
            String value = extractionResult.slots().get(definition.name());
            if (definition.required() && (value == null || value.isBlank())) {
                throw new PromptComplianceCheckException(
                        ErrorCatalog.SLOT_NOT_PROVIDED,
                        language,
                        Map.of("slot_label", definition.name()),
                        "slot_validation");
            }
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmedValue = value.trim();
            if (definition.allowedValues() != null
                    && !definition.allowedValues().isEmpty()
                    && !definition.allowedValues().contains(trimmedValue)) {
                throw constraintViolation(definition, trimmedValue);
            }
            if (definition.pattern() != null
                    && !definition.pattern().isBlank()
                    && !trimmedValue.matches(definition.pattern())) {
                throw constraintViolation(definition, trimmedValue);
            }
            validateNumericConstraint(definition, trimmedValue);
        }
    }

    private PromptComplianceCheckException slotFailure(StructuredSlotValidationError error) {
        String slotName = error.slotName() == null ? "" : error.slotName();
        return new PromptComplianceCheckException(
                resolveSlotErrorCode(error.code(), slotName),
                language,
                Map.of("slot_label", slotName),
                "slot_validation");
    }

    private static ErrorCatalog resolveSlotErrorCode(String code, String slotName) {
        Optional<ErrorCatalog> entry = ErrorCatalog.byCode(code);
        if (entry.isPresent()) {
            return entry.get();
        }
        LOGGER.warn(
                "Unknown slot validation error code '{}' for slot '{}'; falling back to '{}'.",
                code,
                slotName,
                ErrorCatalog.SLOT_RULE_VIOLATION.getCode());
        return ErrorCatalog.SLOT_RULE_VIOLATION;
    }

    private PromptComplianceCheckException constraintViolation(PromptSlotDefinition definition, String actual) {
        return new PromptComplianceCheckException(
                ErrorCatalog.SLOT_CONSTRAINT_VIOLATED,
                language,
                Map.of("slot_label", definition.name(), "actual", actual),
                "slot_validation");
    }

    private void validateNumericConstraint(PromptSlotDefinition definition, String value) {
        if (!"integer".equalsIgnoreCase(definition.jsonType()) && !"number".equalsIgnoreCase(definition.jsonType())) {
            return;
        }
        double numericValue;
        try {
            numericValue = Double.parseDouble(value);
        } catch (NumberFormatException error) {
            throw constraintViolation(definition, value);
        }
        if (definition.minimum() != null && numericValue < definition.minimum()) {
            throw constraintViolation(definition, value);
        }
        if (definition.maximum() != null && numericValue > definition.maximum()) {
            throw constraintViolation(definition, value);
        }
    }
}
