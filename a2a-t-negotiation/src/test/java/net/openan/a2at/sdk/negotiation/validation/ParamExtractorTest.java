package net.openan.a2at.sdk.negotiation.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.validation.SemanticValidator;
import net.openan.a2at.sdk.core.validation.TemplateContentLoader;
import net.openan.a2at.sdk.core.validation.ValidationPipeline;
import net.openan.a2at.sdk.core.validation.ValidationResult;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ParamExtractorTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final NegotiationContext CONTEXT =
            new NegotiationContext(SESSION_ID, 2, 5, NegotiationPerformative.PROPOSE);

    private static final String VALID_ZH_PROMPT = "## 所需信息项\n" + "1. 节能区域信息：请提供真实存在的区域\n";

    private static final NegotiationReference REFERENCE =
            new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN");

    private static final int MAX_ATTEMPTS = 1;

    private static final String TEMPLATE_CONTENT = "dummy template content";

    private final StubComplianceChecker complianceChecker = new StubComplianceChecker();

    private final StubSemanticValidator semanticValidator = new StubSemanticValidator();

    private final StubTemplateContentLoader templateContentLoader = new StubTemplateContentLoader();

    private final ParamExtractor extractor =
            new ParamExtractor(complianceChecker, semanticValidator, MAX_ATTEMPTS, templateContentLoader);

    @Test
    void happyPathMergesContextParamsFirstAndLetsContextWinOnConflict() {
        complianceChecker.result = new NegotiationRuleCheckResult(true, List.of());
        semanticValidator.result = new SemanticValidationResult(
                true,
                "information",
                List.of(),
                Map.of("id", "llm-value", "confirmed_rate_mbps", 2, "nested", Map.of("a", 1)));

        FilledParamData filled = extractor.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE);

        assertEquals(SESSION_ID, filled.data().get("id"));
        assertEquals(2, filled.data().get("round"));
        assertEquals(5, filled.data().get("maxRounds"));
        assertEquals(2, filled.data().get("confirmed_rate_mbps"));
        assertEquals(Map.of("a", 1), filled.data().get("nested"));
        assertEquals(5, filled.data().size());
        assertEquals(1, semanticValidator.invocations);
        assertEquals(1, templateContentLoader.invocations);
        assertEquals(TEMPLATE_CONTENT, semanticValidator.lastTemplateContent);
    }

    @Test
    void extractRequiresIntegerRoundAndMaxRoundsInMergedData() {
        complianceChecker.result = new NegotiationRuleCheckResult(true, List.of());
        semanticValidator.result = new SemanticValidationResult(true, "information", List.of(), Map.of());

        FilledParamData filled = extractor.extract(
                VALID_ZH_PROMPT,
                new NegotiationContext(SESSION_ID, 3, 7, NegotiationPerformative.PROPOSE),
                Map.of(),
                REFERENCE);

        assertTrue(filled.data().get("round") instanceof Integer);
        assertTrue(filled.data().get("maxRounds") instanceof Integer);
        assertEquals(3, filled.data().get("round"));
        assertEquals(7, filled.data().get("maxRounds"));
    }

    @Test
    void ruleFailureSkipsTemplateLoadingAndTheSemanticValidationCall() {
        complianceChecker.result = new NegotiationRuleCheckResult(
                false,
                List.of(new SlotValidationError("round", "negotiation.round_exceeded", "round exceeds maxRounds")));

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE));

        assertEquals("negotiation.rule_violation", exception.getCode());
        assertEquals(1, exception.getErrors().size());
        assertEquals("round", exception.getErrors().get(0).slotName());
        assertEquals(0, semanticValidator.invocations);
        assertEquals(0, templateContentLoader.invocations);
    }

    @Test
    void extractFailsOnRuleViolationWithoutTouchingTheValidator() {
        complianceChecker.result = new NegotiationRuleCheckResult(
                false, List.of(new SlotValidationError("id", "negotiation.invalid_context_id", "not a uuid")));

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE));

        assertEquals("negotiation.rule_violation", exception.getCode());
        assertEquals("id", exception.getErrors().get(0).slotName());
        assertEquals(0, semanticValidator.invocations);
        assertEquals(0, templateContentLoader.invocations);
    }

    @Test
    void nullContextFailsAsNonNegotiationInputWithARenderedMessage() {
        complianceChecker.result = new NegotiationRuleCheckResult(false, List.of());

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract("## 任务目标\n诊断\n", null, Map.of(), REFERENCE));

        assertEquals("negotiation.invalid_input", exception.getCode());
        assertEquals("输入的协商内容无效:缺少协商上下文(该报文不是协商报文)", exception.getMessage());
        assertEquals(List.of(), exception.getErrors());
        assertEquals(0, semanticValidator.invocations);
        assertEquals(0, complianceChecker.invocations);
        assertEquals(0, templateContentLoader.invocations);
    }

    @Test
    void semanticRejectionPassesErrorsThrough() {
        complianceChecker.result = new NegotiationRuleCheckResult(true, List.of());
        List<SlotValidationError> semanticErrors = List.of(
                new SlotValidationError(
                        "section.target_result_content", "negotiation.conclusion_content_mismatch", "Mismatch"),
                new SlotValidationError("section.context", "negotiation.conclusion_mismatch", "Abort is reserved"));
        semanticValidator.result = new SemanticValidationResult(false, "target", semanticErrors, Map.of());

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE));

        assertEquals("negotiation.semantic_rejected", exception.getCode());
        assertEquals(semanticErrors, exception.getErrors());
    }

    @Test
    void extractReturnsTheValidatorOutcome() {
        complianceChecker.result = new NegotiationRuleCheckResult(true, List.of());
        semanticValidator.result =
                new SemanticValidationResult(true, "information", List.of(), Map.of("confirmed_rate_mbps", 2));

        FilledParamData filled = extractor.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE);

        assertEquals(1, semanticValidator.invocations);
        assertEquals(2, filled.data().get("confirmed_rate_mbps"));
    }

    @Test
    void internalValidationFailureIsMappedToRetryableInfrastructureError() {
        complianceChecker.result = new NegotiationRuleCheckResult(true, List.of());
        semanticValidator.failure = new NegotiationValidationException("response is missing negotiation_type");

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE));

        assertEquals("llm.response_invalid", exception.getCode());
        assertEquals(1, exception.getErrors().size());
        assertEquals("_llm", exception.getErrors().get(0).slotName());
        assertEquals("semantic_validation", exception.getErrors().get(0).facts().get("step"));
        assertTrue(exception.getCause().getCause().getMessage().contains("negotiation_type"));
    }

    @Test
    void promptResourceMissIsMappedToTemplateNotFound() {
        complianceChecker.result = new NegotiationRuleCheckResult(true, List.of());
        semanticValidator.failure =
                new ResourceNotFoundException("prompt resource missing", "prompt_resources/prompts/x");

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE));

        assertEquals("template.not_found", exception.getCode());
        assertEquals(List.of(), exception.getErrors());
    }

    @Test
    void templateLoadFailureIsMappedToTemplateNotFoundWithoutTouchingTheValidator() {
        complianceChecker.result = new NegotiationRuleCheckResult(true, List.of());
        semanticValidator.result = new SemanticValidationResult(true, "information", List.of(), Map.of());
        templateContentLoader.failure =
                new ResourceNotFoundException("template missing", "templates/Negotiation-T/information-negotiation");

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> extractor.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE));

        assertEquals("template.not_found", exception.getCode());
        assertEquals(List.of(), exception.getErrors());
        assertEquals(1, templateContentLoader.invocations);
        assertEquals(REFERENCE, templateContentLoader.lastReference);
        assertEquals(0, semanticValidator.invocations);
    }

    @Test
    void paramMergeConflictLogsContentParamMergeConflictWarning() {
        complianceChecker.result = new NegotiationRuleCheckResult(true, List.of());
        semanticValidator.result =
                new SemanticValidationResult(true, "information", List.of(), Map.of("id", "semantic-id"));

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger pipelineLogger = (Logger) LoggerFactory.getLogger(ValidationPipeline.class);
        pipelineLogger.addAppender(appender);
        try {
            extractor.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE);
        } finally {
            pipelineLogger.detachAppender(appender);
            appender.stop();
        }

        assertTrue(
                appender.list.stream()
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().startsWith("content_param_merge_conflict")),
                "expected a WARN for a context-over-semantic parameter merge conflict");
    }

    @Test
    void templateIsLoadedBetweenTheRuleGateAndSemanticValidation() {
        List<String> order = new ArrayList<>();
        NegotiationComplianceChecker checker = context -> {
            order.add("rule");
            return new NegotiationRuleCheckResult(true, List.of());
        };
        SemanticValidator<NegotiationReference> semantic = (prompt, schema, reference, templateContent) -> {
            order.add("semantic");
            return new ValidationResult(true, List.of(), Map.of());
        };
        TemplateContentLoader<NegotiationReference> loader = reference -> {
            order.add("load");
            return TEMPLATE_CONTENT;
        };
        ParamExtractor sut = new ParamExtractor(checker, semantic, MAX_ATTEMPTS, loader);

        sut.extract(VALID_ZH_PROMPT, CONTEXT, Map.of(), REFERENCE);

        assertEquals(List.of("rule", "load", "semantic"), order);
    }

    private static final class StubComplianceChecker implements NegotiationComplianceChecker {

        private NegotiationRuleCheckResult result = new NegotiationRuleCheckResult(false, List.of());

        private int invocations;

        @Override
        public NegotiationRuleCheckResult check(NegotiationContext context) {
            invocations++;
            return result;
        }
    }

    private static final class StubSemanticValidator implements NegotiationSemanticValidator {

        private SemanticValidationResult result = new SemanticValidationResult(false, null, List.of(), Map.of());

        private RuntimeException failure;

        private int invocations;

        private String lastTemplateContent;

        @Override
        public SemanticValidationResult validateNegotiation(
                String prompt,
                Map<String, Object> callerSchema,
                NegotiationReference reference,
                String templateContent) {
            invocations++;
            lastTemplateContent = templateContent;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class StubTemplateContentLoader implements TemplateContentLoader<NegotiationReference> {

        private String content = TEMPLATE_CONTENT;

        private RuntimeException failure;

        private int invocations;

        private NegotiationReference lastReference;

        @Override
        public String load(NegotiationReference reference) {
            invocations++;
            lastReference = reference;
            if (failure != null) {
                throw failure;
            }
            return content;
        }
    }
}
