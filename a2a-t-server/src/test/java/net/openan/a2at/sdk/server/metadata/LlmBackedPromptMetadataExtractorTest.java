package net.openan.a2at.sdk.server.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.analysis.impl.LlmScenarioRecognizer;
import net.openan.a2at.sdk.prompt.analysis.impl.PromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotValidationError;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;
import org.junit.jupiter.api.Test;

class LlmBackedPromptMetadataExtractorTest {

    private static final String SLOT_NAME = "notification_topic";

    @Test
    void extractResolvesScenarioLoadsTemplateAndReturnsExtractedSlots() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"subscribe-incident\",\"error_message\":null}");
        PromptTemplateTextLoader templateLoader =
                (scenarioCode, language) -> "## notification_topic\n{{notification_topic}}\n";
        PromptSlotSchemaLoader slotSchemaLoader = (scenarioCode, language) -> new PromptSlotSchema(
                scenarioCode,
                List.of(new PromptSlotDefinition(SLOT_NAME, true, "string", null, null, null, null, null, null, null)));
        PromptSlotValueExtractor slotValueExtractor = (userInput, scenarioCode, language) ->
                new StructuredSlotExtractionResult(Map.of(SLOT_NAME, "Incident"), List.of());
        LlmBackedPromptMetadataExtractor extractor = new LlmBackedPromptMetadataExtractor(
                new LlmScenarioRecognizer(llmClient),
                List.of(new ScenarioDefinition(
                        "subscribe-incident", "Incident subscription", "Subscribe incident", "Subscribe Incident")),
                "zh-CN",
                "Identify scenario.",
                "Choose scenario.",
                templateLoader,
                slotSchemaLoader,
                slotValueExtractor);

        ProcessedPromptMetadata metadata = extractor.extract("## notification_topic\nIncident\n");

        assertEquals("subscribe-incident", metadata.scenarioCode());
        assertEquals("zh-CN", metadata.language());
        assertEquals("## notification_topic\n{{notification_topic}}\n", metadata.templateText());
        assertEquals(Map.of(SLOT_NAME, "Incident"), metadata.slots());
    }

    @Test
    void extractReturnsScenarioNotMatchedWhenScenarioRecognitionDoesNotMatch() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":false,\"scenario_code\":null,\"error_message\":\"No scenario matched.\"}");
        LlmBackedPromptMetadataExtractor extractor = new LlmBackedPromptMetadataExtractor(
                new LlmScenarioRecognizer(llmClient),
                List.of(new ScenarioDefinition(
                        "subscribe-incident", "Incident subscription", "Subscribe incident", "Subscribe Incident")),
                "zh-CN",
                "Identify scenario.",
                "Choose scenario.",
                (scenarioCode, language) -> "",
                (scenarioCode, language) -> new PromptSlotSchema(scenarioCode, List.of()),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(Map.of(), List.of()));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("unknown prompt"));

        assertEquals("scenario.not_matched", error.getCode());
        assertEquals("prompt_parse", error.getStage());
        assertTrue(error.getMessage().contains("No scenario matched."), "message must render the recognition reason");
    }

    @Test
    void extractReturnsScenarioNotMatchedWhenRecognitionFailsWithoutReason() {
        LLMClient llmClient = new RecordingClient("{\"matched\":false,\"scenario_code\":null,\"error_message\":null}");
        LlmBackedPromptMetadataExtractor extractor = new LlmBackedPromptMetadataExtractor(
                new LlmScenarioRecognizer(llmClient),
                List.of(new ScenarioDefinition(
                        "subscribe-incident", "Incident subscription", "Subscribe incident", "Subscribe Incident")),
                "zh-CN",
                "Identify scenario.",
                "Choose scenario.",
                (scenarioCode, language) -> "",
                (scenarioCode, language) -> new PromptSlotSchema(scenarioCode, List.of()),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(Map.of(), List.of()));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("unknown prompt"));

        assertEquals("scenario.not_matched", error.getCode());
        assertEquals("prompt_parse", error.getStage());
    }

    @Test
    void extractReturnsCatalogSlotCodeWhenStructuredExtractionReportsSlotErrors() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"subscribe-incident\",\"error_message\":null}");
        LlmBackedPromptMetadataExtractor extractor = new LlmBackedPromptMetadataExtractor(
                new LlmScenarioRecognizer(llmClient),
                List.of(new ScenarioDefinition(
                        "subscribe-incident", "Incident subscription", "Subscribe incident", "Subscribe Incident")),
                "zh-CN",
                "Identify scenario.",
                "Choose scenario.",
                (scenarioCode, language) -> "template",
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                SLOT_NAME, true, "string", null, null, null, null, null, null, null))),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(
                        Map.of(SLOT_NAME, ""),
                        List.of(new StructuredSlotValidationError(
                                SLOT_NAME, "slot.not_provided", "topic is missing"))));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("bad prompt"));

        assertEquals("slot.not_provided", error.getCode());
        assertEquals("slot_validation", error.getStage());
        assertTrue(error.getMessage().contains(SLOT_NAME), "message must render the slot label");
        assertEquals(Map.of("slot_label", SLOT_NAME), error.getFacts());
    }

    @Test
    void extractFallsBackToSlotRuleViolationForUnknownExtractionErrorCode() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"subscribe-incident\",\"error_message\":null}");
        LlmBackedPromptMetadataExtractor extractor = new LlmBackedPromptMetadataExtractor(
                new LlmScenarioRecognizer(llmClient),
                List.of(new ScenarioDefinition(
                        "subscribe-incident", "Incident subscription", "Subscribe incident", "Subscribe Incident")),
                "zh-CN",
                "Identify scenario.",
                "Choose scenario.",
                (scenarioCode, language) -> "template",
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                SLOT_NAME, true, "string", null, null, null, null, null, null, null))),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(
                        Map.of(SLOT_NAME, ""),
                        List.of(new StructuredSlotValidationError(SLOT_NAME, "data_problem", "whatever"))));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("bad prompt"));

        assertEquals("slot.rule_violation", error.getCode());
        assertEquals("slot_validation", error.getStage());
    }

    @Test
    void extractReturnsSlotNotExtractedWhenRequiredSlotIsMissing() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"subscribe-incident\",\"error_message\":null}");
        LlmBackedPromptMetadataExtractor extractor = new LlmBackedPromptMetadataExtractor(
                new LlmScenarioRecognizer(llmClient),
                List.of(new ScenarioDefinition(
                        "subscribe-incident", "Incident subscription", "Subscribe incident", "Subscribe Incident")),
                "zh-CN",
                "Identify scenario.",
                "Choose scenario.",
                (scenarioCode, language) -> "template",
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                SLOT_NAME, true, "string", null, null, null, null, null, null, null))),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(Map.of(), List.of()));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("bad prompt"));

        assertEquals("slot.not_provided", error.getCode());
        assertEquals("slot_validation", error.getStage());
        assertTrue(error.getMessage().contains(SLOT_NAME), "message must render the slot label");
    }

    @Test
    void extractReturnsSlotConstraintViolatedWhenValueIsOutsideAllowedValues() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"subscribe-incident\",\"error_message\":null}");
        LlmBackedPromptMetadataExtractor extractor = new LlmBackedPromptMetadataExtractor(
                new LlmScenarioRecognizer(llmClient),
                List.of(new ScenarioDefinition(
                        "subscribe-incident", "Incident subscription", "Subscribe incident", "Subscribe Incident")),
                "zh-CN",
                "Identify scenario.",
                "Choose scenario.",
                (scenarioCode, language) -> "template",
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                SLOT_NAME, true, "string", null, null, null, List.of("Incident"), null, null, null))),
                (userInput, scenarioCode, language) ->
                        new StructuredSlotExtractionResult(Map.of(SLOT_NAME, "Outage"), List.of()));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("bad prompt"));

        assertEquals("slot.constraint_violated", error.getCode());
        assertEquals("slot_validation", error.getStage());
        assertTrue(error.getMessage().contains("Outage"), "message must render the offending value");
        assertEquals(Map.of("slot_label", SLOT_NAME, "actual", "Outage"), error.getFacts());
    }

    @Test
    void extractPropagatesTemplateLoadErrorsAsGenerationFailures() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"subscribe-incident\",\"error_message\":null}");
        LlmBackedPromptMetadataExtractor extractor = new LlmBackedPromptMetadataExtractor(
                new LlmScenarioRecognizer(llmClient),
                List.of(new ScenarioDefinition(
                        "subscribe-incident", "Incident subscription", "Subscribe incident", "Subscribe Incident")),
                "zh-CN",
                "Identify scenario.",
                "Choose scenario.",
                (scenarioCode, language) -> {
                    throw new ResourceNotFoundException("Prompt resource file does not exist.", "template.md");
                },
                (scenarioCode, language) -> new PromptSlotSchema(scenarioCode, List.of()),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(Map.of(), List.of()));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("prompt"));

        assertEquals("template.not_found", error.getCode());
        assertEquals("generation", error.getStage());
        assertTrue(error.getMessage().contains("subscribe-incident"), "message must render the template URI");
        assertTrue(error.getMessage().contains("zh-CN"), "message must render the language");
    }

    @Test
    void extractWrapsUnexpectedTemplateLoadErrorsAsResourceReadFailures() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"subscribe-incident\",\"error_message\":null}");
        LlmBackedPromptMetadataExtractor extractor = new LlmBackedPromptMetadataExtractor(
                new LlmScenarioRecognizer(llmClient),
                List.of(new ScenarioDefinition(
                        "subscribe-incident", "Incident subscription", "Subscribe incident", "Subscribe Incident")),
                "zh-CN",
                "Identify scenario.",
                "Choose scenario.",
                (scenarioCode, language) -> {
                    throw new A2ATError("boom");
                },
                (scenarioCode, language) -> new PromptSlotSchema(scenarioCode, List.of()),
                (userInput, scenarioCode, language) -> new StructuredSlotExtractionResult(Map.of(), List.of()));

        PromptComplianceCheckException error =
                assertThrows(PromptComplianceCheckException.class, () -> extractor.extract("prompt"));

        assertEquals("infra.resource_read_failed", error.getCode());
        assertEquals("generation", error.getStage());
    }

    private static final class RecordingClient implements LLMClient {
        private final String payload;

        private RecordingClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }
}
