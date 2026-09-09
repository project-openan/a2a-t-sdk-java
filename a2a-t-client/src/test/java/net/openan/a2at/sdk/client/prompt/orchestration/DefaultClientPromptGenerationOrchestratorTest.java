package net.openan.a2at.sdk.client.prompt.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
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
import org.junit.jupiter.api.Test;

class DefaultClientPromptGenerationOrchestratorTest {

    private static final PromptSlotSchemaLoader EMPTY_SCHEMA_LOADER =
            (scenarioCode, language) -> new PromptSlotSchema(scenarioCode, List.of());

    private static final TemplateUri AUTH_DATABASE_READ = TemplateUri.of("Authorization-T", "database_read");

    @Test
    void generateTaskPromptLoadsTemplateAndRendersExtractedSlotsWhenScenarioIsMatched() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}\nNotes: {additional_notes}");
        FakeSlotValueExtractor slotValueExtractor =
                new FakeSlotValueExtractor(Map.of("site", "Site A", "additional_notes", "critical"));
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                recognizer,
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                templateLoader,
                slotValueExtractor,
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertTrue(result.success());
        assertEquals("Site: Site A\nNotes: critical", result.promptText());
        assertEquals("Analyze Site A.", recognizer.lastNormalizedInput);
        assertEquals("ran-energy-saving", templateLoader.lastScenarioCode);
        assertEquals("en-US", templateLoader.lastLanguage);
        assertEquals("Analyze Site A.", slotValueExtractor.lastUserInput);
        assertEquals("ran-energy-saving", slotValueExtractor.lastScenarioCode);
        assertEquals("en-US", slotValueExtractor.lastLanguage);
    }

    @Test
    void generateTaskPromptReturnsFailureWhenScenarioIsNotMatched() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(false, null, "No scenario matched."),
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> "Scenario: {scenario}\nInput: {input}",
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(
                        Map.of("scenario", scenarioCode, "input", String.valueOf(userInput)), List.of()),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("scenario.not_matched", result.failure().code());
        assertEquals("scenario", result.failure().stage());
    }

    @Test
    void generateTaskPromptReturnsFailureWhenTemplateIsMissing() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(true, "ran-energy-saving", null),
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> {
                    throw new ResourceNotFoundException("Prompt resource file does not exist.", scenarioCode);
                },
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(
                        Map.of("scenario", scenarioCode, "input", String.valueOf(userInput)), List.of()),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("template.not_found", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

    @Test
    void generateTaskPromptReturnsFailureWhenRenderingFails() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(true, "ran-energy-saving", null),
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> "Scenario: {scenario}\nMissing: {{missing_slot}}",
                (userInput, scenarioCode, language) ->
                        new StructuredSlotExtractionResult(Map.of("scenario", scenarioCode), List.of()),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("template.render_failed", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

    @Test
    void generateTaskPromptReturnsPromptResourceLoadErrorWhenScenarioPromptsAreMissingForRequestedLanguage() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) -> {
                    throw new ResourceNotFoundException(
                            "Prompt resource file does not exist.",
                            "prompt_resources/prompts/scenario_recognition/zh-CN/system.md");
                },
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "zh-CN",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> "Scenario: {scenario}\nInput: {input}",
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(
                        Map.of("scenario", scenarioCode, "input", String.valueOf(userInput)), List.of()),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("template.load_failed", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

    private static DefaultClientPromptGenerationOrchestrator newTemplateUriOrchestrator(
            ScenarioRecognizer recognizer,
            PromptTemplateTextLoader templateLoader,
            PromptSlotValueExtractor slotValueExtractor) {
        return new DefaultClientPromptGenerationOrchestrator(
                recognizer,
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                templateLoader,
                slotValueExtractor,
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);
    }

    private static final class RecordingScenarioRecognizer implements ScenarioRecognizer {
        private int invocationCount;
        private String lastNormalizedInput;

        @Override
        public ScenarioRecognitionResult recognize(
                String normalizedInput, List<ScenarioDefinition> scenarios, String systemPrompt, String userPrompt) {
            this.invocationCount++;
            this.lastNormalizedInput = normalizedInput;
            return new ScenarioRecognitionResult(true, "ran-energy-saving", null);
        }
    }

    private static final class CountingFailingTemplateLoader implements PromptTemplateTextLoader {
        private int loadCount;

        @Override
        public String loadTemplate(String scenarioCode, String language) {
            this.loadCount++;
            throw new ResourceNotFoundException("Prompt resource file does not exist.", scenarioCode);
        }
    }

    private static final class FakeTemplateLoader implements PromptTemplateTextLoader {
        private final String templateText;
        private String lastScenarioCode;
        private String lastLanguage;

        private FakeTemplateLoader(String templateText) {
            this.templateText = templateText;
        }

        @Override
        public String loadTemplate(String scenarioCode, String language) {
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            return templateText;
        }
    }

    private static final class FakeSlotValueExtractor implements PromptSlotValueExtractor {
        private final Map<String, String> slots;
        private Object lastUserInput;
        private String lastScenarioCode;
        private String lastLanguage;

        private FakeSlotValueExtractor(Map<String, String> slots) {
            this.slots = slots;
        }

        @Override
        public StructuredSlotExtractionResult extractSlots(Object userInput, String scenarioCode, String language) {
            this.lastUserInput = userInput;
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            return new StructuredSlotExtractionResult(slots, List.of());
        }
    }

    private static final class FakeSlotValueExtractorWithSchema implements PromptSlotValueExtractor {
        private final Map<String, String> slots;
        private Object lastUserInput;
        private String lastScenarioCode;
        private String lastLanguage;
        private Map<String, Object> lastDataSchema;

        private FakeSlotValueExtractorWithSchema(Map<String, String> slots) {
            this.slots = slots;
        }

        @Override
        public StructuredSlotExtractionResult extractSlots(Object userInput, String scenarioCode, String language) {
            this.lastUserInput = userInput;
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            return new StructuredSlotExtractionResult(slots, List.of());
        }

        @Override
        public StructuredSlotExtractionResult extractSlots(
                Object userInput, String scenarioCode, String language, Map<String, Object> dataSchema) {
            this.lastUserInput = userInput;
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            this.lastDataSchema = dataSchema;
            return new StructuredSlotExtractionResult(slots, List.of());
        }
    }

    private static final class FailingSlotValueExtractor implements PromptSlotValueExtractor {
        private final RuntimeException exception;

        private FailingSlotValueExtractor(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public StructuredSlotExtractionResult extractSlots(Object userInput, String scenarioCode, String language) {
            return new StructuredSlotExtractionResult(Map.of(), List.of());
        }

        @Override
        public StructuredSlotExtractionResult extractSlots(
                Object userInput, String scenarioCode, String language, Map<String, Object> dataSchema) {
            throw exception;
        }
    }

    // --- MetadataContent pipeline tests (generateFromTemplateUriWithMetadata / FromText) ---

    @Test
    void generateFromTemplateUriWithMetadataReturnsMetadataContentOnSuccess() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractor slotValueExtractor = new FakeSlotValueExtractor(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent result =
                orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING);

        assertEquals(StandardTemplates.ENERGY_SAVING.uri(), result.templateUri());
        assertEquals("Site: Site A", result.promptText());
        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, result.extensionUri());
    }

    @Test
    void generateFromTemplateUriWithMetadataScenarioRecognizerIsNotInvoked() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                recognizer, templateLoader, new FakeSlotValueExtractor(Map.of("site", "Site A")));

        orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING);

        assertEquals(0, recognizer.invocationCount);
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsOnNullTemplateUri() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        CountingFailingTemplateLoader templateLoader = new CountingFailingTemplateLoader();
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractor(Map.of()));

        NullPointerException ex = assertThrows(
                NullPointerException.class, () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", null));
        assertFalse(A2ATError.class.isInstance(ex), "null template URI must stay outside the A2ATError tree");
        assertEquals(0, recognizer.invocationCount);
        assertEquals(0, templateLoader.loadCount);
    }

    @Test
    void generateTaskPromptFromTextRejectsNullText() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        CountingFailingTemplateLoader templateLoader = new CountingFailingTemplateLoader();
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractor(Map.of()));

        assertThrows(
                NullPointerException.class,
                () -> orchestrator.generateTaskPromptFromText(null, StandardTemplates.ENERGY_SAVING));
    }

    @Test
    void generateFromTemplateUriWithMetadataPropagatesResourceNotFoundException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                (scenarioCode, language) -> {
                    throw new ResourceNotFoundException("Prompt resource file does not exist.", scenarioCode);
                },
                new FakeSlotValueExtractor(Map.of("site", "Site A")));

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("template.not_found", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataPropagatesRenderException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}\nMissing: {{missing_slot}}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")));

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("template.render_failed", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataPropagatesLlmRuntimeError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language) -> {
                    throw new LLMRuntimeError("LLM invocation failed.");
                });

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("llm.invocation_failed", ex.getCode());
    }

    // --- MetadataContent pipeline tests (generateFromDataWithSchema) ---

    @Test
    void generateFromDataWithSchemaReturnsMetadataContentOnSuccess() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent result = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of("site", "string"), StandardTemplates.ENERGY_SAVING);

        assertEquals(StandardTemplates.ENERGY_SAVING.uri(), result.templateUri());
        assertEquals("Site: Site A", result.promptText());
        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, result.extensionUri());
    }

    @Test
    void generateFromDataWithSchemaPassesSchemaToExtractor() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        Map<String, Object> schema = Map.of("site", "string", "count", "number");
        orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), schema, StandardTemplates.ENERGY_SAVING);

        assertEquals(schema, slotValueExtractor.lastDataSchema);
        assertEquals(StandardTemplates.ENERGY_SAVING.uri(), slotValueExtractor.lastScenarioCode);
    }

    @Test
    void generateTaskPromptFromDataWithSchemaRejectsNullData() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(
                        null, Map.of("site", "string"), StandardTemplates.ENERGY_SAVING));
        assertTrue(ex.getMessage().contains("data"));
    }

    @Test
    void generateTaskPromptFromDataWithSchemaRejectsNullSchema() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(
                        Map.of("site", "Site A"), null, StandardTemplates.ENERGY_SAVING));
        assertEquals("schema", ex.getMessage());
    }

    @Test
    void generateTaskPromptFromDataWithSchemaRejectsEmptySchema() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(
                        Map.of("site", "Site A"), Map.of(), StandardTemplates.ENERGY_SAVING));
        assertTrue(ex.getMessage().contains("Data schema must not be empty"));
    }

    @Test
    void generateFromDataWithSchemaPropagatesResourceNotFoundException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                (scenarioCode, language) -> {
                    throw new ResourceNotFoundException("Prompt resource file does not exist.", scenarioCode);
                },
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A")));

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(
                        Map.of("site", "Site A"), Map.of("site", "string"), StandardTemplates.ENERGY_SAVING));
        assertEquals("template.not_found", ex.getCode());
    }

    @Test
    void generateFromDataWithSchemaPropagatesRenderException() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}\nMissing: {{missing_slot}}"),
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A")));

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(
                        Map.of("site", "Site A"), Map.of("site", "string"), StandardTemplates.ENERGY_SAVING));
        assertEquals("template.render_failed", ex.getCode());
    }

    @Test
    void generateFromDataWithSchemaPropagatesLlmRuntimeError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                new FailingSlotValueExtractor(new LLMRuntimeError("LLM invocation failed.")));

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(
                        Map.of("site", "Site A"), Map.of("site", "string"), StandardTemplates.ENERGY_SAVING));
        assertEquals("llm.invocation_failed", ex.getCode());
    }

    @Test
    void generateFromDataWithSchemaPropagatesResourceNotFoundExceptionFromExtractor() {
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                new FailingSlotValueExtractor(
                        new ResourceNotFoundException("Slot schema file does not exist.", "ran-energy-saving")));

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromDataWithSchema(
                        Map.of("site", "Site A"), Map.of("site", "string"), StandardTemplates.ENERGY_SAVING));
        assertEquals("slot.schema_not_found", ex.getCode());
    }

    // --- Public entry point tests ---

    @Test
    void eachMetadataEntryPointReturnsCorrectMetadataContent() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("site", "Site A"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent taskText =
                orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING);
        MetadataContent taskData = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of("site", "string"), StandardTemplates.ENERGY_SAVING);
        MetadataContent authText = orchestrator.generateAuthPromptFromText("Grant access.", AUTH_DATABASE_READ);
        MetadataContent authData = orchestrator.generateAuthPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of("site", "string"), AUTH_DATABASE_READ);
        MetadataContent notifText =
                orchestrator.generateNotificationPromptFromText("Report finished.", StandardTemplates.ENERGY_SAVING);
        MetadataContent notifData = orchestrator.generateNotificationPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of("site", "string"), StandardTemplates.ENERGY_SAVING);

        assertTrue(taskText.promptText().contains("Site A"));
        assertTrue(taskData.promptText().contains("Site A"));
        assertTrue(authText.promptText().contains("Site A"));
        assertTrue(authData.promptText().contains("Site A"));
        assertTrue(notifText.promptText().contains("Site A"));
        assertTrue(notifData.promptText().contains("Site A"));
    }

    @Test
    void eachMetadataEntryPointUsesCorrectExtensionUri() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Site: {site}");
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                templateLoader,
                new FakeSlotValueExtractor(Map.of("site", "Site A")));

        MetadataContent taskText =
                orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING);
        MetadataContent taskData = orchestrator.generateTaskPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of("site", "string"), StandardTemplates.ENERGY_SAVING);
        MetadataContent authText = orchestrator.generateAuthPromptFromText("Grant access.", AUTH_DATABASE_READ);
        MetadataContent authData = orchestrator.generateAuthPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of("site", "string"), AUTH_DATABASE_READ);
        MetadataContent notifText =
                orchestrator.generateNotificationPromptFromText("Report finished.", StandardTemplates.ENERGY_SAVING);
        MetadataContent notifData = orchestrator.generateNotificationPromptFromDataWithSchema(
                Map.of("site", "Site A"), Map.of("site", "string"), StandardTemplates.ENERGY_SAVING);

        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, taskText.extensionUri());
        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, taskData.extensionUri());
        assertEquals(ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI, authText.extensionUri());
        assertEquals(ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI, authData.extensionUri());
        assertEquals(ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI, notifText.extensionUri());
        assertEquals(ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI, notifData.extensionUri());
    }

    @Test
    void authorizationMetadataEntryPointsPassTemplateUriIdentifier() {
        FakeTemplateLoader templateLoader = new FakeTemplateLoader("Access: {scope}");
        FakeSlotValueExtractorWithSchema slotValueExtractor =
                new FakeSlotValueExtractorWithSchema(Map.of("scope", "read"));
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(new RecordingScenarioRecognizer(), templateLoader, slotValueExtractor);

        MetadataContent nlResult = orchestrator.generateAuthPromptFromText("Grant read access.", AUTH_DATABASE_READ);

        assertEquals("Authorization-T/database_read/v1", nlResult.templateUri());
        assertEquals("Access: read", nlResult.promptText());
        assertEquals("Authorization-T/database_read/v1", templateLoader.lastScenarioCode);
        assertEquals("Authorization-T/database_read/v1", slotValueExtractor.lastScenarioCode);
        assertEquals("Grant read access.", slotValueExtractor.lastUserInput);

        MetadataContent jsonResult = orchestrator.generateAuthPromptFromDataWithSchema(
                Map.of("scope", "read"), Map.of("scope", "string"), AUTH_DATABASE_READ);

        assertEquals("Authorization-T/database_read/v1", jsonResult.templateUri());
        assertEquals("Authorization-T/database_read/v1", templateLoader.lastScenarioCode);
        assertEquals(Map.of("scope", "read"), slotValueExtractor.lastUserInput);
        assertEquals(Map.of("scope", "string"), slotValueExtractor.lastDataSchema);
    }

    // --- slot validation tests ---

    @Test
    void validateRequiredSlotsThrowsSlotValidationErrorWhenRequiredSlotsMissing() {
        PromptSlotSchemaLoader schemaLoader = (scenarioCode, language) -> new PromptSlotSchema(
                scenarioCode,
                List.of(new PromptSlotDefinition(
                        "site", true, "string", null, null, null, null, null, "Site name", null)));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of()),
                new TaskPromptRenderer(),
                schemaLoader);

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("slot.not_provided", ex.getCode());
        List<SlotValidationError> failed = ex.failedParameters();
        assertFalse(failed.isEmpty());
        assertEquals("site", failed.get(0).slotName());
        assertEquals("slot.not_provided", failed.get(0).code());
    }

    @Test
    void validateRequiredSlotsThrowsSlotValidationErrorWhenMultipleRequiredSlotsMissing() {
        PromptSlotSchemaLoader schemaLoader = (scenarioCode, language) -> new PromptSlotSchema(
                scenarioCode,
                List.of(
                        new PromptSlotDefinition(
                                "site", true, "string", null, null, null, null, null, "Site name", null),
                        new PromptSlotDefinition(
                                "target", true, "string", null, null, null, null, null, "Target", null)));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}\nTarget: {target}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                schemaLoader);

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("slot.not_provided", ex.getCode());
        assertEquals(1, ex.failedParameters().size());
        assertEquals("target", ex.failedParameters().get(0).slotName());
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsSlotSchemaNotFoundWhenSchemaLoadingFails() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                (scenarioCode, language) -> {
                    throw new A2ATError("Schema file is corrupt.");
                });

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("slot.schema_not_found", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsSlotSchemaNotFoundFromExtractor() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language) -> {
                    throw new ResourceNotFoundException("Slot schema file does not exist.", scenarioCode);
                },
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("slot.schema_not_found", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsPromptResourceLoadError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                (scenarioCode, language) -> {
                    throw new A2ATError("Template file is corrupt.");
                },
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("template.load_failed", ex.getCode());
    }

    @Test
    void generateFromTemplateUriWithMetadataThrowsLlmInvocationFailedFromA2ATError() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language) -> {
                    throw new A2ATError("Unparseable LLM response.");
                },
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("llm.invocation_failed", ex.getCode());
    }

    @Test
    void validateRequiredSlotsThrowsSlotValidationErrorWhenSlotValueIsEmptyString() {
        PromptSlotSchemaLoader schemaLoader = (scenarioCode, language) -> new PromptSlotSchema(
                scenarioCode,
                List.of(new PromptSlotDefinition(
                        "site", true, "string", null, null, null, null, null, "Site name", null)));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of("site", "")),
                new TaskPromptRenderer(),
                schemaLoader);

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("slot.not_provided", ex.getCode());
        assertEquals(1, ex.failedParameters().size());
        assertEquals("site", ex.failedParameters().get(0).slotName());
        assertEquals("slot.not_provided", ex.failedParameters().get(0).code());
    }

    @Test
    void validateRequiredSlotsThrowsSlotValidationErrorWhenSlotValueIsBlankString() {
        PromptSlotSchemaLoader schemaLoader = (scenarioCode, language) -> new PromptSlotSchema(
                scenarioCode,
                List.of(new PromptSlotDefinition(
                        "site", true, "string", null, null, null, null, null, "Site name", null)));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of("site", "   ")),
                new TaskPromptRenderer(),
                schemaLoader);

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("slot.not_provided", ex.getCode());
        assertEquals(1, ex.failedParameters().size());
        assertEquals("site", ex.failedParameters().get(0).slotName());
    }

    @Test
    void validateRequiredSlotsSucceedsWhenSchemaHasNoRequiredSlotsAndAllSlotsAreEmpty() {
        PromptSlotSchemaLoader schemaLoader = (scenarioCode, language) -> new PromptSlotSchema(
                scenarioCode,
                List.of(new PromptSlotDefinition(
                        "site", false, "string", null, null, null, null, null, "Site name", null)));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of()),
                new TaskPromptRenderer(),
                schemaLoader);

        MetadataContent result =
                orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING);
        assertNotNull(result);
        assertNotNull(result.promptText());
    }

    @Test
    void validateRequiredSlotsIsNotInvokedInGenerateTaskPromptPipeline() {
        PromptSlotSchemaLoader failingSchemaLoader = (scenarioCode, language) -> {
            throw new A2ATError("Schema loading should not be triggered in generateTaskPrompt pipeline.");
        };
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(true, "energy_saving", null),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                (scenarioCode, language) -> "Site: {site}",
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                failingSchemaLoader);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");
        assertTrue(result.success());
        assertEquals("Site: Site A", result.promptText());
    }

    @Test
    void generateTaskPromptNormalizesNullInputToString() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                recognizer,
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt(null);

        assertTrue(result.success());
        assertEquals("null", recognizer.lastNormalizedInput);
        assertEquals("Site: Site A", result.promptText());
    }

    @Test
    void generateTaskPromptReturnsFailureWithEmptyScenarioList() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) -> {
                    assertEquals(0, scenarios.size());
                    return new ScenarioRecognitionResult(false, null, "No scenarios available.");
                },
                List.of(),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> "Scenario: {scenario}\nInput: {input}",
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(Map.of(), List.of()),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("scenario.not_matched", result.failure().code());
        assertEquals("scenario", result.failure().stage());
    }

    @Test
    void generateTaskPromptMapsLlmRuntimeErrorFromExtractorToResultFailure() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(true, "ran-energy-saving", null),
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> "Site: {site}",
                (userInput, scenarioCode, language) -> {
                    throw new LLMRuntimeError("LLM invocation failed.");
                },
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("llm.invocation_failed", result.failure().code());
        assertEquals("generation", result.failure().stage());
    }

    @Test
    void generateTaskPromptMapsLlmResponseContractErrorToResultFailure() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                (normalizedInput, scenarios, systemPrompt, userPrompt) ->
                        new ScenarioRecognitionResult(true, "ran-energy-saving", null),
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                (scenarioCode, language) -> "Site: {site}",
                (userInput, scenarioCode, language) -> {
                    throw new LLMRuntimeError("openai returned invalid json: unexpected token");
                },
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("llm.response_invalid", result.failure().code());
        assertEquals(
                "The LLM response is invalid (step: slot extraction); please retry",
                result.failure().message());
    }

    @Test
    void generateTaskPromptSurfacesExtractionSlotErrorsAsFailure() {
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                recognizer,
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(
                        Map.of("site", "Site A"),
                        List.of(new StructuredSlotValidationError("subscriptionCondition", "slot.not_provided", null))),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("slot.not_provided", result.failure().code());
        assertEquals(
                "'subscriptionCondition' is not provided in the input.",
                result.failure().message());
        assertEquals("generation", result.failure().stage());
    }

    @Test
    void generateTaskPromptMapsUnknownExtractionSlotErrorCodeToFallback() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(
                        Map.of("site", "Site A"),
                        List.of(new StructuredSlotValidationError("extra_field", "unexpected_field", null))),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("slot.rule_violation", result.failure().code());
    }

    @Test
    void generateTaskPromptMapsLegacyExtractionSlotErrorCodes() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "en-US",
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.",
                new FakeTemplateLoader("Site: {site}"),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(
                        Map.of("site", "Site A"),
                        List.of(
                                new StructuredSlotValidationError("site", "missing_input", null),
                                new StructuredSlotValidationError("site", "invalid_value", null))),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site A.");

        assertFalse(result.success());
        assertNotNull(result.failure());
        assertEquals("slot.not_provided", result.failure().code());
        assertTrue(result.failure().message().contains("; "), "both mapped messages must be reported");
    }

    @Test
    void generateFromTemplateUriWithMetadataEmptyInputProducesSlotValidationError() {
        PromptSlotSchemaLoader schemaLoader = (scenarioCode, language) -> new PromptSlotSchema(
                scenarioCode,
                List.of(new PromptSlotDefinition(
                        "site", true, "string", null, null, null, null, null, "Site name", null)));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of()),
                new TaskPromptRenderer(),
                schemaLoader);

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("", StandardTemplates.ENERGY_SAVING));
        assertEquals("slot.not_provided", ex.getCode());
        assertEquals(1, ex.failedParameters().size());
        assertEquals("site", ex.failedParameters().get(0).slotName());
    }

    @Test
    void validateRequiredSlotsTreatsNullSlotValueAsMissing() {
        PromptSlotSchemaLoader schemaLoader = (scenarioCode, language) -> new PromptSlotSchema(
                scenarioCode,
                List.of(new PromptSlotDefinition(
                        "site", true, "string", null, null, null, null, null, "Site name", null)));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Collections.singletonMap("site", null)),
                new TaskPromptRenderer(),
                schemaLoader);

        PromptGenerationException ex = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("Analyze Site A.", StandardTemplates.ENERGY_SAVING));
        assertEquals("slot.not_provided", ex.getCode());
        assertEquals(1, ex.failedParameters().size());
        assertEquals("site", ex.failedParameters().get(0).slotName());
        assertEquals("slot.not_provided", ex.failedParameters().get(0).code());
    }

    // --- free-text input length limit tests ---

    @Test
    void fromTextEntryPointsRejectTextOverTheDefaultLimitBeforeAnyLlmCall() {
        String overLimitText = "a".repeat(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS + 1);
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        CountingFailingTemplateLoader templateLoader = new CountingFailingTemplateLoader();
        DefaultClientPromptGenerationOrchestrator orchestrator =
                newTemplateUriOrchestrator(recognizer, templateLoader, new FakeSlotValueExtractor(Map.of()));

        PromptGenerationException taskEx = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText(overLimitText, StandardTemplates.ENERGY_SAVING));
        PromptGenerationException authEx = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateAuthPromptFromText(overLimitText, AUTH_DATABASE_READ));
        PromptGenerationException notificationEx = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateNotificationPromptFromText(overLimitText, StandardTemplates.ENERGY_SAVING));

        assertEquals("input.text_too_long", taskEx.getCode());
        assertEquals("input.text_too_long", authEx.getCode());
        assertEquals("input.text_too_long", notificationEx.getCode());
        assertTrue(taskEx.getMessage().contains(String.valueOf(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS + 1)));
        assertEquals(0, recognizer.invocationCount, "over-limit input must fail before any LLM call");
        assertEquals(0, templateLoader.loadCount, "over-limit input must fail before template loading");
    }

    @Test
    void fromTextAcceptsTextAtExactlyTheDefaultLimit() {
        String limitText = "a".repeat(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS);
        DefaultClientPromptGenerationOrchestrator orchestrator = newTemplateUriOrchestrator(
                new RecordingScenarioRecognizer(),
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")));

        MetadataContent result = orchestrator.generateTaskPromptFromText(limitText, StandardTemplates.ENERGY_SAVING);

        assertEquals("Site: Site A", result.promptText());
    }

    @Test
    void fromTextRejectsTextOverOneConfiguredLimit() {
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER,
                10);

        PromptGenerationException overEx = assertThrows(
                PromptGenerationException.class,
                () -> orchestrator.generateTaskPromptFromText("a".repeat(11), StandardTemplates.ENERGY_SAVING));
        assertEquals("input.text_too_long", overEx.getCode());

        MetadataContent atLimit =
                orchestrator.generateTaskPromptFromText("a".repeat(10), StandardTemplates.ENERGY_SAVING);
        assertEquals("Site: Site A", atLimit.promptText());
    }

    @Test
    void generateTaskPromptReturnsFailureWhenStringInputExceedsTheDefaultLimit() {
        String overLimitText = "a".repeat(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS + 1);
        RecordingScenarioRecognizer recognizer = new RecordingScenarioRecognizer();
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                recognizer,
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt(overLimitText);

        assertFalse(result.success());
        assertEquals("input.text_too_long", result.failure().code());
        assertEquals("input", result.failure().stage());
        assertEquals(0, recognizer.invocationCount, "over-limit input must fail before any LLM call");
    }

    @Test
    void generateTaskPromptDoesNotCheckLengthForMapInput() {
        Map<String, Object> mapInputWithLongStringValue =
                Map.of("notes", "a".repeat(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS + 1));
        DefaultClientPromptGenerationOrchestrator orchestrator = new DefaultClientPromptGenerationOrchestrator(
                new RecordingScenarioRecognizer(),
                List.of(new ScenarioDefinition("energy_saving", "Energy Saving", "Energy analysis", "Analyze")),
                "en-US",
                "",
                "",
                new FakeTemplateLoader("Site: {site}"),
                new FakeSlotValueExtractor(Map.of("site", "Site A")),
                new TaskPromptRenderer(),
                EMPTY_SCHEMA_LOADER);

        PromptGenerationResult result = orchestrator.generateTaskPrompt(mapInputWithLongStringValue);

        assertTrue(result.success(), "map input must not be length-checked");
        assertEquals("Site: Site A", result.promptText());
    }
}
