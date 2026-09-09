package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateQueryService;
import org.junit.jupiter.api.Test;

/**
 * Verifies abort message generation from typed data through the full orchestrator pipeline.
 *
 * <p>The abort message is type-independent: one common template, one content type carrying only the termination reason.
 * The rendered message must keep the fixed Abort conclusion section, carry the negotiation context bullets and never
 * leak an unreplaced placeholder. The from-data variant must stay deterministic — no LLM call.
 */
class AbortMessageGenerationTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri ABORT_URI = StandardTemplates.NEGOTIATION_ABORT;

    private final CountingClient llm = new CountingClient();

    private NegotiationGenerationOrchestrator orchestrator(String language) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .llmClient(llm)
                .build();
    }

    @Test
    void generatesAChineseAbortMessageFromDataWithoutAnyLlmCall() {
        MetadataContent result = orchestrator("zh-CN")
                .generateAbortFromData(
                        new NegotiationAbortData(
                                new NegotiationContext(SESSION_ID, 5, 5, NegotiationPerformative.ABORT),
                                new NegotiationAbortContent("达到协商轮次上限，本次协商确认结束。")),
                        ABORT_URI);

        assertEquals(ABORT_URI.uri(), result.templateUri());
        String text = result.promptText();
        assertFalse(text.contains("## 协商上下文"), "the context section must not be rendered");
        assertTrue(text.contains("## 协商结果\nAbort"), "the fixed Abort conclusion must be kept");
        assertTrue(text.contains("## 协商终止原因"), "the termination reason section must be rendered");
        assertTrue(text.contains("达到协商轮次上限，本次协商确认结束。"));
        assertEquals(
                new NegotiationContext(SESSION_ID, 5, 5, NegotiationPerformative.ABORT), result.negotiationContext());
        assertFalse(text.contains("{{"), "no unreplaced placeholder may remain");
        assertEquals(0, llm.calls, "the from-data variant must never call the LLM");
    }

    @Test
    void generatesAnEnglishAbortMessageFromData() {
        MetadataContent result = orchestrator("en-US")
                .generateAbortFromData(
                        new NegotiationAbortData(
                                new NegotiationContext(SESSION_ID, 3, 5, NegotiationPerformative.ABORT),
                                new NegotiationAbortContent(
                                        "Reached the negotiation round limit. This negotiation is confirmed and ended.")),
                        ABORT_URI);

        assertEquals(ABORT_URI.uri(), result.templateUri());
        String text = result.promptText();
        assertFalse(text.contains("## Negotiation Context"), "the context section must not be rendered");
        assertTrue(text.contains("## Negotiation Result\nAbort"));
        assertTrue(text.contains("## Negotiation Termination Reason"));
        assertTrue(text.contains("Reached the negotiation round limit."));
        assertEquals(
                new NegotiationContext(SESSION_ID, 3, 5, NegotiationPerformative.ABORT), result.negotiationContext());
        assertFalse(text.contains("{{"));
        assertEquals(0, llm.calls);
    }

    @Test
    void blankTerminationReasonFailsWithTheCodedContentInvalidFailureBeforeAnyLlmCall() {
        NegotiationGenerationException failure =
                assertThrows(NegotiationGenerationException.class, () -> orchestrator("zh-CN")
                        .generateAbortFromData(
                                new NegotiationAbortData(
                                        new NegotiationContext(SESSION_ID, 1, 5, NegotiationPerformative.ABORT),
                                        new NegotiationAbortContent(" ")),
                                ABORT_URI));
        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), failure.getCode());
        assertEquals("content.terminationReason", failure.getFacts().get("field"));
        assertTrue(
                failure.getMessage().contains("content.terminationReason"),
                "message must point at the offending field");
        assertEquals(0, llm.calls);
    }

    @Test
    void abortGenerationRejectsTypedTemplateUris() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> orchestrator("zh-CN")
                .generateAbortFromData(
                        new NegotiationAbortData(
                                new NegotiationContext(SESSION_ID, 1, 5, NegotiationPerformative.ABORT),
                                new NegotiationAbortContent("达到协商轮次上限。")),
                        StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT));
        assertTrue(failure.getMessage().contains("abort"));
    }

    @Test
    void typedEndingGenerationRejectsTheAbortTemplateUri() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> orchestrator("zh-CN")
                .generateRejectFromData(
                        new net.openan.a2at.sdk.negotiation.content.NegotiationEndingData(
                                new NegotiationContext(SESSION_ID, 1, 5, NegotiationPerformative.REJECT),
                                new net.openan.a2at.sdk.negotiation.content.TargetEndingContent(
                                        net.openan.a2at.sdk.negotiation.content.NegotiationConclusion.REJECT,
                                        null,
                                        "预算耗尽")),
                        ABORT_URI));
        assertTrue(failure.getMessage().contains("reject"));
    }

    @Test
    void singleQueryResolvesTheCommonAbortTemplateUri() {
        assertTrue(new TemplateQueryService("zh-CN", "classpath", null)
                .getPrompt(ABORT_URI)
                .isPresent());
        assertTrue(new TemplateQueryService("en-US", "classpath", null)
                .getPrompt(ABORT_URI)
                .isPresent());
    }

    @Test
    void serviceMirrorsTheAbortGenerationEntryPoint() {
        NegotiationContentService service =
                new NegotiationContentService(NegotiationGenerationOrchestratorBuilder.builder()
                        .language("zh-CN")
                        .llmClient(llm)
                        .build());

        MetadataContent result = service.generateAbortFromData(
                new NegotiationAbortData(
                        new NegotiationContext(SESSION_ID, 2, 5, NegotiationPerformative.ABORT),
                        new NegotiationAbortContent("token 消耗超限，协商终止。")),
                ABORT_URI);

        assertEquals(ABORT_URI.uri(), result.templateUri());
        assertTrue(result.promptText().contains("token 消耗超限"));
        assertEquals(0, llm.calls);
    }

    @Test
    void generatesAnAbortMessageFromFreeTextThroughOneExtractionStep() {
        ScriptedClient extractionLlm = new ScriptedClient("{\"termination_reason\":\"达到协商轮次上限，协商终止。\"}");
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(extractionLlm)
                .build();

        MetadataContent result = orchestrator.generateAbortFromText(
                "协商已达到轮次上限，无法继续推进，本次协商终止。",
                new NegotiationContext(SESSION_ID, 5, 5, NegotiationPerformative.ABORT),
                ABORT_URI);

        assertEquals(ABORT_URI.uri(), result.templateUri());
        assertTrue(result.promptText().contains("## 协商结果\nAbort"));
        assertTrue(result.promptText().contains("达到协商轮次上限，协商终止。"));
        assertFalse(result.promptText().contains("{{"));
        assertEquals(1, extractionLlm.calls, "the from-text variant runs exactly one extraction step");
    }

    @Test
    void peerValidatesAReceivedAbortMessageAndExtractsItsParameters() {
        NegotiationGenerationOrchestrator agent = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();
        ScriptedClient peerLlm =
                new ScriptedClient("{\"semantic_verdict\":true,\"negotiation_type\":null,\"errors\":[],\"params\":{}}");
        NegotiationGenerationOrchestrator peer = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(peerLlm)
                .build();

        MetadataContent abortMessage = agent.generateAbortFromData(
                new NegotiationAbortData(
                        new NegotiationContext(SESSION_ID, 5, 5, NegotiationPerformative.ABORT),
                        new NegotiationAbortContent("达到协商轮次上限，本次协商确认结束。")),
                ABORT_URI);

        FilledParamData parameters = peer.validateAbortPromptAndDataFilling(
                abortMessage.promptText(),
                new NegotiationContext(SESSION_ID, 5, 5, NegotiationPerformative.ABORT),
                Map.of("type", "object"),
                ABORT_URI);

        assertEquals(SESSION_ID, parameters.data().get("id"));
        assertEquals(5, parameters.data().get("round"));
        assertEquals(5, parameters.data().get("maxRounds"));
        assertEquals(1, peerLlm.calls, "the validation pipeline runs exactly one semantic step");
    }

    @Test
    void abortValidationFailsOnBeyondBudgetRoundsBeforeAnyLlmCall() {
        NegotiationGenerationOrchestrator agent = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();
        ScriptedClient peerLlm =
                new ScriptedClient("{\"semantic_verdict\":true,\"negotiation_type\":null,\"errors\":[],\"params\":{}}");
        NegotiationGenerationOrchestrator peer = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(peerLlm)
                .build();

        MetadataContent beyondBudget = agent.generateAbortFromData(
                new NegotiationAbortData(
                        new NegotiationContext(SESSION_ID, 6, 5, NegotiationPerformative.ABORT),
                        new NegotiationAbortContent("轮次预算耗尽。")),
                ABORT_URI);

        NegotiationParamExtractionException failure = assertThrows(
                NegotiationParamExtractionException.class,
                () -> peer.validateAbortPromptAndDataFilling(
                        beyondBudget.promptText(),
                        new NegotiationContext(SESSION_ID, 6, 5, NegotiationPerformative.ABORT),
                        Map.of("type", "object"),
                        ABORT_URI));
        assertEquals(ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode(), failure.getCode());
        assertTrue(
                failure.getErrors().stream().anyMatch(error -> "round".equals(error.slotName())),
                "the beyond-budget round must be reported on the round slot");
        assertEquals(0, peerLlm.calls, "the rule gate must fail before any semantic LLM call");
    }

    @Test
    void abortValidationRejectsTypedTemplateUris() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.validateAbortPromptAndDataFilling(
                        "任意文本", null, Map.of("type", "object"), StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE));
        assertTrue(failure.getMessage().contains("abort"));
        assertEquals(0, llm.calls);
    }

    private static final class CountingClient implements LLMClient {

        private int calls;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls++;
            throw new AssertionError("The from-data abort variant must never call the LLM");
        }
    }

    private static final class ScriptedClient implements LLMClient {

        private final String payload;

        private final List<List<Map<String, String>>> recordedMessages = new ArrayList<>();

        private int calls;

        private ScriptedClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls++;
            recordedMessages.add(List.copyOf(messages));
            return new LLMResponse(payload, "scripted-abort-model", Map.of(), Map.of());
        }
    }
}
