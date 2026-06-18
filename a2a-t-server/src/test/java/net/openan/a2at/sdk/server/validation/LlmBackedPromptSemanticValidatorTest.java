package net.openan.a2at.sdk.server.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.adapter.LLMAdapter;
import net.openan.a2at.sdk.llm.model.LLMResponse;
import net.openan.a2at.sdk.llm.model.LlmUsage;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotJsonSchemaLoader;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;
import org.junit.jupiter.api.Test;

class LlmBackedPromptSemanticValidatorTest {

    @Test
    void validatePassesWhenSemanticValidatorApprovesSlots() throws IOException {
        LLMClient llmClient = buildClient("{\"passed\":true,\"errors\":[]}");
        PromptSlotJsonSchemaLoader slotJsonSchemaLoader =
                (scenarioCode, language) -> Map.of("required", java.util.List.of("通知主题"));
        LlmBackedPromptSemanticValidator validator = new LlmBackedPromptSemanticValidator(
                llmClient, slotJsonSchemaLoader, "semantic system", "semantic user");

        assertDoesNotThrow(() -> validator.validate(
                "## 通知主题\nIncident\n",
                new ProcessedPromptMetadata(
                        "subscribe_incident", "zh-CN", "## 通知主题\n{{通知主题}}\n", Map.of("通知主题", "Incident"))));
    }

    @Test
    void validatePassesWhenSemanticValidatorReturnsFormattedJson() throws IOException {
        LLMClient llmClient = buildClient(
                """
                {
                  "passed": true,
                  "errors": []
                }
                """);
        PromptSlotJsonSchemaLoader slotJsonSchemaLoader =
                (scenarioCode, language) -> Map.of("required", java.util.List.of("通知主题"));
        LlmBackedPromptSemanticValidator validator = new LlmBackedPromptSemanticValidator(
                llmClient, slotJsonSchemaLoader, "semantic system", "semantic user");

        assertDoesNotThrow(() -> validator.validate(
                "## 通知主题\nIncident\n",
                new ProcessedPromptMetadata(
                        "subscribe_incident", "zh-CN", "## 通知主题\n{{通知主题}}\n", Map.of("通知主题", "Incident"))));
    }

    @Test
    void validateReturnsSlotValidationErrorWhenSemanticValidatorRejectsSlots() throws IOException {
        LLMClient llmClient = buildClient(
                "{\"passed\":false,\"errors\":[{\"slot_name\":\"通知主题\",\"code\":\"semantic_mismatch\",\"message\":\"通知主题不匹配\"}]}");
        PromptSlotJsonSchemaLoader slotJsonSchemaLoader =
                (scenarioCode, language) -> Map.of("required", java.util.List.of("通知主题"));
        LlmBackedPromptSemanticValidator validator = new LlmBackedPromptSemanticValidator(
                llmClient, slotJsonSchemaLoader, "semantic system", "semantic user");

        PromptComplianceCheckException error = assertThrows(
                PromptComplianceCheckException.class,
                () -> validator.validate(
                        "## 通知主题\nIncident\n",
                        new ProcessedPromptMetadata(
                                "subscribe_incident", "zh-CN", "## 通知主题\n{{通知主题}}\n", Map.of("通知主题", "Incident"))));

        assertEquals("slot_validation_error", error.code());
        assertEquals("slot_validation", error.stage());
    }

    private static LLMClient buildClient(String payload) throws Exception {
        Path envFile = Files.createTempFile("a2at-server-semantic-validator", ".env");
        Files.writeString(
                envFile,
                """
                A2AT_LLM_PROVIDER=openai_compatible
                A2AT_LLM_MODEL=test-model
                A2AT_LLM_API_KEY=test-key
                """);
        return new LLMClient(envFile, new RecordingAdapter(payload));
    }

    private static final class RecordingAdapter implements LLMAdapter {
        private final String payload;

        private RecordingAdapter(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(StructuredGenerationRequest request) {
            return new LLMResponse(payload, "test-model", new LlmUsage(1, 1, 2), Map.of());
        }
    }
}
