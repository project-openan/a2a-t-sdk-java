package net.openan.a2at.sdk.client.prompt.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.extractor.ClientSlotValueExtractor;
import net.openan.a2at.sdk.client.prompt.loader.ClientTemplateLoader;
import net.openan.a2at.sdk.client.prompt.orchestration.DefaultClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.client.prompt.recognition.ClientScenarioRecognizer;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.api.TaskPromptRenderer;
import org.junit.jupiter.api.Test;

class ClientPromptGenerationOrchestratorBuilderTest {

    @Test
    void buildCreatesStructuredDefaultPromptGenerationAssembly() {
        RecordingClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"energy_saving\",\"error_message\":null}",
                "{\"slots\":{\"site\":\"Site A\",\"additional_notes\":\"critical\",\"limit\":\"5\",\"severity\":\"high\"},\"slot_errors\":[]}");

        DefaultClientPromptGenerationOrchestrator orchestrator = ClientPromptGenerationOrchestratorBuilder.builder()
                .llmClient(llmClient)
                .scenarios(List.of(new ScenarioDefinition(
                        "energy_saving", "Energy Saving", "Energy analysis", "Analyze site power")))
                .language("en-US")
                .scenarioSystemPrompt("Identify the best matching scenario.")
                .scenarioUserPrompt("Choose from the provided scenario list.")
                .slotSystemPrompt("Extract slots from the input.")
                .slotUserPrompt("Return slots as JSON.")
                .build();

        PromptGenerationResult result = orchestrator.generateTaskPrompt(Map.of(
                "site", "Site A",
                "additional_notes", "critical",
                "limit", "5",
                "severity", "high"));

        assertTrue(result.success());
        assertEquals(
                normalizeLineEndings("Site: Site A\nNotes: critical"),
                normalizeLineEndings(result.promptText().trim()));
        assertEquals(2, llmClient.requestCount);
    }

    @Test
    void buildUsesExplicitCollaboratorsWhenProvided() {
        RecordingScenarioRecognizer scenarioRecognizer =
                new RecordingScenarioRecognizer(new ScenarioRecognitionResult(true, "energy_saving", null));
        RecordingTemplateLoader templateLoader = new RecordingTemplateLoader("Site: {site}\nNotes: {additional_notes}");
        RecordingSlotValueExtractor slotValueExtractor =
                new RecordingSlotValueExtractor(Map.of("site", "Site B", "additional_notes", "follow-up"));

        DefaultClientPromptGenerationOrchestrator orchestrator = ClientPromptGenerationOrchestratorBuilder.builder()
                .llmClient(new RecordingClient())
                .scenarios(List.of(new ScenarioDefinition(
                        "energy_saving", "Energy Saving", "Energy analysis", "Analyze site power")))
                .language("zh-CN")
                .scenarioSystemPrompt("scenario-system")
                .scenarioUserPrompt("scenario-user")
                .slotSystemPrompt("slot-system")
                .slotUserPrompt("slot-user")
                .scenarioRecognizer(scenarioRecognizer)
                .templateLoader(templateLoader)
                .slotValueExtractor(slotValueExtractor)
                .renderer(new TaskPromptRenderer())
                .build();

        PromptGenerationResult result = orchestrator.generateTaskPrompt("Analyze Site B.");

        assertTrue(result.success());
        assertEquals("Site: Site B\nNotes: follow-up", result.promptText());
        assertEquals("Analyze Site B.", scenarioRecognizer.lastInput);
        assertEquals("scenario-system", scenarioRecognizer.lastSystemPrompt);
        assertEquals("scenario-user", scenarioRecognizer.lastUserPrompt);
        assertEquals("energy_saving", templateLoader.lastScenarioCode);
        assertEquals("zh-CN", templateLoader.lastLanguage);
        assertSame("Analyze Site B.", slotValueExtractor.lastUserInput);
        assertEquals("energy_saving", slotValueExtractor.lastScenarioCode);
        assertEquals("zh-CN", slotValueExtractor.lastLanguage);
        assertEquals(
                normalizeLineEndings("Site: {site}\nNotes: {additional_notes}"),
                normalizeLineEndings(slotValueExtractor.lastTemplateText));
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n");
    }

    private static final class RecordingClient implements LLMClient {

        private final java.util.List<String> payloads;

        private int requestCount;

        private RecordingClient(String... payloads) {
            this.payloads = new java.util.ArrayList<>(java.util.List.of(payloads));
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            requestCount++;
            String payload = payloads.isEmpty() ? "{}" : payloads.remove(0);
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }

    private static final class RecordingScenarioRecognizer implements ClientScenarioRecognizer {

        private final ScenarioRecognitionResult result;

        private String lastInput;

        private String lastSystemPrompt;

        private String lastUserPrompt;

        private RecordingScenarioRecognizer(ScenarioRecognitionResult result) {
            this.result = result;
        }

        @Override
        public ScenarioRecognitionResult recognize(
                String normalizedInput, List<ScenarioDefinition> scenarios, String systemPrompt, String userPrompt) {
            this.lastInput = normalizedInput;
            this.lastSystemPrompt = systemPrompt;
            this.lastUserPrompt = userPrompt;
            return result;
        }
    }

    private static final class RecordingTemplateLoader implements ClientTemplateLoader {

        private final String templateText;

        private String lastScenarioCode;

        private String lastLanguage;

        private RecordingTemplateLoader(String templateText) {
            this.templateText = templateText;
        }

        @Override
        public String loadTemplate(String scenarioCode, String language) {
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            return templateText;
        }
    }

    private static final class RecordingSlotValueExtractor implements ClientSlotValueExtractor {

        private final Map<String, String> slotValues;

        private Object lastUserInput;

        private String lastScenarioCode;

        private String lastLanguage;

        private String lastTemplateText;

        private RecordingSlotValueExtractor(Map<String, String> slotValues) {
            this.slotValues = slotValues;
        }

        @Override
        public Map<String, String> extractSlots(
                Object userInput, String scenarioCode, String language, String templateText) {
            this.lastUserInput = userInput;
            this.lastScenarioCode = scenarioCode;
            this.lastLanguage = language;
            this.lastTemplateText = templateText;
            return slotValues;
        }
    }
}
