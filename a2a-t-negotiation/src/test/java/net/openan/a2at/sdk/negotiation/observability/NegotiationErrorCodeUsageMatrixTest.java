package net.openan.a2at.sdk.negotiation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMError;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.validation.NegotiationSemanticValidator;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Drives every row of the negotiation error-code usage matrix through the real pipelines.
 *
 * <p>Each row triggers one failure condition of the documented matrix and asserts the exception type, the public error
 * code, the number of LLM calls (encoding retryability: a retryable failure consumes all attempts, a non-retryable
 * failure exactly one or zero), and that a structured detail identifying the problem field or slot is carried.
 */
class NegotiationErrorCodeUsageMatrixTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final TemplateUri INFORMATION_ENDING_URI = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

    private static final String VALID_CONTEXT_PROMPT = "## 协商上下文\n- id: " + UUID + "\n- round: 1\n- maxRounds: 5";

    private static final Map<String, Object> SCHEMA = Map.of("type", "object");

    private static final NegotiationContext CONTEXT =
            new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE);

    @TestFactory
    Stream<DynamicTest> everyErrorCodeRowOfTheMatrixBehavesAsPinned() {
        return Stream.of(
                row(
                        "generation_template_missing",
                        "not-a-json",
                        0,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .templateLoader(missingTemplateLoader())
                                .build()
                                .generateProposeFromData(proposeData(), INFORMATION_PROPOSE_URI),
                        NegotiationGenerationException.class,
                        ErrorCatalog.TEMPLATE_NOT_FOUND.getCode()),
                row(
                        "generation_response_invalid_is_retryable",
                        "not-a-json-object",
                        2,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(2)
                                .build()
                                .generateProposeFromText(
                                        "请提供节能区域。",
                                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                        INFORMATION_PROPOSE_URI),
                        NegotiationGenerationException.class,
                        ErrorCatalog.LLM_RESPONSE_INVALID.getCode()),
                row(
                        "generation_slot_missing_is_not_retryable",
                        "{}",
                        1,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(3)
                                .build()
                                .generateProposeFromText(
                                        "请提供节能区域。",
                                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                        INFORMATION_PROPOSE_URI),
                        NegotiationGenerationException.class,
                        ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode()),
                row(
                        "generation_phase_conclusion_mismatch_is_conclusion_mismatch",
                        "{\"conclusion\":\"Reject\",\"items\":[{\"name\":\"区域\",\"value\":\"松山湖\"}]}",
                        1,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(3)
                                .build()
                                .generateAcceptFromText(
                                        "确认提供区域。",
                                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.ACCEPT),
                                        INFORMATION_ENDING_URI),
                        NegotiationGenerationException.class,
                        ErrorCatalog.NEGOTIATION_CONCLUSION_MISMATCH.getCode()),
                row(
                        "generation_target_confirm_request_round_with_conditional_sections_is_invalid_input",
                        "{\"target_negotiation_description\":\"目标已经澄清，请确认。\",\"intent_understanding\":"
                                + "[{\"name\":\"发起方理解\",\"value\":\"对方希望降低节能力度\"}],"
                                + "\"target_confirm_request\":\"目标已经澄清，是否同意按照此目标继续执行？\"}",
                        1,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(3)
                                .build()
                                .generateProposeFromText(
                                        "目标已经澄清，请确认。",
                                        new NegotiationContext(UUID, 3, 5, NegotiationPerformative.PROPOSE),
                                        StandardTemplates.TARGET_NEGOTIATION_PROPOSE),
                        NegotiationGenerationException.class,
                        ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode()),
                row(
                        "generation_feasibility_confirm_request_round_with_infeasibility_details_is_invalid_input",
                        "{\"feasibility_negotiation_description\":\"评估完成，结论可行，请确认。\",\"action\":"
                                + "\"REQUEST_FEASIBILITY_EVALUATION\",\"infeasibility_details_and_proposal\":"
                                + "[{\"name\":\"不应出现\",\"value\":\"值\"}],\"feasibility_confirm_request\":"
                                + "\"目标评估为可行，是否同意按照此目标继续执行？\"}",
                        1,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(3)
                                .build()
                                .generateProposeFromText(
                                        "评估完成，结论可行，请确认。",
                                        new NegotiationContext(UUID, 3, 5, NegotiationPerformative.PROPOSE),
                                        StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE),
                        NegotiationGenerationException.class,
                        ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode()),
                row(
                        "generation_feasibility_confirm_request_round_with_alternative_action_is_invalid_input",
                        "{\"feasibility_negotiation_description\":\"评估完成，结论可行，请确认。\",\"action\":"
                                + "\"PROPOSE_ALTERNATIVE_ON_FAILURE\",\"feasibility_confirm_request\":"
                                + "\"目标评估为可行，是否同意按照此目标继续执行？\"}",
                        1,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(3)
                                .build()
                                .generateProposeFromText(
                                        "评估完成，结论可行，请确认。",
                                        new NegotiationContext(UUID, 3, 5, NegotiationPerformative.PROPOSE),
                                        StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE),
                        NegotiationGenerationException.class,
                        ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode()),
                row(
                        "generation_llm_infrastructure_failure_is_retryable",
                        null,
                        2,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(2)
                                .build()
                                .generateProposeFromText(
                                        "请提供节能区域。",
                                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                        INFORMATION_PROPOSE_URI),
                        NegotiationGenerationException.class,
                        ErrorCatalog.LLM_INVOCATION_FAILED.getCode()),
                row(
                        "param_non_negotiation_input",
                        semanticPayload("information"),
                        0,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .build()
                                .validateProposePromptAndDataFilling(
                                        "plain text without any negotiation section",
                                        null,
                                        SCHEMA,
                                        INFORMATION_PROPOSE_URI),
                        NegotiationParamExtractionException.class,
                        ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode()),
                row(
                        "param_rule_violation",
                        semanticPayload("information"),
                        0,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .build()
                                .validateProposePromptAndDataFilling(
                                        "## 所需信息项\n1. 区域\n",
                                        new NegotiationContext(UUID, 9, 5, NegotiationPerformative.PROPOSE),
                                        SCHEMA,
                                        INFORMATION_PROPOSE_URI),
                        NegotiationParamExtractionException.class,
                        ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode()),
                row(
                        "param_semantic_rejection",
                        "{\"semantic_verdict\":false,\"negotiation_type\":null,\"errors\":[{\"slot_name\":"
                                + "\"section.info_static\",\"code\":\"negotiation.type_mismatch\",\"facts\":"
                                + "{\"implied\":\"information\",\"declared\":\"information\"}}],\"params\":{}}",
                        1,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(3)
                                .build()
                                .validateProposePromptAndDataFilling(
                                        VALID_CONTEXT_PROMPT, CONTEXT, SCHEMA, INFORMATION_PROPOSE_URI),
                        NegotiationParamExtractionException.class,
                        ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode()),
                row(
                        "param_llm_infrastructure_failure_is_retryable",
                        null,
                        2,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(2)
                                .build()
                                .validateProposePromptAndDataFilling(
                                        VALID_CONTEXT_PROMPT, CONTEXT, SCHEMA, INFORMATION_PROPOSE_URI),
                        NegotiationParamExtractionException.class,
                        ErrorCatalog.LLM_INVOCATION_FAILED.getCode()),
                row(
                        "entry_programming_error_carries_no_code_but_the_offending_argument",
                        "not-a-json",
                        0,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .build()
                                .generateProposeFromData(proposeData(), StandardTemplates.ENERGY_SAVING),
                        IllegalArgumentException.class,
                        null),
                row(
                        "internal_render_failure_is_wrapped_as_render_failed",
                        "not-a-json",
                        0,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .templateLoader(nullContentTemplateLoader())
                                .build()
                                .generateProposeFromData(proposeData(), INFORMATION_PROPOSE_URI),
                        NegotiationGenerationException.class,
                        ErrorCatalog.TEMPLATE_RENDER_FAILED.getCode()),
                row(
                        "internal_semantic_shape_violation_is_retryable_infrastructure",
                        "{\"semantic_verdict\":true,\"errors\":[],\"params\":{}}",
                        2,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .maxAttempts(2)
                                .build()
                                .validateProposePromptAndDataFilling(
                                        VALID_CONTEXT_PROMPT, CONTEXT, SCHEMA, INFORMATION_PROPOSE_URI),
                        NegotiationParamExtractionException.class,
                        ErrorCatalog.LLM_RESPONSE_INVALID.getCode()),
                row(
                        "param_prompt_resource_missing_is_template_not_found",
                        semanticPayload("information"),
                        0,
                        llm -> () -> NegotiationGenerationOrchestratorBuilder.builder()
                                .language("zh-CN")
                                .llmClient(llm)
                                .semanticValidator(throwingSemanticValidator())
                                .build()
                                .validateProposePromptAndDataFilling(
                                        VALID_CONTEXT_PROMPT, CONTEXT, SCHEMA, INFORMATION_PROPOSE_URI),
                        NegotiationParamExtractionException.class,
                        ErrorCatalog.TEMPLATE_NOT_FOUND.getCode()));
    }

    private static DynamicTest row(
            String name,
            String llmPayload,
            int expectedLlmCalls,
            Scenario scenario,
            Class<? extends RuntimeException> expectedType,
            String expectedCode) {
        return DynamicTest.dynamicTest(
                name, () -> verify(name, llmPayload, expectedLlmCalls, scenario, expectedType, expectedCode));
    }

    private static void verify(
            String name,
            String llmPayload,
            int expectedLlmCalls,
            Scenario scenario,
            Class<? extends RuntimeException> expectedType,
            String expectedCode) {
        CountingClient llm = new CountingClient(llmPayload);
        RuntimeException failure = runExpectingFailure(scenario.action(llm), name, expectedType);

        assertTrue(
                expectedType.isInstance(failure),
                "row " + name + " must fail with " + expectedType.getSimpleName() + " but failed with "
                        + failure.getClass().getSimpleName());
        assertEquals(expectedLlmCalls, llm.calls.get(), "row " + name + " LLM call count");
        assertTrue(
                failure.getMessage() != null && !failure.getMessage().isBlank(),
                "row " + name + " must carry a non-blank message");

        if (failure instanceof IllegalArgumentException argumentFailure) {
            assertFalse(
                    A2ATError.class.isInstance(argumentFailure),
                    "row " + name + " must stay outside A2ATError because it carries no code");
            assertNullCode(expectedCode, name);
            assertTrue(
                    argumentFailure.getMessage().contains("Template URI does not address"),
                    "row " + name + " must name the offending argument in its message but was: "
                            + argumentFailure.getMessage());
            return;
        }
        if (failure instanceof NegotiationGenerationException generationFailure) {
            assertEquals(expectedCode, generationFailure.getCode(), "row " + name + " public code");
            return;
        }
        if (failure instanceof NegotiationParamExtractionException extractionFailure) {
            assertEquals(expectedCode, extractionFailure.getCode(), "row " + name + " public code");
            boolean expectsSlotDetails = ErrorCatalog.NEGOTIATION_RULE_VIOLATION
                            .getCode()
                            .equals(expectedCode)
                    || ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode().equals(expectedCode)
                    || ErrorCatalog.LLM_INVOCATION_FAILED.getCode().equals(expectedCode)
                    || ErrorCatalog.LLM_RESPONSE_INVALID.getCode().equals(expectedCode);
            if (expectsSlotDetails) {
                assertFalse(
                        extractionFailure.getErrors().isEmpty(),
                        "row " + name + " must carry non-empty structured error details");
                assertTrue(
                        extractionFailure.getErrors().stream()
                                .allMatch(error -> error.slotName() != null
                                        && !error.slotName().isBlank()),
                        "row " + name + " error details must carry slot names");
            }
            return;
        }
        fail("row " + name + " failed with an unexpected exception type: "
                + failure.getClass().getName());
    }

    private static void assertNullCode(String expectedCode, String name) {
        assertNull(expectedCode, "row " + name + " carries no public code by design");
    }

    private static RuntimeException runExpectingFailure(
            Runnable action, String name, Class<? extends RuntimeException> expectedType) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            return failure;
        }
        fail("row " + name + " was expected to fail with " + expectedType.getSimpleName() + " but completed");
        return null;
    }

    private static NegotiationProposeData proposeData() {
        return new NegotiationProposeData(
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null));
    }

    private static String semanticPayload(String negotiationType) {
        return "{\"semantic_verdict\":true,\"negotiation_type\":\"" + negotiationType + "\",\"errors\":[],"
                + "\"params\":{\"region\":\"松山湖\"}}";
    }

    private static NegotiationTemplateLoader missingTemplateLoader() {
        return new NegotiationTemplateLoader() {
            @Override
            public PromptTemplate load(NegotiationReference reference) {
                throw new ResourceNotFoundException("Negotiation template does not exist.", reference.uri());
            }
        };
    }

    private static NegotiationTemplateLoader nullContentTemplateLoader() {
        return new NegotiationTemplateLoader() {
            @Override
            public PromptTemplate load(NegotiationReference reference) {
                return new PromptTemplate(reference.templateUri(), "broken template", null, "classpath");
            }
        };
    }

    private static NegotiationSemanticValidator throwingSemanticValidator() {
        return (prompt, callerSchema, reference, templateContent) -> {
            throw new ResourceNotFoundException(
                    "Semantic validation prompt does not exist.", "prompt_resources/prompts");
        };
    }

    @FunctionalInterface
    private interface Scenario {

        Runnable action(CountingClient llm);
    }

    private static final class CountingClient implements LLMClient {

        private final AtomicInteger calls = new AtomicInteger();

        private final String payload;

        private CountingClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls.incrementAndGet();
            if (payload == null) {
                throw new LLMError("LLM endpoint unavailable.");
            }
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }
}
