package net.openan.a2at.sdk.prompt.analysis.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.json.JsonValueParser;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.analysis.exception.ScenarioRecognitionException;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import org.junit.jupiter.api.Test;

class ScenarioRecognizerTest {

    @Test
    void recognizeBuildsStructuredMessagesAndReturnsMatchedScenario() {
        RecordingClient llmClient =
                new RecordingClient("{\"matched\":true,\"scenario_code\":\"energy_saving\",\"error_message\":null}");

        ScenarioRecognizer recognizer = new ScenarioRecognizer(llmClient);
        ScenarioRecognitionResult result = recognizer.recognize(
                "Please analyze site A energy usage.",
                List.of(new ScenarioDefinition(
                        "energy_saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.");

        assertTrue(result.matched());
        assertEquals("energy_saving", result.scenarioCode());
        assertEquals(2, llmClient.lastMessages().size());
        assertEquals("system", llmClient.lastMessages().get(0).get("role"));
        assertTrue(llmClient.lastMessages().get(1).get("content").contains("energy_saving"));
        assertTrue(llmClient.lastSchema().containsKey("required"));
    }

    @Test
    void recognizeRejectsMatchedPayloadWithoutScenarioCode() {
        LLMClient llmClient =
                new RecordingClient("{\"matched\":true,\"scenario_code\":null,\"error_message\":null}");

        ScenarioRecognizer recognizer = new ScenarioRecognizer(llmClient);

        assertThrows(
                ScenarioRecognitionException.class,
                () -> recognizer.recognize(
                        "Analyze site A energy usage.",
                        List.of(new ScenarioDefinition(
                                "energy_saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                        "Identify the best matching scenario.",
                        "Choose from the provided scenario list."));
    }

    @Test
    void recognizeCanUseSharedJsonParserAbstraction() {
        LLMClient llmClient = new RecordingClient("ignored");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("matched", true);
        payload.put("scenario_code", "energy_saving");
        payload.put("error_message", null);
        RecordingJsonValueParser parser = new RecordingJsonValueParser(payload);

        ScenarioRecognizer recognizer = new ScenarioRecognizer(llmClient, parser);
        ScenarioRecognitionResult result = recognizer.recognize(
                "Please analyze site A energy usage.",
                List.of(new ScenarioDefinition(
                        "energy_saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.");

        assertTrue(result.matched());
        assertEquals("energy_saving", result.scenarioCode());
        assertEquals("ignored", parser.lastPayload);
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

        List<Map<String, String>> lastMessages() {
            return lastMessages;
        }

        Map<String, Object> lastSchema() {
            return lastSchema;
        }
    }

    private static final class RecordingJsonValueParser implements JsonValueParser {
        private final Map<String, Object> result;
        private String lastPayload;

        private RecordingJsonValueParser(Map<String, Object> result) {
            this.result = result;
        }

        @Override
        public Map<String, Object> parseObject(String payload) {
            this.lastPayload = payload;
            return result;
        }
    }
}
