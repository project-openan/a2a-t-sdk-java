package net.openan.a2at.sdk.prompt.analysis.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.analysis.model.StructuredSlotExtractionResult;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import org.junit.jupiter.api.Test;

class DefaultStructuredPromptSlotValueExtractorTest {

    @Test
    void extractSlotsParsesFormattedStructuredJsonPayload() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A",
                    "additional_notes": null,
                    "limit": "5",
                    "severity": "high"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(
                                new PromptSlotDefinition(
                                        "site", true, "string", null, null, null, null, null, null, null),
                                new PromptSlotDefinition(
                                        "additional_notes", false, "string", null, null, null, null, null, null, null),
                                new PromptSlotDefinition(
                                        "limit", false, "integer", null, 1.0d, 10.0d, null, null, null, null),
                                new PromptSlotDefinition(
                                        "severity",
                                        false,
                                        "string",
                                        null,
                                        null,
                                        null,
                                        List.of("low", "medium", "high"),
                                        null,
                                        null,
                                        null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        StructuredSlotExtractionResult result =
                extractor.extractSlots("Analyze Site A with critical severity.", "ran-energy-saving", "en-US");

        assertEquals(
                Map.of(
                        "site", "Site A",
                        "additional_notes", "",
                        "limit", "5",
                        "severity", "high"),
                result.slots());
        assertEquals(List.of(), result.slotErrors());
    }

    @Test
    void extractSlotsPreservesSlotTextContainingClosingBrace() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A",
                    "additional_notes": "Need } fallback"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(
                                new PromptSlotDefinition(
                                        "site", true, "string", null, null, null, null, null, null, null),
                                new PromptSlotDefinition(
                                        "additional_notes",
                                        false,
                                        "string",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        StructuredSlotExtractionResult result =
                extractor.extractSlots("Analyze Site A with fallback note.", "ran-energy-saving", "en-US");

        assertEquals(Map.of("site", "Site A", "additional_notes", "Need } fallback"), result.slots());
        assertEquals(List.of(), result.slotErrors());
    }

    @Test
    void should_injectDataSchemaSection_When_schemaIsProvided() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                "site", true, "string", null, null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        Map<String, Object> dataSchema = Map.of(
                "type", "object",
                "properties",
                        Map.of(
                                "site", Map.of("type", "string", "description", "The target site name"),
                                "severity", Map.of("type", "string", "description", "The severity level")),
                "required", List.of("site"));

        extractor.extractSlots("Analyze Site A.", "ran-energy-saving", "en-US", dataSchema);

        String userMessage = llmClient.lastUserContent();
        assertTrue(userMessage.contains("[data_schema]"));
        assertTrue(userMessage.contains("\"type\":\"object\""));
        assertTrue(userMessage.contains("\"properties\""));
        assertTrue(userMessage.contains("\"The target site name\""));
    }

    @Test
    void should_notIncludeDataSchemaSection_When_schemaIsNull() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                "site", true, "string", null, null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        extractor.extractSlots("Analyze Site A.", "ran-energy-saving", "en-US", null);

        String userMessage = llmClient.lastUserContent();
        assertFalse(userMessage.contains("[data_schema]"));
    }

    @Test
    void should_notIncludeDataSchemaSection_When_schemaIsEmpty() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                "site", true, "string", null, null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        extractor.extractSlots("Analyze Site A.", "ran-energy-saving", "en-US", Map.of());

        String userMessage = llmClient.lastUserContent();
        assertFalse(userMessage.contains("[data_schema]"));
    }

    @Test
    void buildMessagesIncludesFullSlotDescriptions() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                "site",
                                true,
                                "string",
                                null,
                                null,
                                null,
                                List.of("Site A", "Site B"),
                                null,
                                "The target site name",
                                "The site must be a physical location"))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        extractor.extractSlots("Analyze Site A.", "ran-energy-saving", "en-US");

        String userMessage = llmClient.lastUserContent();
        assertTrue(userMessage.contains("[slots]"));
        assertTrue(userMessage.contains("\"name\""));
        assertTrue(userMessage.contains("\"site\""));
        assertTrue(userMessage.contains("\"required\""));
        assertTrue(userMessage.contains("\"type\""));
        assertTrue(userMessage.contains("\"string\""));
        assertTrue(userMessage.contains("\"description\""));
        assertTrue(userMessage.contains("\"The target site name\""));
        assertTrue(userMessage.contains("\"enum\""));
        assertTrue(userMessage.contains("\"Site A\""));
        assertTrue(userMessage.contains("\"x-a2at-value-constraint\""));
        assertTrue(userMessage.contains("\"The site must be a physical location\""));
        assertTrue(userMessage.contains("["));
    }

    @Test
    void defaultExtractSlotsWithSchemaDelegatesToThreeArg() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": []
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                "site", true, "string", null, null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        PromptSlotValueExtractor lambdaExtractor = (input, code, lang) -> extractor.extractSlots(input, code, lang);

        StructuredSlotExtractionResult result =
                lambdaExtractor.extractSlots("Analyze Site A.", "ran-energy-saving", "en-US", Map.of("site", "desc"));

        String userMessage = llmClient.lastUserContent();
        assertFalse(userMessage.contains("[data_schema]"));
        assertEquals(Map.of("site", "Site A"), result.slots());
    }

    @Test
    void slotErrorsAreParsedWithFactsAndRenderedMessage() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": [
                    {"slot_name": "subscriptionCondition", "code": "slot.not_provided", "facts": {"slot_label": "订购条件"}},
                    {"slot_name": "operationType", "code": "slot.constraint_violated", "facts": {"slot_label": "操作类型", "actual": "导出"}}
                  ]
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                "site", true, "string", null, null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        StructuredSlotExtractionResult result = extractor.extractSlots("Analyze Site A.", "ran-energy-saving", "zh-CN");

        assertEquals(2, result.slotErrors().size());
        assertEquals("slot.not_provided", result.slotErrors().get(0).code());
        assertEquals("输入中未提供「订购条件」。", result.slotErrors().get(0).message());
        assertEquals(Map.of("slot_label", "订购条件"), result.slotErrors().get(0).facts());
        assertEquals("slot.constraint_violated", result.slotErrors().get(1).code());
        assertEquals("「操作类型」的取值「导出」不在允许范围内", result.slotErrors().get(1).message());
        assertEquals(
                Map.of("slot_label", "操作类型", "actual", "导出"),
                result.slotErrors().get(1).facts());
    }

    @Test
    void unknownSlotErrorCodeFallsBackToSlotRuleViolation() {
        RecordingClient llmClient = new RecordingClient(
                """
                {
                  "slots": {
                    "site": "Site A"
                  },
                  "slot_errors": [
                    {"slot_name": "operationType", "code": "data_problem", "facts": {"slot_label": "Operation Type"}}
                  ]
                }
                """);
        DefaultStructuredPromptSlotValueExtractor extractor = new DefaultStructuredPromptSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(new PromptSlotDefinition(
                                "site", true, "string", null, null, null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        StructuredSlotExtractionResult result = extractor.extractSlots("Analyze Site A.", "ran-energy-saving", "en-US");

        assertEquals(1, result.slotErrors().size());
        assertEquals("slot.rule_violation", result.slotErrors().get(0).code());
        assertEquals(
                "The value of 'operationType' violates the validation rules.",
                result.slotErrors().get(0).message());
        assertEquals(
                Map.of("slot_label", "operationType"),
                result.slotErrors().get(0).facts());
    }

    private static final class RecordingClient implements LLMClient {

        private final String payload;

        private List<Map<String, String>> lastMessages;

        private RecordingClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            this.lastMessages = messages;
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }

        String lastUserContent() {
            for (Map<String, String> message : lastMessages) {
                if ("user".equals(message.get("role"))) {
                    return message.get("content");
                }
            }
            return "";
        }
    }
}
