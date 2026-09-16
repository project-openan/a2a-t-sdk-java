package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.junit.jupiter.api.Test;

/**
 * Locks the classpath-only loading of the negotiation LLM prompt resources.
 *
 * <p>The LLM prompt resources are deliberately not configurable: the system prompt sent to the LLM is always the
 * built-in classpath prompt of the configured language. Both prompt-consuming steps are covered: the content extraction
 * of the from-text generation and the semantic validation of the parameter extraction pipeline.
 */
class CustomRootPromptsIgnoredTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final String EXTRACTION_RESPONSE =
            "{\"items\":[{\"name\":\"节能区域\",\"value\":\"松山湖\"}],\"relationship\":null}";

    private static final String SEMANTIC_RESPONSE =
            "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                    + "\"params\":{\"region\":\"松山湖\"}}";

    @Test
    void contentExtractionKeepsUsingTheBuiltInSystemPrompt() {
        RecordingLlmClient llm = new RecordingLlmClient(EXTRACTION_RESPONSE);
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        NegotiationContext context = new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE);

        MetadataContent result = orchestrator.generateProposeFromText("请提供节能区域。", context, INFORMATION_PROPOSE_URI);

        assertEquals(1, llm.callCount());
        String builtinSystemPrompt =
                new NegotiationPromptResourceLoader().loadSystem("information_negotiation", "zh-CN");
        assertEquals(
                builtinSystemPrompt,
                llm.systemContentOfLastCall(),
                "the system prompt sent to the LLM must be the built-in classpath prompt");
        assertTrue(llm.userContentOfLastCall().contains("请提供节能区域。"), "the user prompt must carry the input text");
        assertTrue(result.promptText().contains("节能区域"));
    }

    @Test
    void semanticValidationKeepsUsingTheBuiltInSystemPrompt() {
        RecordingLlmClient llm = new RecordingLlmClient(EXTRACTION_RESPONSE, SEMANTIC_RESPONSE);
        NegotiationGenerationOrchestrator orchestrator = orchestrator(llm);
        MetadataContent message = orchestrator.generateProposeFromText(
                "请提供节能区域。",
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                INFORMATION_PROPOSE_URI);

        FilledParamData filled = orchestrator.validateProposePromptAndDataFilling(
                message.promptText(),
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                Map.of("type", "object", "properties", Map.of("region", Map.of("type", "string"))),
                INFORMATION_PROPOSE_URI);

        assertEquals(2, llm.callCount());
        String builtinSystemPrompt =
                new NegotiationPromptResourceLoader().loadSystem("negotiation_semantic_validation", "zh-CN");
        assertEquals(
                builtinSystemPrompt,
                llm.systemContentOfLastCall(),
                "the semantic validation system prompt must be the built-in classpath prompt");
        assertEquals("松山湖", filled.data().get("region"));
    }

    private NegotiationGenerationOrchestrator orchestrator(RecordingLlmClient llm) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();
    }

    private static final class RecordingLlmClient implements LLMClient {

        private final List<String> payloads;

        private final ArrayList<List<Map<String, String>>> recordedMessages = new ArrayList<>();

        private RecordingLlmClient(String... payloads) {
            this.payloads = List.of(payloads);
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            recordedMessages.add(messages);
            String payload = payloads.get(Math.min(recordedMessages.size() - 1, payloads.size() - 1));
            return new LLMResponse(
                    payload, "recording-test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }

        int callCount() {
            return recordedMessages.size();
        }

        String systemContentOfLastCall() {
            return roleContentOfLastCall("system");
        }

        String userContentOfLastCall() {
            return roleContentOfLastCall("user");
        }

        private String roleContentOfLastCall(String role) {
            List<Map<String, String>> messages = recordedMessages.get(recordedMessages.size() - 1);
            return messages.stream()
                    .filter(message -> role.equals(message.get("role")))
                    .map(message -> message.get("content"))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
