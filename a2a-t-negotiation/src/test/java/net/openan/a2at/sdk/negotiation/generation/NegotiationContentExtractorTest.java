package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.junit.jupiter.api.Test;

class NegotiationContentExtractorTest {

    @Test
    void extractsInformationProposeContent() {
        RecordingClient llm = new RecordingClient(
                "{\"items\":[{\"name\":\"故障发生时间\",\"value\":\"精确到分钟的时间点\"},{\"name\":\"受影响小区标识\",\"value\":null}],"
                        + "\"relationship\":\"故障发生时间与受影响小区标识需逐小区对应\"}");

        NegotiationContent content = new DefaultNegotiationContentExtractor(llm)
                .extract(
                        "请提供故障发生时间与受影响小区标识。",
                        reference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN"));

        InformationProposeContent proposeContent = assertInstanceOf(InformationProposeContent.class, content);
        assertEquals(
                List.of(new NegotiationItem("故障发生时间", "精确到分钟的时间点"), new NegotiationItem("受影响小区标识", null)),
                proposeContent.items());
        assertEquals("故障发生时间与受影响小区标识需逐小区对应", proposeContent.relationship());

        assertEquals(2, llm.lastMessages.size());
        assertEquals("system", llm.lastMessages.get(0).get("role"));
        assertEquals("user", llm.lastMessages.get(1).get("role"));
        assertTrue(llm.lastMessages.get(1).get("content").contains("协商阶段：propose"));
        assertTrue(llm.lastMessages.get(1).get("content").contains("请提供故障发生时间与受影响小区标识。"));
        assertEquals(
                new NegotiationJsonSchemaBuilder()
                        .buildExtractionSchema(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE),
                llm.lastSchema);
    }

    @Test
    void extractsTargetProposeContent() {
        NegotiationContent content = new DefaultNegotiationContentExtractor(
                        new RecordingClient("{\"target_negotiation_description\":\"请求将节能目标由30%调整为20%。\","
                                + "\"intent_understanding\":[{\"name\":\"发起方理解\",\"value\":\"对方希望降低节能力度\"}],"
                                + "\"alignment_and_clarification\":null,"
                                + "\"request_for_clarification\":[{\"name\":\"速率保障下限\",\"value\":null}]}"))
                .extract("请求调整节能目标。", reference(NegotiationType.TARGET, NegotiationPerformative.PROPOSE, "en-US"));

        TargetProposeContent proposeContent = assertInstanceOf(TargetProposeContent.class, content);
        assertEquals("请求将节能目标由30%调整为20%。", proposeContent.targetNegotiationDescription());
        assertEquals(List.of(new NegotiationItem("发起方理解", "对方希望降低节能力度")), proposeContent.intentUnderstanding());
        assertNull(proposeContent.alignmentAndClarification());
        assertEquals(List.of(new NegotiationItem("速率保障下限", null)), proposeContent.requestForClarification());
    }

    @Test
    void extractsFeasibilityProposeContentForBothActions() {
        NegotiationContent request = new DefaultNegotiationContentExtractor(new RecordingClient(
                        "{\"feasibility_negotiation_description\":\"请评估该节能目标能否达成。\",\"action\":\"REQUEST_FEASIBILITY_EVALUATION\","
                                + "\"contents_to_evaluate\":[{\"name\":\"评估对象\",\"value\":\"停电8小时期间的速率保障\"}],"
                                + "\"infeasibility_details_and_proposal\":null}"))
                .extract("请评估。", reference(NegotiationType.FEASIBILITY, NegotiationPerformative.PROPOSE, "zh-CN"));

        FeasibilityProposeContent requestContent = assertInstanceOf(FeasibilityProposeContent.class, request);
        assertEquals(NegotiationAction.REQUEST_FEASIBILITY_EVALUATION, requestContent.action());
        assertEquals(List.of(new NegotiationItem("评估对象", "停电8小时期间的速率保障")), requestContent.contentsToEvaluate());

        NegotiationContent alternative = new DefaultNegotiationContentExtractor(
                        new RecordingClient(
                                "{\"feasibility_negotiation_description\":\"目标不可行，提出下调方案。\",\"action\":\"PROPOSE_ALTERNATIVE_ON_FAILURE\","
                                        + "\"contents_to_evaluate\":null,"
                                        + "\"infeasibility_details_and_proposal\":[{\"name\":\"替代提案\",\"value\":\"下调至2Mbps\"}]}"))
                .extract("不可行。", reference(NegotiationType.FEASIBILITY, NegotiationPerformative.PROPOSE, "zh-CN"));

        FeasibilityProposeContent alternativeContent = assertInstanceOf(FeasibilityProposeContent.class, alternative);
        assertEquals(NegotiationAction.PROPOSE_ALTERNATIVE_ON_FAILURE, alternativeContent.action());
        assertEquals(
                List.of(new NegotiationItem("替代提案", "下调至2Mbps")), alternativeContent.infeasibilityDetailsAndProposal());
    }

    @Test
    void extractsTargetProposeConfirmRequestContent() {
        NegotiationContent content = new DefaultNegotiationContentExtractor(
                        new RecordingClient("{\"target_negotiation_description\":\"任务目标澄清完成，请答复<目标澄清后的确认请求>。\","
                                + "\"intent_understanding\":null,"
                                + "\"alignment_and_clarification\":null,"
                                + "\"request_for_clarification\":null,"
                                + "\"target_confirm_request\":\"目标已经澄清，是否同意按照此目标继续执行？\"}"))
                .extract("目标已经澄清，请确认。", reference(NegotiationType.TARGET, NegotiationPerformative.PROPOSE, "zh-CN"));

        TargetProposeContent proposeContent = assertInstanceOf(TargetProposeContent.class, content);
        assertEquals("任务目标澄清完成，请答复<目标澄清后的确认请求>。", proposeContent.targetNegotiationDescription());
        assertEquals("目标已经澄清，是否同意按照此目标继续执行？", proposeContent.targetConfirmRequest());
        assertNull(proposeContent.intentUnderstanding());
        assertNull(proposeContent.alignmentAndClarification());
        assertNull(proposeContent.requestForClarification());
    }

    @Test
    void extractsFeasibilityProposeConfirmRequestContent() {
        NegotiationContent content = new DefaultNegotiationContentExtractor(new RecordingClient(
                        "{\"feasibility_negotiation_description\":\"针对调整后的速率保障目标，可行性评估已完成，结论为可行，请答复<评估可行时的确认请求>。\","
                                + "\"action\":\"REQUEST_FEASIBILITY_EVALUATION\","
                                + "\"contents_to_evaluate\":null,"
                                + "\"infeasibility_details_and_proposal\":null,"
                                + "\"feasibility_confirm_request\":\"评估目标可行，是否同意按照此目标继续执行？\"}"))
                .extract(
                        "评估目标可行，请确认。",
                        reference(NegotiationType.FEASIBILITY, NegotiationPerformative.PROPOSE, "zh-CN"));

        FeasibilityProposeContent proposeContent = assertInstanceOf(FeasibilityProposeContent.class, content);
        assertEquals(NegotiationAction.REQUEST_FEASIBILITY_EVALUATION, proposeContent.action());
        assertEquals("评估目标可行，是否同意按照此目标继续执行？", proposeContent.feasibilityConfirmRequest());
        assertNull(proposeContent.contentsToEvaluate());
        assertNull(proposeContent.infeasibilityDetailsAndProposal());
    }

    @Test
    void passesConfirmRequestWordingThroughLeniently() {
        TargetProposeContent target = assertInstanceOf(
                TargetProposeContent.class,
                new DefaultNegotiationContentExtractor(new RecordingClient(
                                "{\"target_negotiation_description\":\"描述\",\"intent_understanding\":null,"
                                        + "\"alignment_and_clarification\":null,\"request_for_clarification\":null,"
                                        + "\"target_confirm_request\":\"请确认按此目标推进\"}"))
                        .extract("文本", reference(NegotiationType.TARGET, NegotiationPerformative.PROPOSE, "zh-CN")));
        assertEquals("请确认按此目标推进", target.targetConfirmRequest());

        FeasibilityProposeContent feasibility = assertInstanceOf(
                FeasibilityProposeContent.class,
                new DefaultNegotiationContentExtractor(new RecordingClient(
                                "{\"feasibility_negotiation_description\":\"描述\",\"action\":\"REQUEST_FEASIBILITY_EVALUATION\","
                                        + "\"contents_to_evaluate\":null,\"infeasibility_details_and_proposal\":null,"
                                        + "\"feasibility_confirm_request\":\"方案可行，望确认\"}"))
                        .extract(
                                "文本",
                                reference(NegotiationType.FEASIBILITY, NegotiationPerformative.PROPOSE, "zh-CN")));
        assertEquals("方案可行，望确认", feasibility.feasibilityConfirmRequest());
    }

    @Test
    void extractsEndingContentForEveryTypeAndBothPhases() {
        NegotiationContent infoAccept = new DefaultNegotiationContentExtractor(new RecordingClient(
                        "{\"conclusion\":\"Accept\",\"items\":[{\"name\":\"故障发生时间\",\"value\":\"2026-08-19 10:30\"}]}"))
                .extract("同意提供。", reference(NegotiationType.INFORMATION, NegotiationPerformative.ACCEPT, "zh-CN"));

        InformationEndingContent infoEnding = assertInstanceOf(InformationEndingContent.class, infoAccept);
        assertEquals(NegotiationConclusion.ACCEPT, infoEnding.conclusion());
        assertEquals(List.of(new NegotiationItem("故障发生时间", "2026-08-19 10:30")), infoEnding.items());

        NegotiationContent targetReject = new DefaultNegotiationContentExtractor(new RecordingClient(
                        "{\"conclusion\":\"Reject\",\"confirmed_intent\":null,\"failure_reason\":\"双方未达成一致。\"}"))
                .extract("无法同意。", reference(NegotiationType.TARGET, NegotiationPerformative.REJECT, "zh-CN"));

        TargetEndingContent targetEnding = assertInstanceOf(TargetEndingContent.class, targetReject);
        assertEquals(NegotiationConclusion.REJECT, targetEnding.conclusion());
        assertEquals("双方未达成一致。", targetEnding.failureReason());

        NegotiationContent feasibilityAccept = new DefaultNegotiationContentExtractor(
                        new RecordingClient("{\"conclusion\":\"Accept\",\"feasibility_summary\":\"同意下调至2Mbps。\"}"))
                .extract("同意。", reference(NegotiationType.FEASIBILITY, NegotiationPerformative.ACCEPT, "zh-CN"));

        assertEquals(
                "同意下调至2Mbps。",
                assertInstanceOf(
                                net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent.class,
                                feasibilityAccept)
                        .feasibilitySummary());

        assertEquals("accept", phaseTokenInUserPrompt(NegotiationPerformative.ACCEPT));
        assertEquals("reject", phaseTokenInUserPrompt(NegotiationPerformative.REJECT));
        assertEquals("propose", phaseTokenInUserPrompt(NegotiationPerformative.PROPOSE));
    }

    @Test
    void mapsTransportFailuresToTheInvocationFailedCode() {
        LLMClient failing = (messages, jsonSchema, temperature, maxTokens) -> {
            throw new IllegalStateException("connection reset");
        };

        NegotiationGenerationException exception =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(failing)
                        .extract(
                                "文本",
                                reference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.LLM_INVOCATION_FAILED.getCode(), exception.getCode());
    }

    @Test
    void mapsMissingLlmClientToTheNotConfiguredCode() {
        NegotiationGenerationException exception =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(null)
                        .extract(
                                "文本",
                                reference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.LLM_NOT_CONFIGURED.getCode(), exception.getCode());
    }

    @Test
    void mapsResponseContractViolationsAndShapeFailures() {
        NegotiationGenerationException unparseable =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(
                                new RecordingClient("这不是 JSON"))
                        .extract(
                                "文本",
                                reference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), unparseable.getCode());

        NegotiationGenerationException wrongShape =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(
                                new RecordingClient("{\"items\":\"不是数组\"}"))
                        .extract(
                                "文本",
                                reference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_EXTRACT_FAILED.getCode(), wrongShape.getCode());
        assertEquals("items", wrongShape.getFacts().get("field"));

        NegotiationGenerationException emptyResponse =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(
                                new RecordingClient("  "))
                        .extract(
                                "文本",
                                reference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), emptyResponse.getCode());
    }

    @Test
    void mapsMissingRequiredFieldsToTheSlotMissingCode() {
        NegotiationGenerationException missingItems =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(
                                new RecordingClient("{\"relationship\":null}"))
                        .extract(
                                "文本",
                                reference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), missingItems.getCode());
        assertTrue(missingItems.getMessage().contains("items"));

        NegotiationGenerationException missingDescription = assertThrows(
                NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(new RecordingClient(
                                "{\"intent_understanding\":null,\"alignment_and_clarification\":null,"
                                        + "\"request_for_clarification\":null}"))
                        .extract("文本", reference(NegotiationType.TARGET, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), missingDescription.getCode());
        assertTrue(missingDescription.getMessage().contains("target_negotiation_description"));

        NegotiationGenerationException missingConclusion =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(
                                new RecordingClient("{\"items\":[]}"))
                        .extract(
                                "文本", reference(NegotiationType.INFORMATION, NegotiationPerformative.ACCEPT, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), missingConclusion.getCode());
        assertTrue(missingConclusion.getMessage().contains("conclusion"));

        NegotiationGenerationException missingConfirmedIntent = assertThrows(
                NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(new RecordingClient(
                                "{\"conclusion\":\"Accept\",\"confirmed_intent\":null,\"failure_reason\":null}"))
                        .extract("文本", reference(NegotiationType.TARGET, NegotiationPerformative.ACCEPT, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), missingConfirmedIntent.getCode());
        assertTrue(missingConfirmedIntent.getMessage().contains("confirmed_intent"));
    }

    @Test
    void mapsConclusionPhaseMismatchesToTheConclusionMismatchCode() {
        NegotiationGenerationException rejectInAccept =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(
                                new RecordingClient("{\"conclusion\":\"Reject\",\"items\":[]}"))
                        .extract(
                                "文本", reference(NegotiationType.INFORMATION, NegotiationPerformative.ACCEPT, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_CONCLUSION_MISMATCH.getCode(), rejectInAccept.getCode());
        assertEquals("Accept", rejectInAccept.getFacts().get("expected"));
        assertEquals("Reject", rejectInAccept.getFacts().get("actual"));

        NegotiationGenerationException acceptInReject =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(
                                new RecordingClient("{\"conclusion\":\"Accept\",\"feasibility_summary\":\"同意。\"}"))
                        .extract(
                                "文本", reference(NegotiationType.FEASIBILITY, NegotiationPerformative.REJECT, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_CONCLUSION_MISMATCH.getCode(), acceptInReject.getCode());
    }

    @Test
    void rejectsEmptyInformationEndingItemsBeforeTemplateRendering() {
        NegotiationGenerationException exception =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(
                                new RecordingClient("{\"conclusion\":\"Reject\",\"items\":[]}"))
                        .extract(
                                "拒绝，资源查询服务正在检修。",
                                reference(NegotiationType.INFORMATION, NegotiationPerformative.REJECT, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("result content"));
    }

    @Test
    void rejectsEmptyInformationProposeItemsBeforeTemplateRendering() {
        NegotiationGenerationException exception =
                assertThrows(NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(
                                new RecordingClient("{\"items\":[]}"))
                        .extract(
                                "请补充缺失信息。",
                                reference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("requested items"));
    }

    @Test
    void mapsFeasibilityActionProblemsToTheInvalidInputCode() {
        NegotiationGenerationException missingAction = assertThrows(
                NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(new RecordingClient(
                                "{\"feasibility_negotiation_description\":\"描述\",\"contents_to_evaluate\":[]}"))
                        .extract(
                                "文本",
                                reference(NegotiationType.FEASIBILITY, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), missingAction.getCode());

        NegotiationGenerationException emptyDrivenContent = assertThrows(
                NegotiationGenerationException.class, () -> new DefaultNegotiationContentExtractor(new RecordingClient(
                                "{\"feasibility_negotiation_description\":\"描述\",\"action\":\"REQUEST_FEASIBILITY_EVALUATION\","
                                        + "\"contents_to_evaluate\":[],\"infeasibility_details_and_proposal\":null}"))
                        .extract(
                                "文本",
                                reference(NegotiationType.FEASIBILITY, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), emptyDrivenContent.getCode());
    }

    @Test
    void rejectsBlankInputTextAndNullReference() {
        DefaultNegotiationContentExtractor extractor =
                new DefaultNegotiationContentExtractor(new RecordingClient("{}"));

        NegotiationGenerationException blank = assertThrows(
                NegotiationGenerationException.class,
                () -> extractor.extract(
                        "  ", reference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN")));

        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), blank.getCode());

        assertEquals(
                "Negotiation reference must not be null.",
                assertThrows(NullPointerException.class, () -> extractor.extract("文本", null))
                        .getMessage());
    }

    private static NegotiationReference reference(
            NegotiationType type, NegotiationPerformative performative, String language) {
        return new NegotiationReference(type, performative, language);
    }

    private static String phaseTokenInUserPrompt(NegotiationPerformative performative) {
        RecordingClient client = new RecordingClient("{\"conclusion\":\"Accept\",\"items\":[]}");
        try {
            new DefaultNegotiationContentExtractor(client)
                    .extract("文本", reference(NegotiationType.INFORMATION, performative, "zh-CN"));
        } catch (NegotiationGenerationException expectedForReject) {
            // The scripted conclusion only satisfies the accept phase; the messages are recorded either way.
        }
        String userPrompt = client.lastMessages.get(1).get("content").replace("\r\n", "\n");
        int start = userPrompt.indexOf("协商阶段：") + "协商阶段：".length();
        return userPrompt.substring(start, userPrompt.indexOf('\n', start));
    }

    private static final class RecordingClient implements LLMClient {
        private final String payload;

        private List<Map<String, String>> lastMessages;

        private Map<String, Object> lastSchema;

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
            this.lastSchema = jsonSchema;
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }
}
