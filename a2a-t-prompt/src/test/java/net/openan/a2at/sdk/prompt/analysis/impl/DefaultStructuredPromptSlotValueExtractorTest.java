package net.openan.a2at.sdk.prompt.analysis.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        RecordingClient llmClient =
                new RecordingClient(
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
                                new PromptSlotDefinition("site", true, "string", null, null, null, null, null),
                                new PromptSlotDefinition(
                                        "additional_notes", false, "string", null, null, null, null, null),
                                new PromptSlotDefinition("limit", false, "integer", null, 1.0d, 10.0d, null, null),
                                new PromptSlotDefinition(
                                        "severity",
                                        false,
                                        "string",
                                        null,
                                        null,
                                        null,
                                        List.of("low", "medium", "high"),
                                        null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        StructuredSlotExtractionResult result =
                extractor.extractSlots("Analyze Site A with critical severity.", "energy_saving", "en-US");

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
        RecordingClient llmClient =
                new RecordingClient(
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
                                new PromptSlotDefinition("site", true, "string", null, null, null, null, null),
                                new PromptSlotDefinition(
                                        "additional_notes", false, "string", null, null, null, null, null))),
                "Extract slots from the input.",
                "Return slots as JSON.");

        StructuredSlotExtractionResult result =
                extractor.extractSlots("Analyze Site A with fallback note.", "energy_saving", "en-US");

        assertEquals(Map.of("site", "Site A", "additional_notes", "Need } fallback"), result.slots());
        assertEquals(List.of(), result.slotErrors());
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
