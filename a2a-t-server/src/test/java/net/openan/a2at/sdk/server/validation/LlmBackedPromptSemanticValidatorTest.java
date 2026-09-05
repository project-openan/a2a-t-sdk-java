package net.openan.a2at.sdk.server.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;
import org.junit.jupiter.api.Test;

class LlmBackedPromptSemanticValidatorTest {

    private static final String SLOT_NAME = "notification_topic";

    private static final PromptSlotSchemaLoader SLOT_SCHEMA_LOADER = (scenarioCode, language) -> new PromptSlotSchema(
            scenarioCode,
            List.of(new PromptSlotDefinition(SLOT_NAME, true, "string", null, null, null, null, null, null, null)));

    @Test
    void validatePassesWhenSemanticValidatorApprovesSlots() {
        LLMClient llmClient = new RecordingClient("{\"passed\":true,\"errors\":[]}");
        LlmBackedPromptSemanticValidator validator =
                new LlmBackedPromptSemanticValidator(llmClient, SLOT_SCHEMA_LOADER, "semantic system", "semantic user");

        assertDoesNotThrow(() -> validator.validate(
                "## notification_topic\nIncident\n",
                new ProcessedPromptMetadata(
                        "subscribe-incident",
                        "zh-CN",
                        "## notification_topic\n{{notification_topic}}\n",
                        Map.of(SLOT_NAME, "Incident"))));
    }

    @Test
    void validatePassesWhenSemanticValidatorReturnsFormattedJson() {
        LLMClient llmClient = new RecordingClient(
                """
                {
                  "passed": true,
                  "errors": []
                }
                """);
        LlmBackedPromptSemanticValidator validator =
                new LlmBackedPromptSemanticValidator(llmClient, SLOT_SCHEMA_LOADER, "semantic system", "semantic user");

        assertDoesNotThrow(() -> validator.validate(
                "## notification_topic\nIncident\n",
                new ProcessedPromptMetadata(
                        "subscribe-incident",
                        "zh-CN",
                        "## notification_topic\n{{notification_topic}}\n",
                        Map.of(SLOT_NAME, "Incident"))));
    }

    @Test
    void validateReturnsCatalogCodeWhenSemanticValidatorRejectsSlots() {
        LLMClient llmClient = new RecordingClient("{\"passed\":false,\"errors\":[{\"slot_name\":\"notification_topic\","
                + "\"code\":\"slot.semantic_conflict\","
                + "\"facts\":{\"slot_label\":\"notification_topic\",\"reason\":\"topic does not match\"}}]}");
        LlmBackedPromptSemanticValidator validator =
                new LlmBackedPromptSemanticValidator(llmClient, SLOT_SCHEMA_LOADER, "semantic system", "semantic user");

        PromptComplianceCheckException error = assertThrows(
                PromptComplianceCheckException.class,
                () -> validator.validate(
                        "## notification_topic\nIncident\n",
                        new ProcessedPromptMetadata(
                                "subscribe-incident",
                                "zh-CN",
                                "## notification_topic\n{{notification_topic}}\n",
                                Map.of(SLOT_NAME, "Incident"))));

        assertEquals("slot.semantic_conflict", error.getCode());
        assertEquals("slot_validation", error.getStage());
        assertTrue(error.getMessage().contains("notification_topic"), "message must render the slot label");
        assertTrue(error.getMessage().contains("topic does not match"), "message must render the conflict reason");
        assertEquals(Map.of("slot_label", "notification_topic", "reason", "topic does not match"), error.getFacts());
    }

    @Test
    void validateMapsUnknownCodeToSlotRuleViolationFallback() {
        LLMClient llmClient = new RecordingClient("{\"passed\":false,\"errors\":[{\"slot_name\":\"notification_topic\","
                + "\"code\":\"data_problem\","
                + "\"facts\":{\"slot_label\":\"notification_topic\"}}]}");
        LlmBackedPromptSemanticValidator validator =
                new LlmBackedPromptSemanticValidator(llmClient, SLOT_SCHEMA_LOADER, "semantic system", "semantic user");

        PromptComplianceCheckException error = assertThrows(
                PromptComplianceCheckException.class,
                () -> validator.validate(
                        "## notification_topic\nIncident\n",
                        new ProcessedPromptMetadata(
                                "subscribe-incident",
                                "zh-CN",
                                "## notification_topic\n{{notification_topic}}\n",
                                Map.of(SLOT_NAME, "Incident"))));

        assertEquals("slot.rule_violation", error.getCode());
        assertEquals("slot_validation", error.getStage());
        assertTrue(error.getMessage().contains("notification_topic"), "message must render the slot label");
    }

    @Test
    void validateRejectsWhenPassedTrueButErrorsNonEmpty() {
        LLMClient llmClient = new RecordingClient("{\"passed\":true,\"errors\":[{\"slot_name\":\"notification_topic\","
                + "\"code\":\"slot.semantic_conflict\","
                + "\"facts\":{\"slot_label\":\"notification_topic\",\"reason\":\"topic does not match\"}}]}");
        LlmBackedPromptSemanticValidator validator =
                new LlmBackedPromptSemanticValidator(llmClient, SLOT_SCHEMA_LOADER, "semantic system", "semantic user");

        PromptComplianceCheckException error = assertThrows(
                PromptComplianceCheckException.class,
                () -> validator.validate(
                        "## notification_topic\nIncident\n",
                        new ProcessedPromptMetadata(
                                "subscribe-incident",
                                "zh-CN",
                                "## notification_topic\n{{notification_topic}}\n",
                                Map.of(SLOT_NAME, "Incident"))));

        assertEquals("slot.semantic_conflict", error.getCode());
        assertEquals("slot_validation", error.getStage());
        assertTrue(error.getMessage().contains("topic does not match"), "message must render the conflict reason");
    }

    @Test
    void validatePassesWhenPassedTrueAndErrorsMissing() {
        LLMClient llmClient = new RecordingClient("{\"passed\":true}");
        LlmBackedPromptSemanticValidator validator =
                new LlmBackedPromptSemanticValidator(llmClient, SLOT_SCHEMA_LOADER, "semantic system", "semantic user");

        assertDoesNotThrow(() -> validator.validate(
                "## notification_topic\nIncident\n",
                new ProcessedPromptMetadata(
                        "subscribe-incident",
                        "zh-CN",
                        "## notification_topic\n{{notification_topic}}\n",
                        Map.of(SLOT_NAME, "Incident"))));
    }

    @Test
    void validatePassesWhenPassedTrueAndErrorsNull() {
        LLMClient llmClient = new RecordingClient("{\"passed\":true,\"errors\":null}");
        LlmBackedPromptSemanticValidator validator =
                new LlmBackedPromptSemanticValidator(llmClient, SLOT_SCHEMA_LOADER, "semantic system", "semantic user");

        assertDoesNotThrow(() -> validator.validate(
                "## notification_topic\nIncident\n",
                new ProcessedPromptMetadata(
                        "subscribe-incident",
                        "zh-CN",
                        "## notification_topic\n{{notification_topic}}\n",
                        Map.of(SLOT_NAME, "Incident"))));
    }

    @Test
    void validateRejectsWithResponseInvalidWhenPassedFalseWithEmptyErrors() {
        LLMClient llmClient = new RecordingClient("{\"passed\":false,\"errors\":[]}");
        LlmBackedPromptSemanticValidator validator =
                new LlmBackedPromptSemanticValidator(llmClient, SLOT_SCHEMA_LOADER, "semantic system", "semantic user");

        PromptComplianceCheckException error = assertThrows(
                PromptComplianceCheckException.class,
                () -> validator.validate(
                        "## notification_topic\nIncident\n",
                        new ProcessedPromptMetadata(
                                "subscribe-incident",
                                "zh-CN",
                                "## notification_topic\n{{notification_topic}}\n",
                                Map.of(SLOT_NAME, "Incident"))));

        assertEquals("llm.response_invalid", error.getCode());
        assertEquals("slot_validation", error.getStage());
        assertEquals(Map.of("step", "semantic_validation"), error.getFacts());
    }

    @Test
    void validateRejectsWithResponseInvalidWhenPayloadIsNotJson() {
        LLMClient llmClient = new RecordingClient("not-json");
        LlmBackedPromptSemanticValidator validator =
                new LlmBackedPromptSemanticValidator(llmClient, SLOT_SCHEMA_LOADER, "semantic system", "semantic user");

        PromptComplianceCheckException error = assertThrows(
                PromptComplianceCheckException.class,
                () -> validator.validate(
                        "## notification_topic\nIncident\n",
                        new ProcessedPromptMetadata(
                                "subscribe-incident",
                                "zh-CN",
                                "## notification_topic\n{{notification_topic}}\n",
                                Map.of(SLOT_NAME, "Incident"))));

        assertEquals("llm.response_invalid", error.getCode());
        assertEquals("slot_validation", error.getStage());
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
