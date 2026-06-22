package net.openan.a2at.sdk.client.prompt.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import org.junit.jupiter.api.Test;

class DefaultStructuredClientSlotValueExtractorTest {

    @Test
    void extractSlotsBuildsStructuredPromptAndReturnsNormalizedSlots() {
        RecordingClient llmClient = new RecordingClient(
                "{\"slots\":{\"site\":\"Site A\",\"additional_notes\":null,\"limit\":\"5\",\"severity\":\"high\"},"
                        + "\"slot_errors\":[]}");
        DefaultStructuredClientSlotValueExtractor extractor = new DefaultStructuredClientSlotValueExtractor(
                llmClient,
                (scenarioCode, language) -> new PromptSlotSchema(
                        scenarioCode,
                        List.of(
                                new PromptSlotDefinition("site", true, "string", "^Site .+", null, null, null, null),
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

        Map<String, String> slots = extractor.extractSlots(
                "Analyze Site A with critical severity.",
                "energy_saving",
                "en-US",
                "Site: {site}\nNotes: {additional_notes}\nLimit: {limit}\nSeverity: {severity}");

        assertEquals(
                Map.of(
                        "site", "Site A",
                        "additional_notes", "",
                        "limit", "5",
                        "severity", "high"),
                slots);
        assertEquals(2, llmClient.lastMessages().size());
        assertEquals("system", llmClient.lastMessages().get(0).get("role"));
        assertTrue(llmClient.lastMessages().get(1).get("content").contains("energy_saving"));
        assertTrue(llmClient.lastMessages().get(1).get("content").contains("Analyze Site A with critical severity."));
        assertTrue(llmClient.lastSchema().containsKey("required"));
    }

    private static final class RecordingClient implements LLMClient {

        private final String payload;

        private List<Map<String, String>> lastMessages;

        private Map<String, Object> lastSchema;

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
            this.lastSchema = jsonSchema;
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }

        private List<Map<String, String>> lastMessages() {
            return lastMessages;
        }

        private Map<String, Object> lastSchema() {
            return lastSchema;
        }
    }
}
