package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.resources.DefaultNegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.junit.jupiter.api.Test;

class NegotiationGeneratorsTest {

    private static final String CONTEXT_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String ZH_TARGET_PROPOSE_TEMPLATE =
            """
            ## 协商上下文
            {{协商上下文}}（必填）
            要求：
            上下文要求。

            ## 目标协商
            {{目标协商概述}}（必填）
            要求：
            概述要求。

            ## 意图理解陈述
            {{意图理解陈述}}（选填）
            要求：
            意图要求。

            ## 理解对齐与疑问澄清
            {{理解对齐与疑问澄清}}（选填）
            要求：
            对齐要求。

            ## 待澄清内容
            {{待澄清内容}}（选填）
            要求：
            澄清要求。
            """;

    private static final String ZH_FEASIBILITY_PROPOSE_TEMPLATE =
            """
            ## 协商上下文
            {{协商上下文}}（必填）
            要求：
            上下文要求。

            ## 可行性协商
            {{可行性协商概述}}（必填）
            要求：
            概述要求。

            ## 待评估内容说明
            {{待评估内容说明}}（选填）
            要求：
            评估要求。

            ## 评估不可行时的详情和提案
            {{评估不可行时的详情和提案}}（选填）
            要求：
            提案要求。
            """;

    private static final String ZH_TARGET_PROPOSE_CONFIRM_TEMPLATE =
            """
            ## 协商上下文
            {{协商上下文}}（必填）
            要求：
            上下文要求。

            ## 目标协商
            {{目标协商概述}}（必填）
            要求：
            概述要求。

            ## 意图理解陈述
            {{意图理解陈述}}（选填）
            要求：
            意图要求。

            ## 理解对齐与疑问澄清
            {{理解对齐与疑问澄清}}（选填）
            要求：
            对齐要求。

            ## 待澄清内容
            {{待澄清内容}}（选填）
            要求：
            澄清要求。

            ## 目标澄清后的确认请求
            {{目标澄清后的确认请求}}（选填）
            要求：
            确认要求。
            """;

    private static final String ZH_FEASIBILITY_PROPOSE_CONFIRM_TEMPLATE =
            """
            ## 协商上下文
            {{协商上下文}}（必填）
            要求：
            上下文要求。

            ## 可行性协商
            {{可行性协商概述}}（必填）
            要求：
            概述要求。

            ## 待评估内容说明
            {{待评估内容说明}}（选填）
            要求：
            评估要求。

            ## 评估不可行时的详情和提案
            {{评估不可行时的详情和提案}}（选填）
            要求：
            提案要求。

            ## 评估可行时的确认请求
            {{评估可行时的确认请求}}（选填）
            要求：
            确认要求。
            """;

    private static final String ZH_INFO_PROPOSE_TEMPLATE =
            """
            ## 协商上下文
            {{协商上下文}}（必填）
            要求：
            上下文要求。

            ## 信息协商
            请根据<所需信息项>补充相关内容。

            ## 所需信息项
            {{所需信息项}}（必填）
            要求：
            信息项要求。
            """;

    private static final String ZH_INFO_ENDING_TEMPLATE =
            """
            ## 协商上下文
            {{协商上下文}}（必填）
            要求：
            上下文要求。

            ## 信息协商结果
            {{信息协商结果}}（必填）
            要求：
            结论要求。

            ## 信息协商结果内容
            {{信息协商结果内容}}（必填）
            要求：
            内容要求。
            """;

    private static final String ZH_TARGET_ENDING_TEMPLATE =
            """
            ## 协商上下文
            {{协商上下文}}（必填）
            要求：
            上下文要求。

            ## 目标协商结果
            {{目标协商结果}}（必填）
            要求：
            结论要求。

            ## 目标协商结果内容
            {{目标协商结果内容}}（必填）
            要求：
            内容要求。
            """;

    private static final String ZH_FEASIBILITY_ENDING_TEMPLATE =
            """
            ## 协商上下文
            {{协商上下文}}（必填）
            要求：
            上下文要求。

            ## 可行性协商结果
            {{可行性协商结果}}（必填）
            要求：
            结论要求。

            ## 可行性评估结果确认
            {{评估结果确认}}（必填）
            要求：
            确认要求。
            """;

    private final Vocabulary zhVocabulary = Vocabulary.forLanguage("zh-CN");

    private final Vocabulary enVocabulary = Vocabulary.forLanguage("en-US");

    @Test
    void feasibilityRequestActionRendersExactlyTheEvaluationSection() {
        FeasibilityProposeContent content = new FeasibilityProposeContent(
                "请协助评估该节能目标能否达成。",
                NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                List.of(new NegotiationItem("评估对象", "停电8小时期间核心用户的速率保障")),
                List.of(new NegotiationItem("不应出现", "值")),
                null);

        String rendered = new FeasibilityProposeGenerator()
                .generate(
                        context(1),
                        content,
                        template(StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE, ZH_FEASIBILITY_PROPOSE_TEMPLATE),
                        zhVocabulary);

        assertTrue(rendered.contains("## 可行性协商\n请协助评估该节能目标能否达成。"));
        assertTrue(rendered.contains("## 待评估内容说明\n1. 评估对象：停电8小时期间核心用户的速率保障"));
        assertFalse(rendered.contains("## 评估不可行时的详情和提案"));
        assertFalse(rendered.contains("要求："));
    }

    @Test
    void feasibilityAlternativeActionRendersExactlyTheInfeasibilitySection() {
        FeasibilityProposeContent content = new FeasibilityProposeContent(
                "当前速率目标不可行，提出下调方案。",
                NegotiationAction.PROPOSE_ALTERNATIVE_ON_FAILURE,
                List.of(new NegotiationItem("不应出现", "值")),
                List.of(
                        new NegotiationItem("不可行原因", "蓄电池仅能支撑8小时2Mbps的保障能力"),
                        new NegotiationItem("替代提案", "停电期间将速率保障目标下调至2Mbps")),
                null);

        String rendered = new FeasibilityProposeGenerator()
                .generate(
                        context(2),
                        content,
                        template(StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE, ZH_FEASIBILITY_PROPOSE_TEMPLATE),
                        zhVocabulary);

        assertTrue(rendered.contains("## 可行性协商\n当前速率目标不可行，提出下调方案。"));
        assertTrue(rendered.contains("## 评估不可行时的详情和提案\n1. 不可行原因：蓄电池仅能支撑8小时2Mbps的保障能力"));
        assertTrue(rendered.contains("2. 替代提案：停电期间将速率保障目标下调至2Mbps"));
        assertFalse(rendered.contains("## 待评估内容说明"));
    }

    @Test
    void feasibilityNullActionOrEmptyDrivenContentIsRejected() {
        FeasibilityProposeContent nullAction =
                new FeasibilityProposeContent("描述", null, List.of(new NegotiationItem("名称", "值")), null, null);

        NullPointerException nullActionError =
                assertThrows(NullPointerException.class, () -> new FeasibilityProposeGenerator()
                        .generate(
                                context(1),
                                nullAction,
                                template(
                                        StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE,
                                        ZH_FEASIBILITY_PROPOSE_TEMPLATE),
                                zhVocabulary));
        assertTrue(nullActionError.getMessage().contains("action must not be null"));

        FeasibilityProposeContent emptyEvaluation =
                new FeasibilityProposeContent("描述", NegotiationAction.REQUEST_FEASIBILITY_EVALUATION, null, null, null);

        NegotiationGenerationException emptyEvaluationError =
                assertThrows(NegotiationGenerationException.class, () -> new FeasibilityProposeGenerator()
                        .generate(
                                context(1),
                                emptyEvaluation,
                                template(
                                        StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE,
                                        ZH_FEASIBILITY_PROPOSE_TEMPLATE),
                                zhVocabulary));
        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), emptyEvaluationError.getCode());
        assertEquals(
                "content.contentsToEvaluate", emptyEvaluationError.getFacts().get("field"));

        FeasibilityProposeContent emptyAlternative = new FeasibilityProposeContent(
                "描述", NegotiationAction.PROPOSE_ALTERNATIVE_ON_FAILURE, null, List.of(), null);

        NegotiationGenerationException emptyAlternativeError =
                assertThrows(NegotiationGenerationException.class, () -> new FeasibilityProposeGenerator()
                        .generate(
                                context(1),
                                emptyAlternative,
                                template(
                                        StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE,
                                        ZH_FEASIBILITY_PROPOSE_TEMPLATE),
                                zhVocabulary));
        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), emptyAlternativeError.getCode());
        assertEquals(
                "content.infeasibilityDetailsAndProposal",
                emptyAlternativeError.getFacts().get("field"));

        FeasibilityProposeContent blankDescription = new FeasibilityProposeContent(
                " ",
                NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                List.of(new NegotiationItem("名称", "值")),
                null,
                null);

        NegotiationGenerationException blankDescriptionError =
                assertThrows(NegotiationGenerationException.class, () -> new FeasibilityProposeGenerator()
                        .generate(
                                context(1),
                                blankDescription,
                                template(
                                        StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE,
                                        ZH_FEASIBILITY_PROPOSE_TEMPLATE),
                                zhVocabulary));
        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), blankDescriptionError.getCode());
        assertEquals(
                "content.feasibilityNegotiationDescription",
                blankDescriptionError.getFacts().get("field"));
    }

    @Test
    void targetConfirmRequestRendersOnlyTheSummaryAndConfirmRequestSections() {
        TargetProposeContent content =
                new TargetProposeContent("任务目标澄清完成，请答复<目标澄清后的确认请求>。", null, null, null, "目标已经澄清，是否同意按照此目标继续执行？");

        String rendered = new TargetProposeGenerator()
                .generate(
                        context(2),
                        content,
                        template(StandardTemplates.TARGET_NEGOTIATION_PROPOSE, ZH_TARGET_PROPOSE_CONFIRM_TEMPLATE),
                        zhVocabulary);

        assertTrue(rendered.contains("## 目标协商\n任务目标澄清完成，请答复<目标澄清后的确认请求>。"));
        assertTrue(rendered.contains("## 目标澄清后的确认请求\n目标已经澄清，是否同意按照此目标继续执行？"));
        assertFalse(rendered.contains("## 意图理解陈述"));
        assertFalse(rendered.contains("## 理解对齐与疑问澄清"));
        assertFalse(rendered.contains("## 待澄清内容"));
        assertFalse(rendered.contains("要求："));

        String renderedEn = new TargetProposeGenerator()
                .generate(
                        context(2),
                        new TargetProposeContent(
                                "The clarification of the task target has been completed. Please reply to <Target"
                                        + " Clarification Confirmation Request>.",
                                null,
                                null,
                                null,
                                "The target has been clarified. Do you agree to proceed with this target?"),
                        template(
                                StandardTemplates.TARGET_NEGOTIATION_PROPOSE,
                                """
                                ## Target Negotiation
                                {{target_negotiation_summary}} (required)
                                Requirement: summary.

                                ## Intent Understanding Statement
                                {{intent_understanding_statement}} (optional)
                                Requirement: intent.

                                ## Target Clarification Confirmation Request
                                {{target_confirm_request}} (optional)
                                Requirement: confirm.
                                """),
                        enVocabulary);

        assertTrue(renderedEn.contains("## Target Clarification Confirmation Request\nThe target has been clarified."
                + " Do you agree to proceed with this target?"));
        assertFalse(renderedEn.contains("## Intent Understanding Statement"));
    }

    @Test
    void feasibilityConfirmRequestRendersOnlyTheSummaryAndConfirmRequestSections() {
        FeasibilityProposeContent content = new FeasibilityProposeContent(
                "针对调整后的速率保障目标，可行性评估已完成，结论为可行，请答复<评估可行时的确认请求>。",
                NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                null,
                null,
                "评估目标可行，是否同意按照此目标继续执行？");

        String rendered = new FeasibilityProposeGenerator()
                .generate(
                        context(2),
                        content,
                        template(
                                StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE,
                                ZH_FEASIBILITY_PROPOSE_CONFIRM_TEMPLATE),
                        zhVocabulary);

        assertTrue(rendered.contains("## 可行性协商\n针对调整后的速率保障目标，可行性评估已完成，结论为可行，请答复<评估可行时的确认请求>。"));
        assertTrue(rendered.contains("## 评估可行时的确认请求\n评估目标可行，是否同意按照此目标继续执行？"));
        assertFalse(rendered.contains("## 待评估内容说明"));
        assertFalse(rendered.contains("## 评估不可行时的详情和提案"));
        assertFalse(rendered.contains("要求："));

        String renderedEn = new FeasibilityProposeGenerator()
                .generate(
                        context(2),
                        new FeasibilityProposeContent(
                                "Regarding the adjusted rate guarantee target, the feasibility assessment has been"
                                        + " completed and the conclusion is feasible. Please reply to <Feasible"
                                        + " Evaluation Confirmation Request>.",
                                NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                null,
                                null,
                                "The target is assessed as feasible. Do you agree to proceed with this target?"),
                        template(
                                StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE,
                                """
                                ## Feasibility Negotiation
                                {{feasibility_negotiation_summary}} (required)
                                Requirement: summary.

                                ## Under Evaluation Description
                                {{under_evaluation_description}} (optional)
                                Requirement: evaluate.

                                ## Feasible Evaluation Confirmation Request
                                {{feasibility_confirm_request}} (optional)
                                Requirement: confirm.
                                """),
                        enVocabulary);

        assertTrue(renderedEn.contains("## Feasible Evaluation Confirmation Request\nThe target is assessed as"
                + " feasible. Do you agree to proceed with this target?"));
        assertFalse(renderedEn.contains("## Under Evaluation Description"));
    }

    @Test
    void targetFirstRoundRendersIntentSectionAndDropsAlignmentSection() {
        TargetProposeContent content = new TargetProposeContent(
                "对无线节能优化任务的意图理解参见意图理解陈述，请澄清和确认。",
                List.of(new NegotiationItem("发起方理解", "对方希望在体验无损的前提下降低节能力度")),
                List.of(new NegotiationItem("对齐项", "不应在首轮渲染")),
                List.of(new NegotiationItem("节能时间范围", "需要澄清")),
                null);

        String rendered = new TargetProposeGenerator()
                .generate(
                        context(1),
                        content,
                        template(StandardTemplates.TARGET_NEGOTIATION_PROPOSE, ZH_TARGET_PROPOSE_TEMPLATE),
                        zhVocabulary);

        assertTrue(rendered.contains("## 意图理解陈述\n1. 发起方理解：对方希望在体验无损的前提下降低节能力度"));
        assertFalse(rendered.contains("## 理解对齐与疑问澄清"));
        assertTrue(rendered.contains("## 待澄清内容\n1. 节能时间范围：需要澄清"));
        assertTrue(rendered.contains("## 目标协商\n对无线节能优化任务的意图理解参见意图理解陈述，请澄清和确认。"));
    }

    @Test
    void targetLaterRoundRendersAlignmentSectionAndDropsIntentSection() {
        TargetProposeContent content = new TargetProposeContent(
                "已提供疑问澄清，请确认。",
                List.of(new NegotiationItem("意图项", "不应在非首轮渲染")),
                List.of(new NegotiationItem("确认结果", "节能时间范围确认为08:00~18:00")),
                null,
                null);

        String rendered = new TargetProposeGenerator()
                .generate(
                        context(3),
                        content,
                        template(StandardTemplates.TARGET_NEGOTIATION_PROPOSE, ZH_TARGET_PROPOSE_TEMPLATE),
                        zhVocabulary);

        assertTrue(rendered.contains("## 理解对齐与疑问澄清\n1. 确认结果：节能时间范围确认为08:00~18:00"));
        assertFalse(rendered.contains("## 意图理解陈述"));
        assertFalse(rendered.contains("## 待澄清内容"));
    }

    @Test
    void targetBlankDescriptionIsRejected() {
        TargetProposeContent content = new TargetProposeContent(" ", null, null, null, null);

        NegotiationGenerationException exception =
                assertThrows(NegotiationGenerationException.class, () -> new TargetProposeGenerator()
                        .generate(
                                context(1),
                                content,
                                template(StandardTemplates.TARGET_NEGOTIATION_PROPOSE, ZH_TARGET_PROPOSE_TEMPLATE),
                                zhVocabulary));

        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), exception.getCode());
        assertEquals(
                "content.targetNegotiationDescription", exception.getFacts().get("field"));
    }

    @Test
    void informationProposeAppendsRelationshipLineAndOmitsItWhenAbsent() {
        InformationProposeContent withRelationship = new InformationProposeContent(
                List.of(new NegotiationItem("故障发生时间", "精确到分钟的时间点"), new NegotiationItem("受影响小区标识", "CGI 或小区名称")),
                "故障发生时间与受影响小区标识需逐小区对应");

        String renderedZh = new InformationProposeGenerator()
                .generate(
                        context(1),
                        withRelationship,
                        template(StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE, ZH_INFO_PROPOSE_TEMPLATE),
                        zhVocabulary);

        assertTrue(renderedZh.contains("## 所需信息项\n1. 故障发生时间：精确到分钟的时间点\n2. 受影响小区标识：CGI 或小区名称"));
        assertTrue(renderedZh.endsWith("缺失项之间的关系：故障发生时间与受影响小区标识需逐小区对应"));

        InformationProposeContent withoutRelationship =
                new InformationProposeContent(List.of(new NegotiationItem("故障发生时间", "精确到分钟的时间点")), null);

        String renderedWithout = new InformationProposeGenerator()
                .generate(
                        context(1),
                        withoutRelationship,
                        template(StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE, ZH_INFO_PROPOSE_TEMPLATE),
                        zhVocabulary);

        assertFalse(renderedWithout.contains("缺失项之间的关系"));
    }

    @Test
    void informationProposeRendersEnglishRelationshipLabelWithTrailingSpace() {
        InformationProposeContent content = new InformationProposeContent(
                List.of(new NegotiationItem("Failure time", "minute precision")),
                "failure time and cell identity must correspond per cell");

        PromptTemplate enTemplate = template(
                StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE,
                """
                ## Required Information Items
                {{required_information_items}} (required)
                Requirement: items.
                """);

        String rendered = new InformationProposeGenerator().generate(context(1), content, enTemplate, enVocabulary);

        assertTrue(rendered.endsWith(
                "Relationship between missing items: failure time and cell identity must correspond per cell"));
    }

    @Test
    void endingGeneratorsRenderConclusionLiterals() {
        String infoRendered = new InformationEndingGenerator()
                .generate(
                        context(2, NegotiationPerformative.ACCEPT),
                        new InformationEndingContent(
                                NegotiationConclusion.ACCEPT,
                                List.of(new NegotiationItem("故障发生时间", "2026-08-19 10:30"))),
                        template(StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT, ZH_INFO_ENDING_TEMPLATE),
                        zhVocabulary);

        assertTrue(infoRendered.contains("## 信息协商结果\nAccept"));
        assertTrue(infoRendered.contains("## 信息协商结果内容\n1. 故障发生时间：2026-08-19 10:30"));

        String targetRendered = new TargetEndingGenerator()
                .generate(
                        context(2, NegotiationPerformative.REJECT),
                        new TargetEndingContent(NegotiationConclusion.REJECT, null, "双方未就速率保障下限达成一致。"),
                        template(StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT, ZH_TARGET_ENDING_TEMPLATE),
                        zhVocabulary);

        assertTrue(targetRendered.contains("## 目标协商结果\nReject"));
        assertTrue(targetRendered.contains("## 目标协商结果内容\n双方未就速率保障下限达成一致。"));

        String feasibilityRendered = new FeasibilityEndingGenerator()
                .generate(
                        context(2),
                        new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, "同意将速率保障目标由5Mbps下调至2Mbps。"),
                        template(
                                StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT,
                                ZH_FEASIBILITY_ENDING_TEMPLATE),
                        zhVocabulary);

        assertTrue(feasibilityRendered.contains("## 可行性协商结果\nAccept"));
        assertTrue(feasibilityRendered.contains("## 可行性评估结果确认\n同意将速率保障目标由5Mbps下调至2Mbps。"));
    }

    @Test
    void informationEndingRejectsEmptyResultItems() {
        NegotiationGenerationException exception =
                assertThrows(NegotiationGenerationException.class, () -> new InformationEndingGenerator()
                        .generate(
                                context(2),
                                new InformationEndingContent(NegotiationConclusion.REJECT, List.of()),
                                template(
                                        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT,
                                        ZH_INFO_ENDING_TEMPLATE),
                                zhVocabulary));

        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), exception.getCode());
        assertEquals("items", exception.getFacts().get("field"));
    }

    @Test
    void informationProposeRejectsEmptyRequestedItems() {
        NegotiationGenerationException exception =
                assertThrows(NegotiationGenerationException.class, () -> new InformationProposeGenerator()
                        .generate(
                                context(1),
                                new InformationProposeContent(List.of(), null),
                                template(StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE, ZH_INFO_PROPOSE_TEMPLATE),
                                zhVocabulary));

        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), exception.getCode());
        assertEquals("items", exception.getFacts().get("field"));
    }

    @Test
    void abortConclusionIsRejectedByEveryEndingGenerator() {
        assertThrows(IllegalArgumentException.class, () -> new InformationEndingGenerator()
                .generate(
                        context(1),
                        new InformationEndingContent(NegotiationConclusion.ABORT, List.of()),
                        template(StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT, ZH_INFO_ENDING_TEMPLATE),
                        zhVocabulary));
        assertThrows(IllegalArgumentException.class, () -> new TargetEndingGenerator()
                .generate(
                        context(1),
                        new TargetEndingContent(NegotiationConclusion.ABORT, "意图", null),
                        template(StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT, ZH_TARGET_ENDING_TEMPLATE),
                        zhVocabulary));
        assertThrows(IllegalArgumentException.class, () -> new FeasibilityEndingGenerator()
                .generate(
                        context(1),
                        new FeasibilityEndingContent(NegotiationConclusion.ABORT, "结论"),
                        template(
                                StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT,
                                ZH_FEASIBILITY_ENDING_TEMPLATE),
                        zhVocabulary));
    }

    @Test
    void nullConclusionIsRejected() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new InformationEndingGenerator()
                .generate(
                        context(1),
                        new InformationEndingContent(null, List.of()),
                        template(StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT, ZH_INFO_ENDING_TEMPLATE),
                        zhVocabulary));

        assertTrue(exception.getMessage().contains("conclusion must not be null"));
    }

    @Test
    void targetEndingRequiresTheFieldMatchingTheConclusion() {
        NegotiationGenerationException acceptWithoutIntent =
                assertThrows(NegotiationGenerationException.class, () -> new TargetEndingGenerator()
                        .generate(
                                context(1),
                                new TargetEndingContent(NegotiationConclusion.ACCEPT, null, "失败原因"),
                                template(StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT, ZH_TARGET_ENDING_TEMPLATE),
                                zhVocabulary));

        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), acceptWithoutIntent.getCode());
        assertEquals("content.confirmedIntent", acceptWithoutIntent.getFacts().get("field"));

        NegotiationGenerationException rejectWithoutReason =
                assertThrows(NegotiationGenerationException.class, () -> new TargetEndingGenerator()
                        .generate(
                                context(1),
                                new TargetEndingContent(NegotiationConclusion.REJECT, "  ", null),
                                template(StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT, ZH_TARGET_ENDING_TEMPLATE),
                                zhVocabulary));

        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), rejectWithoutReason.getCode());
        assertEquals("content.failureReason", rejectWithoutReason.getFacts().get("field"));
    }

    @Test
    void feasibilityEndingRequiresTheSummary() {
        NegotiationGenerationException exception =
                assertThrows(NegotiationGenerationException.class, () -> new FeasibilityEndingGenerator()
                        .generate(
                                context(1),
                                new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, " "),
                                template(
                                        StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT,
                                        ZH_FEASIBILITY_ENDING_TEMPLATE),
                                zhVocabulary));

        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), exception.getCode());
        assertEquals("content.feasibilitySummary", exception.getFacts().get("field"));
    }

    @Test
    void generatorRejectsContentOfAnotherRuntimeType() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new InformationProposeGenerator()
                        .generate(
                                context(1),
                                new TargetProposeContent("描述", null, null, null, null),
                                template(StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE, ZH_INFO_PROPOSE_TEMPLATE),
                                zhVocabulary));

        assertTrue(exception.getMessage().contains("requires content of type InformationProposeContent"));
    }

    @Test
    void generatorsRenderAgainstTheBundledTemplates() {
        NegotiationReference reference = NegotiationReference.tryParse(
                        StandardTemplates.TARGET_NEGOTIATION_PROPOSE.uri(), NegotiationPerformative.PROPOSE, "zh-CN")
                .orElseThrow(() -> new AssertionError("expected the bundled target propose template to resolve"));
        PromptTemplate loaded = new DefaultNegotiationTemplateLoader("zh-CN").load(reference);

        TargetProposeContent content = new TargetProposeContent(
                "对无线节能优化任务的意图理解参见<意图理解陈述>，请澄清和确认。",
                List.of(new NegotiationItem("发起方理解", "对方希望在体验无损的前提下降低节能力度")),
                null,
                List.of(new NegotiationItem("节能时间范围", "需要澄清")),
                null);

        String rendered = new TargetProposeGenerator().generate(context(1), content, loaded, zhVocabulary);

        assertTrue(rendered.startsWith("## 目标协商\n"));
        assertFalse(rendered.contains("协商上下文"), "the context section must not be rendered");
        assertTrue(rendered.contains("## 目标协商\n对无线节能优化任务的意图理解参见<意图理解陈述>，请澄清和确认。"));
        assertTrue(rendered.contains("## 意图理解陈述"));
        assertFalse(rendered.contains("## 理解对齐与疑问澄清"));
        assertTrue(rendered.contains("## 待澄清内容\n1. 节能时间范围：需要澄清"));
        assertFalse(rendered.contains("要求："));
        assertFalse(rendered.endsWith("\n"));
    }

    private static NegotiationContext context(int round) {
        return context(round, NegotiationPerformative.PROPOSE);
    }

    private static NegotiationContext context(int round, NegotiationPerformative performative) {
        return new NegotiationContext(CONTEXT_ID, round, 5, performative);
    }

    private static PromptTemplate template(TemplateUri templateUri, String content) {
        return new PromptTemplate(templateUri, "", content, "classpath");
    }
}
