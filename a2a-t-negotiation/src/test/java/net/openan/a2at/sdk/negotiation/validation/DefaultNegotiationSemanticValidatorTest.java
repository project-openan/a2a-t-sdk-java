package net.openan.a2at.sdk.negotiation.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.junit.jupiter.api.Test;

class DefaultNegotiationSemanticValidatorTest {

    private static final String VALID_PROMPT = "## Negotiation Context\n"
            + "- id: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3\n"
            + "- round: 1\n"
            + "- maxRounds: 5\n\n"
            + "## Required Information Items\n"
            + "1. energy saving region: provide a real region\n";

    private static final String TEMPLATE_CONTENT = "dummy template content";

    private final RecordingSchemaBuilder schemaBuilder = new RecordingSchemaBuilder();

    private final RecordingLLMClient llmClient = new RecordingLLMClient();

    private final DefaultNegotiationSemanticValidator validator =
            new DefaultNegotiationSemanticValidator(llmClient, schemaBuilder::buildSemanticValidationSchema);

    private final NegotiationReference informationReference =
            new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "en-US");

    private final Map<String, Object> callerSchema =
            Map.of("type", "object", "properties", Map.of("confirmed_rate_mbps", Map.of("type", "integer")));

    @Test
    void validResponseYieldsVerdictTypeErrorsAndParams() {
        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\","
                + "\"errors\":[],\"params\":{\"confirmed_rate_mbps\":2}}";

        SemanticValidationResult result =
                validator.validateNegotiation(VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT);

        assertTrue(result.verdict());
        assertEquals("information", result.negotiationType());
        assertEquals(List.of(), result.errors());
        assertEquals(Map.of("confirmed_rate_mbps", 2), result.params());
        assertEquals(1, llmClient.invocations);
        assertEquals(callerSchema, schemaBuilder.lastCallerSchema);
    }

    @Test
    void rejectMessageParamsCarryTheReasonsOfNonProvision() {
        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\","
                + "\"errors\":[],\"params\":{\"energy saving region\":\"site inventory unavailable, cannot provide\"}}";
        Map<String, Object> rejectSchema =
                Map.of("type", "object", "properties", Map.of("energy saving region", Map.of("type", "string")));
        NegotiationReference rejectReference =
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.REJECT, "en-US");

        SemanticValidationResult result =
                validator.validateNegotiation(VALID_PROMPT, rejectSchema, rejectReference, TEMPLATE_CONTENT);

        assertTrue(result.verdict());
        assertEquals(Map.of("energy saving region", "site inventory unavailable, cannot provide"), result.params());
    }

    @Test
    void englishSystemPromptCarriesTheRejectReasonExtractionRule() {
        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}";
        NegotiationReference rejectReference =
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.REJECT, "en-US");

        validator.validateNegotiation(VALID_PROMPT, callerSchema, rejectReference, TEMPLATE_CONTENT);

        String systemPrompt = llmClient.lastMessages.get(0).get("content");
        assertTrue(systemPrompt.contains("reason of non-provision stated for that field"));
    }

    @Test
    void chineseSystemPromptCarriesTheRejectReasonExtractionRule() {
        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}";
        NegotiationReference rejectReference =
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.REJECT, "zh-CN");

        validator.validateNegotiation(VALID_PROMPT, callerSchema, rejectReference, TEMPLATE_CONTENT);

        String systemPrompt = llmClient.lastMessages.get(0).get("content");
        assertTrue(systemPrompt.contains("无法提供的原因文本"));
    }

    @Test
    void proposeMessageParamsCarryTheFullExpectationText() {
        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\","
                + "\"errors\":[],\"params\":{\"energy saving region\":\"energy saving area information, e.g. Songshanhu\"}}";
        Map<String, Object> proposeSchema =
                Map.of("type", "object", "properties", Map.of("energy saving region", Map.of("type", "string")));
        NegotiationReference proposeReference =
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "en-US");

        SemanticValidationResult result =
                validator.validateNegotiation(VALID_PROMPT, proposeSchema, proposeReference, TEMPLATE_CONTENT);

        assertTrue(result.verdict());
        assertEquals(
                Map.of("energy saving region", "energy saving area information, e.g. Songshanhu"), result.params());
    }

    @Test
    void englishSystemPromptCarriesTheProposeExpectationExtractionRule() {
        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}";

        validator.validateNegotiation(VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT);

        String systemPrompt = llmClient.lastMessages.get(0).get("content");
        assertTrue(systemPrompt.contains("the full expectation text stated for that field"));
        assertTrue(systemPrompt.contains("never treat a sample as the field's supplied value"));
    }

    @Test
    void chineseSystemPromptCarriesTheProposeExpectationExtractionRule() {
        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}";
        NegotiationReference proposeReference =
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN");

        validator.validateNegotiation(VALID_PROMPT, callerSchema, proposeReference, TEMPLATE_CONTENT);

        String systemPrompt = llmClient.lastMessages.get(0).get("content");
        assertTrue(systemPrompt.contains("完整期望内容原文"));
        assertTrue(systemPrompt.contains("不得将样例当作该字段已提供的真实值"));
    }

    @Test
    void singleStructuredCallReceivesMergedSchemaAndFilledUserPrompt() {
        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\"," + "\"errors\":[],\"params\":{}}";

        validator.validateNegotiation(VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT);

        List<Map<String, String>> messages = llmClient.lastMessages;
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        assertTrue(messages.get(0).get("content").contains("semantic validation"));
        assertEquals("user", messages.get(1).get("role"));
        String userPrompt = messages.get(1).get("content");
        assertTrue(userPrompt.contains("information"));
        assertTrue(userPrompt.contains("propose"));
        assertTrue(userPrompt.contains(TEMPLATE_CONTENT));
        assertTrue(userPrompt.contains(StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri()));
        assertTrue(userPrompt.contains("confirmed_rate_mbps"));
        assertTrue(userPrompt.contains("energy saving region"));
        assertEquals(Map.of("merged", true), llmClient.lastSchema);
        assertNull(llmClient.lastTemperature);
        assertNull(llmClient.lastMaxTokens);
    }

    @Test
    void missingNegotiationTypeKeyIsAShapeViolation() {
        llmClient.payload = "{\"semantic_verdict\":true,\"errors\":[],\"params\":{}}";

        NegotiationValidationException exception = assertThrows(
                NegotiationValidationException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));

        assertTrue(exception.getMessage().contains("negotiation_type"));
    }

    @Test
    void missingOtherRequiredKeysAreShapeViolations() {
        llmClient.payload = "{\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));

        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));

        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[]}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));
    }

    @Test
    void wrongShapesAreShapeViolations() {
        llmClient.payload =
                "{\"semantic_verdict\":\"yes\",\"negotiation_type\":\"information\"," + "\"errors\":[],\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));

        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\","
                + "\"errors\":\"none\",\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));

        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\"," + "\"errors\":[],\"params\":[]}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));

        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\","
                + "\"errors\":[{\"slot_name\":\"section.context\"}],\"params\":{}}";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));

        llmClient.payload = "not json at all";
        assertThrows(
                NegotiationValidationException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));
    }

    @Test
    void nullTypeWithTrueVerdictIsTurnedIntoSemanticRejection() {
        llmClient.payload = "{\"semantic_verdict\":true,\"negotiation_type\":null,\"errors\":[],\"params\":{}}";

        SemanticValidationResult result =
                validator.validateNegotiation(VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT);

        assertFalse(result.verdict());
        assertNull(result.negotiationType());
        assertEquals(1, result.errors().size());
        assertEquals("section.info_static", result.errors().get(0).slotName());
        assertEquals("negotiation.type_mismatch", result.errors().get(0).code());
    }

    @Test
    void typeMismatchWithTrueVerdictIsTurnedIntoSemanticRejection() {
        llmClient.payload =
                "{\"semantic_verdict\":true,\"negotiation_type\":\"target\"," + "\"errors\":[],\"params\":{}}";

        SemanticValidationResult result =
                validator.validateNegotiation(VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT);

        assertFalse(result.verdict());
        assertEquals("target", result.negotiationType());
        assertEquals(1, result.errors().size());
        assertEquals("section.target", result.errors().get(0).slotName());
        assertEquals("negotiation.type_mismatch", result.errors().get(0).code());
        assertTrue(result.errors().get(0).message().contains("information"));
    }

    @Test
    void negativeVerdictPassesThroughWithTypeNullAndErrors() {
        llmClient.payload = "{\"semantic_verdict\":false,\"negotiation_type\":null,"
                + "\"errors\":[{\"slot_name\":\"section.target_result_content\","
                + "\"code\":\"negotiation.conclusion_content_mismatch\",\"facts\":{\"conclusion\":\"Accept\","
                + "\"section_label\":\"section.target_result_content\"}}],\"params\":{}}";

        SemanticValidationResult result =
                validator.validateNegotiation(VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT);

        assertFalse(result.verdict());
        assertNull(result.negotiationType());
        assertEquals(1, result.errors().size());
        assertEquals("section.target_result_content", result.errors().get(0).slotName());
        assertEquals(
                "negotiation.conclusion_content_mismatch",
                result.errors().get(0).code());
        assertEquals("Accept", result.errors().get(0).facts().get("conclusion"));
        assertTrue(result.errors().get(0).message().contains("Accept"));
    }

    @Test
    void unknownCodeIsMappedToTheNegotiationFallbackCode() {
        llmClient.payload = "{\"semantic_verdict\":false,\"negotiation_type\":null,"
                + "\"errors\":[{\"slot_name\":\"section.info_static\",\"code\":\"data_problem\","
                + "\"facts\":{\"reason\":\"irrelevant\"}}],\"params\":{}}";

        SemanticValidationResult result =
                validator.validateNegotiation(VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT);

        assertFalse(result.verdict());
        assertEquals(1, result.errors().size());
        assertEquals("negotiation.rule_violation", result.errors().get(0).code());
        assertEquals("section.info_static", result.errors().get(0).facts().get("section_label"));
        assertTrue(result.errors().get(0).message().contains("section.info_static"));
    }

    @Test
    void llmTransportFailurePropagatesForTheOrchestrationLayerToMap() {
        llmClient.failure = new LLMRuntimeError("invocation failed");

        LLMRuntimeError error = assertThrows(
                LLMRuntimeError.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, informationReference, TEMPLATE_CONTENT));

        assertTrue(error.getMessage().contains("invocation failed"));
    }

    @Test
    void missingPromptResourceForLanguageSurfacesResourceNotFound() {
        NegotiationReference unsupportedLanguageReference =
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "fr-FR");

        assertThrows(
                ResourceNotFoundException.class,
                () -> validator.validateNegotiation(
                        VALID_PROMPT, callerSchema, unsupportedLanguageReference, TEMPLATE_CONTENT));
        assertEquals(0, llmClient.invocations);
    }

    private static final class RecordingLLMClient implements LLMClient {

        private String payload = "{}";

        private RuntimeException failure;

        private int invocations;

        private List<Map<String, String>> lastMessages;

        private Map<String, Object> lastSchema;

        private Double lastTemperature;

        private Integer lastMaxTokens;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            invocations++;
            lastMessages = messages;
            lastSchema = jsonSchema;
            lastTemperature = temperature;
            lastMaxTokens = maxTokens;
            if (failure != null) {
                throw failure;
            }
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }

    private static final class RecordingSchemaBuilder {

        private Map<String, Object> lastCallerSchema;

        public Map<String, Object> buildSemanticValidationSchema(Map<String, Object> callerSchema) {
            lastCallerSchema = callerSchema;
            return Map.of("merged", true);
        }

        @Test
        @SuppressWarnings("unchecked")
        void semanticValidationSchemaMergesTheCallerSchemaIntoAFourKeyContract() {
            Map<String, Object> callerSchema =
                    Map.of("type", "object", "properties", Map.of("energy_rate", Map.of("type", "number")));

            Map<String, Object> schema =
                    DefaultNegotiationSemanticValidator.buildSemanticValidationSchema(callerSchema);

            assertEquals("object", schema.get("type"));
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            assertEquals(
                    List.of("semantic_verdict", "negotiation_type", "errors", "params"),
                    List.copyOf(properties.keySet()));
            assertEquals(List.of("semantic_verdict", "negotiation_type", "errors", "params"), schema.get("required"));
            assertEquals(Boolean.FALSE, schema.get("additionalProperties"));

            assertEquals(Map.of("type", "boolean"), properties.get("semantic_verdict"));

            Map<String, Object> negotiationType = (Map<String, Object>) properties.get("negotiation_type");
            assertEquals(List.of("string", "null"), negotiationType.get("type"));
            List<Object> typeEnum = (List<Object>) negotiationType.get("enum");
            assertEquals(4, typeEnum.size());
            assertTrue(typeEnum.contains("information"));
            assertTrue(typeEnum.contains("target"));
            assertTrue(typeEnum.contains("feasibility"));
            assertTrue(typeEnum.contains(null));

            Map<String, Object> errors = (Map<String, Object>) properties.get("errors");
            assertEquals("array", errors.get("type"));
            Map<String, Object> errorItem = (Map<String, Object>) errors.get("items");
            assertEquals(
                    List.of("slot_name", "code", "facts"),
                    List.copyOf(((Map<String, Object>) errorItem.get("properties")).keySet()));
            assertEquals(List.of("slot_name", "code", "facts"), errorItem.get("required"));

            assertEquals(callerSchema, properties.get("params"));
        }

        @Test
        void semanticValidationSchemaWrapsCallerSchemaWithoutTypeKeyword() {
            Map<String, Object> callerSchema = new java.util.LinkedHashMap<>();
            callerSchema.put("properties", Map.of("id", Map.of("type", "string")));

            Map<String, Object> schema =
                    DefaultNegotiationSemanticValidator.buildSemanticValidationSchema(callerSchema);

            Map<?, ?> params = (Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("params");
            assertEquals("object", params.get("type"));
            assertEquals(Map.of("id", Map.of("type", "string")), params.get("properties"));
            assertNull(params.get("additionalProperties"));
        }

        @Test
        void rejectsNullCallerSchema() {
            assertEquals(
                    "Caller parameter schema must not be null.",
                    assertThrows(
                                    NullPointerException.class,
                                    () -> DefaultNegotiationSemanticValidator.buildSemanticValidationSchema(null))
                            .getMessage());
        }
    }
}
