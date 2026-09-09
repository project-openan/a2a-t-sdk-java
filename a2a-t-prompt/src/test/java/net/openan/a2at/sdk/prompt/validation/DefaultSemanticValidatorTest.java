package net.openan.a2at.sdk.prompt.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.ValidationPipeline;
import net.openan.a2at.sdk.core.validation.ValidationResult;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.junit.jupiter.api.Test;

class DefaultSemanticValidatorTest {

    private static final String VALID_RESPONSE =
            """
            {
              "semantic_verdict": true,
              "errors": [],
              "params": {"site": "Site A"}
            }
            """;

    private static final String TEMPLATE_CONTENT = "dummy template content";

    @Test
    void validatesEnUSPromptAndExtractsParams() {
        RecordingClient llmClient = new RecordingClient(VALID_RESPONSE);

        DefaultSemanticValidator validator = new DefaultSemanticValidator(llmClient, "en-US");

        ValidationResult result = validator.validate(
                "Check Site A power usage.",
                Map.of("type", "object"),
                TemplateUri.of("Task-T", "ran-energy-saving"),
                TEMPLATE_CONTENT);

        assertTrue(result.verdict());
        assertEquals(Map.of("site", "Site A"), result.params());
        assertTrue(llmClient.lastSystemContent().contains("validation"));
    }

    @Test
    void loadsZhCnResourcesWithoutException() {
        DefaultSemanticValidator validator = new DefaultSemanticValidator(null, "zh-CN");

        assertNotNull(validator);
    }

    @Test
    void throwsResourceNotFoundExceptionForMissingLanguage() {
        ResourceNotFoundException exception =
                assertThrows(ResourceNotFoundException.class, () -> new DefaultSemanticValidator(null, "fr-FR"));

        assertTrue(exception.resourcePath().contains("content_validation"));
        assertTrue(exception.getMessage().contains("zh-CN or en-US"));
    }

    @Test
    void validateRetriesLlmInfrastructureErrorThroughPipeline() {
        FlakyClient flakyClient = new FlakyClient(1, VALID_RESPONSE);

        DefaultSemanticValidator validator = new DefaultSemanticValidator(flakyClient, "en-US");

        ValidationPipeline pipeline = new ValidationPipeline(prompt -> Map.of(), validator, 2, "en-US");

        FilledParamData result = pipeline.validate(
                "Check Site A power usage.",
                Map.of("type", "object"),
                TemplateUri.of("Task-T", "ran-energy-saving"),
                TEMPLATE_CONTENT);

        assertEquals(Map.of("site", "Site A"), result.data());
        assertEquals(2, flakyClient.invocations());
    }

    @Test
    void validateExhaustsRetriesAndWrapsLlmError() {
        FlakyClient flakyClient = new FlakyClient(
                Integer.MAX_VALUE,
                """
                {
                  "semantic_verdict": true,
                  "errors": [],
                  "params": {}
                }
                """);

        DefaultSemanticValidator validator = new DefaultSemanticValidator(flakyClient, "en-US");

        ValidationPipeline pipeline = new ValidationPipeline(prompt -> Map.of(), validator, 3, "en-US");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> pipeline.validate(
                        "Check Site A power usage.",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "ran-energy-saving"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.invocation_failed", exception.getCode());
        assertInstanceOf(LLMRuntimeError.class, exception.getCause());
        assertEquals(3, flakyClient.invocations());
    }

    @Test
    void pipelineCarriesNullParamsIntoFilledParamData() {
        // the semantic validator passed overall, but one schema slot is explicitly null: the merged
        // FilledParamData must carry the key with a null value so callers scanning for blank slots can see the
        // missing parameter and trigger negotiation for it
        String llmJson =
                "{\"semantic_verdict\": true, \"errors\": []," + "\"params\": {\"任务对象\": \"端口A\", \"任务上下文\": null}}";
        DefaultSemanticValidator validator = new DefaultSemanticValidator(new StubClient(llmJson), "zh-CN");

        ValidationPipeline pipeline = new ValidationPipeline(prompt -> Map.of(), validator, 2, "en-US");

        FilledParamData result = pipeline.validate(
                "task prompt", Map.of("type", "object"), TemplateUri.of("Task-T", "network-layer"), TEMPLATE_CONTENT);

        assertEquals(2, result.data().size());
        assertEquals("端口A", result.data().get("任务对象"));
        assertNull(result.data().get("任务上下文"));
    }

    @Test
    void validatePreservesNullParamValuesAsMissingSlots() {
        String llmJson =
                "{\"semantic_verdict\": true, \"errors\": []," + "\"params\": {\"任务对象\": \"端口A\", \"任务上下文\": null}}";
        DefaultSemanticValidator validator = new DefaultSemanticValidator(new StubClient(llmJson), "zh-CN");

        ValidationResult result = validator.validate(
                "task prompt", Map.of("type", "object"), TemplateUri.of("Task-T", "network-layer"), TEMPLATE_CONTENT);

        assertTrue(result.verdict());
        // a null param is the validator's signal that a schema slot is missing from the content; the key must stay
        // present with a null value so downstream missing-slot detection (negotiation triggering) can see it
        assertEquals(2, result.params().size());
        assertEquals("端口A", result.params().get("任务对象"));
        assertTrue(result.params().containsKey("任务上下文"));
        assertNull(result.params().get("任务上下文"));
    }

    @Test
    void missingVerdictKeyFailsWithInfrastructureError() {
        DefaultSemanticValidator validator =
                new DefaultSemanticValidator(new StubClient("{\"errors\": [], \"params\": {}}"), "en-US");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "network-layer"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.response_invalid", exception.getCode());
        assertTrue(exception.getCause().getMessage().contains("semantic_verdict"));
    }

    @Test
    void stringVerdictValueFailsWithInfrastructureError() {
        DefaultSemanticValidator validator = new DefaultSemanticValidator(
                new StubClient("{\"semantic_verdict\": \"true\", \"errors\": [], \"params\": {}}"), "en-US");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "network-layer"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.response_invalid", exception.getCode());
        assertTrue(exception.getCause().getMessage().contains("semantic_verdict"));
    }

    @Test
    void nonListErrorsFailsWithInfrastructureError() {
        DefaultSemanticValidator validator = new DefaultSemanticValidator(
                new StubClient("{\"semantic_verdict\": true, \"errors\": {}, \"params\": {}}"), "en-US");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "network-layer"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.response_invalid", exception.getCode());
        assertTrue(exception.getCause().getMessage().contains("errors"));
    }

    @Test
    void malformedErrorElementFailsWithInfrastructureError() {
        DefaultSemanticValidator validator = new DefaultSemanticValidator(
                new StubClient("{\"semantic_verdict\": false, \"errors\": [\"oops\"], \"params\": {}}"), "en-US");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "network-layer"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.response_invalid", exception.getCode());
        assertTrue(exception.getCause().getMessage().contains("errors"));
    }

    @Test
    void nonMapParamsFailsWithInfrastructureError() {
        DefaultSemanticValidator validator = new DefaultSemanticValidator(
                new StubClient("{\"semantic_verdict\": true, \"errors\": [], \"params\": []}"), "en-US");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "network-layer"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.response_invalid", exception.getCode());
        assertTrue(exception.getCause().getMessage().contains("params"));
    }

    @Test
    void stringParamKeysPassTheContract() {
        DefaultSemanticValidator validator = new DefaultSemanticValidator(
                new StubClient("{\"semantic_verdict\": true, \"errors\": [], \"params\": {\"site\": \"Site A\"}}"),
                "en-US");

        ValidationResult result = validator.validate(
                "task prompt", Map.of("type", "object"), TemplateUri.of("Task-T", "network-layer"), TEMPLATE_CONTENT);

        assertTrue(result.verdict());
    }

    @Test
    void nonStringParamKeyFailsWithInfrastructureError() {
        // JSON object keys are strings by definition, so the negative path is driven through a parser
        // stub that produces a non-string key
        Map<Object, Object> rawParams = new LinkedHashMap<>();
        rawParams.put(42, "Site A");
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("semantic_verdict", Boolean.TRUE);
        parsed.put("errors", List.of());
        parsed.put("params", rawParams);
        DefaultSemanticValidator validator = new DefaultSemanticValidator(
                new StubClient("payload content is irrelevant"), "en-US", prompt -> parsed);

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "network-layer"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.response_invalid", exception.getCode());
        assertTrue(exception.getCause().getMessage().contains("params keys"));
    }

    @Test
    void contractViolationRetriesAndRecoversThroughPipeline() {
        FlakyPayloadClient client = new FlakyPayloadClient(
                "{\"semantic_verdict\": \"true\", \"errors\": [], \"params\": {}}", VALID_RESPONSE);

        DefaultSemanticValidator validator = new DefaultSemanticValidator(client, "en-US");
        ValidationPipeline pipeline = new ValidationPipeline(prompt -> Map.of(), validator, 2, "en-US");

        FilledParamData result = pipeline.validate(
                "Check Site A power usage.",
                Map.of("type", "object"),
                TemplateUri.of("Task-T", "ran-energy-saving"),
                TEMPLATE_CONTENT);

        // first attempt violates the output contract (string verdict) → retryable infra error → second attempt
        // returns a compliant response and the pipeline succeeds
        assertEquals(Map.of("site", "Site A"), result.data());
        assertEquals(2, client.invocations());
    }

    @Test
    void contractViolationExhaustsRetriesThroughPipeline() {
        FlakyPayloadClient client =
                new FlakyPayloadClient("{\"semantic_verdict\": \"true\", \"errors\": [], \"params\": {}}", null);

        DefaultSemanticValidator validator = new DefaultSemanticValidator(client, "en-US");
        ValidationPipeline pipeline = new ValidationPipeline(prompt -> Map.of(), validator, 3, "en-US");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> pipeline.validate(
                        "Check Site A power usage.",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "ran-energy-saving"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.response_invalid", exception.getCode());
        assertEquals(3, client.invocations());
    }

    @Test
    void parsesErrorsWithFactsAndRendersTheMessage() {
        String llmJson = "{\"semantic_verdict\": false, \"errors\": [{\"slot_name\": \"subscriptionCondition\","
                + " \"code\": \"content.param_missing\", \"facts\": {\"section_label\": \"订阅条件\"}}],"
                + "\"params\": {}}";
        DefaultSemanticValidator validator = new DefaultSemanticValidator(new StubClient(llmJson), "zh-CN");

        ValidationResult result = validator.validate(
                "task prompt", Map.of("type", "object"), TemplateUri.of("Task-T", "network-layer"), TEMPLATE_CONTENT);

        assertFalse(result.verdict());
        assertEquals(1, result.errors().size());
        SlotValidationError error = result.errors().get(0);
        assertEquals("subscriptionCondition", error.slotName());
        assertEquals("content.param_missing", error.code());
        assertEquals("「订阅条件」未填写,请补充该参数的取值", error.message());
        assertEquals(Map.of("section_label", "订阅条件"), error.facts());
    }

    @Test
    void rendersEntryFieldMissingMessageInEnglish() {
        String llmJson = "{\"semantic_verdict\": false, \"errors\": [{\"slot_name\": \"policyList\","
                + " \"code\": \"content.entry_field_missing\","
                + " \"facts\": {\"section_label\": \"Policy List\", \"index\": 2,"
                + " \"field_label\": \"Effective Date\"}}], \"params\": {}}";
        DefaultSemanticValidator validator = new DefaultSemanticValidator(new StubClient(llmJson), "en-US");

        ValidationResult result = validator.validate(
                "task prompt", Map.of("type", "object"), TemplateUri.of("Task-T", "network-layer"), TEMPLATE_CONTENT);

        assertEquals(1, result.errors().size());
        SlotValidationError error = result.errors().get(0);
        assertEquals("content.entry_field_missing", error.code());
        assertEquals("Entry 2 of 'Policy List' is missing required field 'Effective Date'", error.message());
    }

    @Test
    void unknownErrorCodeFallsBackToContentRuleViolation() {
        String llmJson = "{\"semantic_verdict\": false, \"errors\": [{\"slot_name\": \"policyList\","
                + " \"code\": \"data_problem\", \"facts\": {\"section_label\": \"Policy List\"}}],"
                + "\"params\": {}}";
        DefaultSemanticValidator validator = new DefaultSemanticValidator(new StubClient(llmJson), "en-US");

        ValidationResult result = validator.validate(
                "task prompt", Map.of("type", "object"), TemplateUri.of("Task-T", "network-layer"), TEMPLATE_CONTENT);

        assertEquals(1, result.errors().size());
        SlotValidationError error = result.errors().get(0);
        assertEquals("content.rule_violation", error.code());
        assertEquals("The value of 'policyList' violates the validation rules.", error.message());
        assertEquals(Map.of("section_label", "policyList"), error.facts());
    }

    @Test
    void missingLlmClientFailsWithLlmNotConfigured() {
        DefaultSemanticValidator validator = new DefaultSemanticValidator(null, "zh-CN");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "network-layer"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.not_configured", exception.getCode());
        assertEquals("未配置 LLM 客户端,无法执行该操作(请检查 A2AT_LLM_* 配置)", exception.getMessage());
    }

    @Test
    void malformedErrorFactsFailsWithResponseInvalid() {
        DefaultSemanticValidator validator = new DefaultSemanticValidator(
                new StubClient("{\"semantic_verdict\": false, \"errors\": [{\"slot_name\": \"site\", \"code\":"
                        + " \"content.param_missing\"}], \"params\": {}}"),
                "en-US");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Task-T", "network-layer"),
                        TEMPLATE_CONTENT));

        assertEquals("llm.response_invalid", exception.getCode());
        assertTrue(exception.getCause().getMessage().contains("facts"));
    }

    @Test
    void templateContentWithPlaceholderLiteralIsNotSecondOrderReplaced() {
        String templateContentWithPlaceholder = "Template body with [extension_name] and [input] literals.";
        RecordingClient recordingClient = new RecordingClient(VALID_RESPONSE);
        DefaultSemanticValidator validator = new DefaultSemanticValidator(recordingClient, "en-US");

        validator.validate(
                "Check Site A power usage.",
                Map.of("type", "object"),
                TemplateUri.of("Task-T", "network-layer"),
                templateContentWithPlaceholder);

        String userContent = recordingClient.userContent();
        assertTrue(userContent.contains("Template body with [extension_name] and [input] literals."));
        assertTrue(userContent.contains("Task-T"));
        assertTrue(userContent.contains("Check Site A power usage"));
    }

    @Test
    void templateContentPlaceholderIsFilled() {
        RecordingClient recordingClient = new RecordingClient(VALID_RESPONSE);
        DefaultSemanticValidator validator = new DefaultSemanticValidator(recordingClient, "en-US");

        validator.validate(
                "Check Site A power usage.",
                Map.of("type", "object"),
                TemplateUri.of("Task-T", "network-layer"),
                "## Energy Saving Template\n\nCheck the energy saving region.");

        String userContent = recordingClient.userContent();
        assertTrue(userContent.contains("## Energy Saving Template"));
        assertTrue(userContent.contains("energy saving region"));
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

        String lastSystemContent() {
            for (Map<String, String> message : lastMessages) {
                if ("system".equals(message.get("role"))) {
                    return message.get("content");
                }
            }
            return "";
        }

        String userContent() {
            for (Map<String, String> message : lastMessages) {
                if ("user".equals(message.get("role"))) {
                    return message.get("content");
                }
            }
            return "";
        }
    }

    private static final class FlakyClient implements LLMClient {

        private final int failuresToSimulate;

        private final String successPayload;

        private final AtomicInteger counter = new AtomicInteger(0);

        private FlakyClient(int failuresToSimulate, String successPayload) {
            this.failuresToSimulate = failuresToSimulate;
            this.successPayload = successPayload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            int invocation = counter.incrementAndGet();
            if (invocation <= failuresToSimulate) {
                throw new LLMRuntimeError("network timeout");
            }
            return new LLMResponse(
                    successPayload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }

        int invocations() {
            return counter.get();
        }
    }

    /** Stub LLM client returning a fixed content payload. */
    private static final class StubClient implements LLMClient {

        private final String content;

        private StubClient(String content) {
            this.content = content;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse(content, "stub-llm", Map.of(), Map.of());
        }
    }

    /**
     * LLM client returning a malformed payload first, then a compliant one (or always malformed when success is null).
     */
    private static final class FlakyPayloadClient implements LLMClient {

        private final String malformedPayload;

        private final String successPayload;

        private final AtomicInteger counter = new AtomicInteger(0);

        private FlakyPayloadClient(String malformedPayload, String successPayload) {
            this.malformedPayload = malformedPayload;
            this.successPayload = successPayload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            int invocation = counter.incrementAndGet();
            if (invocation == 1 || successPayload == null) {
                return new LLMResponse(malformedPayload, "test-model", Map.of(), Map.of());
            }
            return new LLMResponse(successPayload, "test-model", Map.of(), Map.of());
        }

        int invocations() {
            return counter.get();
        }
    }
}
