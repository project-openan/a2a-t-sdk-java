package net.openan.a2at.sdk.client.prompt.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.orchestration.DefaultClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.model.PromptGenerationConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import org.junit.jupiter.api.Test;

class ClientPromptAssemblyPackageStructureTest {

    @Test
    void defaultAssemblyStillBuildsAndGeneratesPrompt() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"energy_saving\",\"error_message\":null}",
                "{\"slots\":{\"site\":\"Site A\",\"additional_notes\":\"critical\",\"limit\":\"5\",\"severity\":\"high\"},\"slot_errors\":[]}");

        DefaultClientPromptGenerationOrchestrator orchestrator =
                DefaultClientPromptGenerationOrchestratorFactory.create(
                        llmClient,
                        List.of(new ScenarioDefinition(
                                "energy_saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                        new PromptGenerationConfig(
                                "en-US",
                                "Identify the best matching scenario.",
                                "Choose from the provided scenario list.",
                                "Extract slots from the input.",
                                "Return slots as JSON."));

        PromptGenerationResult result = orchestrator.generateTaskPrompt(Map.of(
                "site", "Site A",
                "additional_notes", "critical",
                "limit", "5",
                "severity", "high"));

        assertTrue(result.success());
        assertEquals(
                normalizeLineEndings("Site: Site A\nNotes: critical"),
                normalizeLineEndings(result.promptText().trim()));
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n");
    }

    private static final class RecordingClient implements LLMClient {

        private final java.util.List<String> payloads;

        private RecordingClient(String... payloads) {
            this.payloads = new java.util.ArrayList<>(java.util.List.of(payloads));
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse(
                    payloads.remove(0), "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }
}
