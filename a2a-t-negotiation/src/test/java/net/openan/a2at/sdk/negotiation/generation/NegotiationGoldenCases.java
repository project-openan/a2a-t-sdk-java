package net.openan.a2at.sdk.negotiation.generation;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;

/**
 * Negotiation-module-local copy of the golden fixture inputs for the staying unit behavior tests.
 *
 * <p>The canonical golden fixture system (the {@code GoldenInputs} model, the generator and the comparison guard,
 * plus the committed fixture files) lives in the {@code a2a-t-corpus} consistency module. The unit behavior tests
 * that remain in this module keep asserting byte equality against the same fixed inputs and rendered golden
 * texts, so the needed fragments are copied here: the fixed contexts, the fixed typed content per language, the
 * built-in template URIs and the byte-exact golden texts of the committed fixtures inlined as constants.
 *
 * <p>Fixed inputs of the golden fixture set of the negotiation content layer.
 *
 * <p>Every golden fixture is rendered from exactly one {@link GoldenCase} carrying a fixed negotiation context, fixed
 * typed content and the built-in template URI of its negotiation type and phase. The fixture data lives in the
 * private-line complaint diagnosis business domain (see {@code docs-local/business-facts-dictionary.md}): the
 * information fixtures carry the access port name and complaint category of the OMC workbench complaint loop, the
 * target fixtures negotiate the latency repair target, and the feasibility fixtures assess the port expansion.
 * The typed content is language-dependent — zh-CN fixtures carry Chinese business data, en-US fixtures the English
 * business form — so each language renders its own golden fixture with real business data in that language.
 *
 * <p>The fixture data deliberately exercises the known rendering pitfalls: a non-null relationship with an appended
 * line and a null-value item (information propose), the round-driven conditional sections with a non-empty
 * clarification list (target propose, round 1), the action-driven conditional section (feasibility propose, evaluation
 * request action), the confirm-request category rendering only the summary and the fixed confirm request (target and
 * feasibility propose confirm), the accept and reject conclusion literals, the feasibility summary rendered into the
 * vocabulary-exception slot of the feasibility result confirmation section, and the common abort template rendering
 * the termination reason.
 */
public final class NegotiationGoldenCases {

    /** Fixed negotiation session id shared by every golden fixture. */
    public static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    /** Language with bundled zh-CN negotiation resources. */
    public static final String ZH_CN = "zh-CN";

    /** Language with bundled en-US negotiation resources. */
    public static final String EN_US = "en-US";

    /** Both languages covered by the golden fixture set, in a fixed order. */
    public static final List<String> LANGUAGES = List.of(ZH_CN, EN_US);

    /** Golden fixture text of zh-CN/abort. */
    private static final String GOLDEN_zh_CN_ABORT = "## 协商结果\nAbort\n\n## 协商终止原因\n已达到协商轮次上限，本次协商确认终止。";
    /** Golden fixture text of zh-CN/feasibility_accept. */
    private static final String GOLDEN_zh_CN_FEASIBILITY_ACCEPT = "## 可行性协商结果\nAccept\n\n## 可行性评估结果确认\n端口扩容方案可在割接窗口内完成，业务中断时长满足不超过30分钟的约束；本次可行性协商确认结束。";
    /** Golden fixture text of zh-CN/feasibility_propose. */
    private static final String GOLDEN_zh_CN_FEASIBILITY_PROPOSE = "## 可行性协商\n请评估专线接入端口扩容方案能否在割接窗口内完成，见<待评估内容说明>；请评估。\n\n## 待评估内容说明\n1. 扩容方案：接入端口带宽由100Mbps扩容至1000Mbps\n2. 既有约束：割接窗口仅限2026年5月30日02:00-06:00，业务中断不超过30分钟";
    /** Golden fixture text of zh-CN/feasibility_reject. */
    private static final String GOLDEN_zh_CN_FEASIBILITY_REJECT = "## 可行性协商结果\nReject\n\n## 可行性评估结果确认\n受汇聚机房板卡槽位不足限制，端口扩容无法在指定割接窗口内完成；本次可行性协商确认结束。";
    /** Golden fixture text of zh-CN/information_accept. */
    private static final String GOLDEN_zh_CN_INFORMATION_ACCEPT = "## 信息协商结果\nAccept\n\n## 信息协商结果内容\n1. 接入端口名称：P533-珠江旧城-PTN3900-23-TPA1EG24-1\n2. 投诉分类：专线质差";
    /** Golden fixture text of zh-CN/information_propose. */
    private static final String GOLDEN_zh_CN_INFORMATION_PROPOSE = "## 信息协商\n请根据[所需信息项](#所需信息项)补充相关内容。\n\n## 所需信息项\n1. 接入端口名称：举例：P533-珠江旧城-PTN3900-23-TPA1EG24-1\n2. 投诉分类：举例：专线质差\n3. 专线业务标识\n缺失项之间的关系：OR";
    /** Golden fixture text of zh-CN/information_reject. */
    private static final String GOLDEN_zh_CN_INFORMATION_REJECT = "## 信息协商结果\nReject\n\n## 信息协商结果内容\n1. 接入端口名称：无法提供，工作台侧端口资源台账暂不可查";
    /** Golden fixture text of zh-CN/target_accept. */
    private static final String GOLDEN_zh_CN_TARGET_ACCEPT = "## 目标协商结果\nAccept\n\n## 目标协商结果内容\n最终确认的意图：在2026年5月15日前将深圳至广州专线的平均时延恢复至20ms以内，高峰时段丢包率不高于1%。";
    /** Golden fixture text of zh-CN/target_propose. */
    private static final String GOLDEN_zh_CN_TARGET_PROPOSE = "## 目标协商\n专线质差投诉的修复目标协商意图见<意图理解陈述>；时延目标与完成时限仍存在待澄清问题，见<待澄清内容>；请澄清并确认。\n\n## 意图理解陈述\n1. 修复意图：在2026年5月15日前将深圳至广州专线的平均时延恢复至20ms以内\n2. 修复目标：高峰时段丢包率不高于1%\n\n## 待澄清内容\n1. 时延目标：平均时延目标是恢复至20ms以内还是50ms以内\n2. 完成时限：修复完成时限是48小时内还是72小时内";
    /** Golden fixture text of zh-CN/target_propose_confirm. */
    private static final String GOLDEN_zh_CN_TARGET_PROPOSE_CONFIRM = "## 目标协商\n任务目标澄清完成，请答复<目标澄清后的确认请求>。\n\n## 目标澄清后的确认请求\n目标已经澄清，是否同意按照此目标继续执行？";
    /** Golden fixture text of zh-CN/feasibility_propose_confirm. */
    private static final String GOLDEN_zh_CN_FEASIBILITY_PROPOSE_CONFIRM = "## 可行性协商\n针对调整后的速率保障目标，可行性评估已完成，结论为可行，请答复<评估可行时的确认请求>。\n\n## 评估可行时的确认请求\n评估目标可行，是否同意按照此目标继续执行？";
    /** Golden fixture text of zh-CN/target_reject. */
    private static final String GOLDEN_zh_CN_TARGET_REJECT = "## 目标协商结果\nReject\n\n## 目标协商结果内容\n因光缆割接窗口尚未确认，时延目标未能完全澄清。";
    /** Golden fixture text of en-US/target_propose_confirm. */
    private static final String GOLDEN_en_US_TARGET_PROPOSE_CONFIRM = "## Target Negotiation\nThe clarification of the task target has been completed. Please reply to <Target Clarification Confirmation Request>.\n\n## Target Clarification Confirmation Request\nThe target has been clarified. Do you agree to proceed with this target?";
    /** Golden fixture text of en-US/feasibility_propose_confirm. */
    private static final String GOLDEN_en_US_FEASIBILITY_PROPOSE_CONFIRM = "## Feasibility Negotiation\nRegarding the adjusted rate guarantee target, the feasibility assessment has been completed and the conclusion is feasible. Please reply to <Feasible Evaluation Confirmation Request>.\n\n## Feasible Evaluation Confirmation Request\nThe target is assessed as feasible. Do you agree to proceed with this target?";
    /** Golden fixture text of en-US/abort. */
    private static final String GOLDEN_en_US_ABORT = "## Negotiation Result\nAbort\n\n## Negotiation Termination Reason\nThe negotiation round limit is reached; this negotiation is confirmed as terminated.";
    /** Golden fixture text of en-US/feasibility_accept. */
    private static final String GOLDEN_en_US_FEASIBILITY_ACCEPT = "## Feasibility Negotiation Result\nAccept\n\n## Feasibility Assessment Result Confirmation\nThe port expansion can be completed within the cutover window and satisfies the 30-minute interruption constraint; this negotiation is confirmed as concluded.";
    /** Golden fixture text of en-US/feasibility_propose. */
    private static final String GOLDEN_en_US_FEASIBILITY_PROPOSE = "## Feasibility Negotiation\nPlease assess whether the dedicated-line access port expansion can be completed within the cutover window, see <Under Evaluation Description>; please assess.\n\n## Under Evaluation Description\n1. expansion plan: expand the access port bandwidth from 100Mbps to 1000Mbps\n2. existing constraint: the cutover window is limited to 02:00-06:00 on 2026-05-30 with service interruption no longer than 30 minutes";
    /** Golden fixture text of en-US/feasibility_reject. */
    private static final String GOLDEN_en_US_FEASIBILITY_REJECT = "## Feasibility Negotiation Result\nReject\n\n## Feasibility Assessment Result Confirmation\nThe port expansion cannot be completed within the designated cutover window because of insufficient board slots in the aggregation room; this negotiation is confirmed as concluded.";
    /** Golden fixture text of en-US/information_accept. */
    private static final String GOLDEN_en_US_INFORMATION_ACCEPT = "## Information Negotiation Result\nAccept\n\n## Information Negotiation Result Content\n1. Access Port Name: P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1\n2. Complaint Category: dedicated-line quality degradation";
    /** Golden fixture text of en-US/information_propose. */
    private static final String GOLDEN_en_US_INFORMATION_PROPOSE = "## Information Negotiation\nPlease supplement the relevant content based on [Required Information Items](#required-information-items).\n\n## Required Information Items\n1. Access Port Name: e.g. P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1\n2. Complaint Category: e.g. dedicated-line quality degradation\n3. Private Line Service Identifier\nRelationship between missing items: OR";
    /** Golden fixture text of en-US/information_reject. */
    private static final String GOLDEN_en_US_INFORMATION_REJECT = "## Information Negotiation Result\nReject\n\n## Information Negotiation Result Content\n1. Access Port Name: cannot be provided because the port inventory is temporarily unavailable on the workbench side";
    /** Golden fixture text of en-US/target_accept. */
    private static final String GOLDEN_en_US_TARGET_ACCEPT = "## Target Negotiation Result\nAccept\n\n## Target Negotiation Result Content\nThe finally confirmed intent: restore the average latency of the Shenzhen-to-Guangzhou dedicated line to within 20ms before 2026-05-15, with a peak-hour packet loss rate no higher than 1%.";
    /** Golden fixture text of en-US/target_propose. */
    private static final String GOLDEN_en_US_TARGET_PROPOSE = "## Target Negotiation\nThe intent understanding of the dedicated-line quality degradation repair target is listed in <Intent Understanding Statement>; open questions remain about the latency target and the completion deadline, see <Content to Clarify>; please clarify and confirm.\n\n## Intent Understanding Statement\n1. repair intent: restore the average latency of the Shenzhen-to-Guangzhou dedicated line to within 20ms before 2026-05-15\n2. repair target: peak-hour packet loss rate no higher than 1%\n\n## Content to Clarify\n1. latency target: is the average latency target within 20ms or within 50ms\n2. completion deadline: is the repair deadline 48 hours or 72 hours";
    /** Golden fixture text of en-US/target_reject. */
    private static final String GOLDEN_en_US_TARGET_REJECT = "## Target Negotiation Result\nReject\n\n## Target Negotiation Result Content\nThe latency target cannot be clarified in full because the fiber cutover window is not confirmed yet.";

    private static final Map<String, String> GOLDEN_TEXTS = Map.ofEntries(
            Map.entry("zh-CN/abort", GOLDEN_zh_CN_ABORT),
            Map.entry("zh-CN/feasibility_accept", GOLDEN_zh_CN_FEASIBILITY_ACCEPT),
            Map.entry("zh-CN/feasibility_propose", GOLDEN_zh_CN_FEASIBILITY_PROPOSE),
            Map.entry("zh-CN/feasibility_propose_confirm", GOLDEN_zh_CN_FEASIBILITY_PROPOSE_CONFIRM),
            Map.entry("zh-CN/feasibility_reject", GOLDEN_zh_CN_FEASIBILITY_REJECT),
            Map.entry("zh-CN/information_accept", GOLDEN_zh_CN_INFORMATION_ACCEPT),
            Map.entry("zh-CN/information_propose", GOLDEN_zh_CN_INFORMATION_PROPOSE),
            Map.entry("zh-CN/information_reject", GOLDEN_zh_CN_INFORMATION_REJECT),
            Map.entry("zh-CN/target_accept", GOLDEN_zh_CN_TARGET_ACCEPT),
            Map.entry("zh-CN/target_propose", GOLDEN_zh_CN_TARGET_PROPOSE),
            Map.entry("zh-CN/target_propose_confirm", GOLDEN_zh_CN_TARGET_PROPOSE_CONFIRM),
            Map.entry("zh-CN/target_reject", GOLDEN_zh_CN_TARGET_REJECT),
            Map.entry("en-US/abort", GOLDEN_en_US_ABORT),
            Map.entry("en-US/feasibility_accept", GOLDEN_en_US_FEASIBILITY_ACCEPT),
            Map.entry("en-US/feasibility_propose", GOLDEN_en_US_FEASIBILITY_PROPOSE),
            Map.entry("en-US/feasibility_propose_confirm", GOLDEN_en_US_FEASIBILITY_PROPOSE_CONFIRM),
            Map.entry("en-US/feasibility_reject", GOLDEN_en_US_FEASIBILITY_REJECT),
            Map.entry("en-US/information_accept", GOLDEN_en_US_INFORMATION_ACCEPT),
            Map.entry("en-US/information_propose", GOLDEN_en_US_INFORMATION_PROPOSE),
            Map.entry("en-US/information_reject", GOLDEN_en_US_INFORMATION_REJECT),
            Map.entry("en-US/target_accept", GOLDEN_en_US_TARGET_ACCEPT),
            Map.entry("en-US/target_propose", GOLDEN_en_US_TARGET_PROPOSE),
            Map.entry("en-US/target_propose_confirm", GOLDEN_en_US_TARGET_PROPOSE_CONFIRM),
            Map.entry("en-US/target_reject", GOLDEN_en_US_TARGET_REJECT));

    private NegotiationGoldenCases() {}

    /**
     * Returns the default fixture context: round 2 of at most 5 rounds, carrying the fixture's performative.
     *
     * @param performative performative of the fixture the context belongs to
     * @return fixed negotiation context of every fixture except the target propose fixture
     */
    public static NegotiationContext defaultContext(NegotiationPerformative performative) {
        return new NegotiationContext(SESSION_ID, 2, 5, performative);
    }

    /**
     * Returns the first-round fixture context used by the target propose fixture, carrying the fixture's performative.
     *
     * @param performative performative of the fixture the context belongs to
     * @return fixed negotiation context of round 1 of at most 5 rounds
     */
    public static NegotiationContext firstRoundContext(NegotiationPerformative performative) {
        return new NegotiationContext(SESSION_ID, 1, 5, performative);
    }

    /** One golden fixture case: one negotiation type, performative, fixed context, fixed content and its template URI. */
    public enum GoldenCase {

        /**
         * Information propose fixture of the complaint diagnosis loop: the OMC asks the workbench for the missing
         * access port name and complaint category, plus an optional null-value private line service identifier.
         */
        INFORMATION_PROPOSE(NegotiationPerformative.PROPOSE, "information-negotiation", "information_propose") {
            @Override
            public NegotiationContext context() {
                return NegotiationGoldenCases.defaultContext(performative());
            }

            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new InformationProposeContent(
                            List.of(
                                    new NegotiationItem(
                                            "Access Port Name",
                                            "e.g. P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1"),
                                    new NegotiationItem(
                                            "Complaint Category", "e.g. dedicated-line quality degradation"),
                                    new NegotiationItem("Private Line Service Identifier", null)),
                            "OR");
                }
                return new InformationProposeContent(
                        List.of(
                                new NegotiationItem("接入端口名称", "举例：P533-珠江旧城-PTN3900-23-TPA1EG24-1"),
                                new NegotiationItem("投诉分类", "举例：专线质差"),
                                new NegotiationItem("专线业务标识", null)),
                        "OR");
            }
        },

        /**
         * Target propose fixture of round 1: the workbench and the OMC clarify the latency repair target and the
         * completion deadline of the quality degradation complaint.
         */
        TARGET_PROPOSE(NegotiationPerformative.PROPOSE, "target-negotiation", "target_propose") {
            @Override
            public NegotiationContext context() {
                return NegotiationGoldenCases.firstRoundContext(performative());
            }

            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new TargetProposeContent(
                            "The intent understanding of the dedicated-line quality degradation repair target is"
                                    + " listed in <Intent Understanding Statement>; open questions remain about the"
                                    + " latency target and the completion deadline, see <Content to Clarify>; please"
                                    + " clarify and confirm.",
                            List.of(
                                    new NegotiationItem(
                                            "repair intent",
                                            "restore the average latency of the Shenzhen-to-Guangzhou dedicated"
                                                    + " line to within 20ms before 2026-05-15"),
                                    new NegotiationItem(
                                            "repair target", "peak-hour packet loss rate no higher than 1%")),
                            null,
                            List.of(
                                    new NegotiationItem(
                                            "latency target",
                                            "is the average latency target within 20ms or within 50ms"),
                                    new NegotiationItem(
                                            "completion deadline",
                                            "is the repair deadline 48 hours or 72 hours")),
                            null);
                }
                return new TargetProposeContent(
                        "专线质差投诉的修复目标协商意图见<意图理解陈述>；时延目标与完成时限仍存在待澄清问题，见<待澄清内容>；请澄清并确认。",
                        List.of(
                                new NegotiationItem(
                                        "修复意图", "在2026年5月15日前将深圳至广州专线的平均时延恢复至20ms以内"),
                                new NegotiationItem("修复目标", "高峰时段丢包率不高于1%")),
                        null,
                        List.of(
                                new NegotiationItem("时延目标", "平均时延目标是恢复至20ms以内还是50ms以内"),
                                new NegotiationItem("完成时限", "修复完成时限是48小时内还是72小时内")),
                        null);
            }
        },

        /**
         * Target propose confirm-request fixture of a later round: the clarification is complete, so the message
         * carries only the summary and the fixed confirm request of the "target clarified and requesting
         * confirmation" category.
         */
        TARGET_PROPOSE_CONFIRM(NegotiationPerformative.PROPOSE, "target-negotiation", "target_propose_confirm") {
            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new TargetProposeContent(
                            "The clarification of the task target has been completed. Please reply to <Target"
                                    + " Clarification Confirmation Request>.",
                            null,
                            null,
                            null,
                            "The target has been clarified. Do you agree to proceed with this target?");
                }
                return new TargetProposeContent(
                        "任务目标澄清完成，请答复<目标澄清后的确认请求>。",
                        null,
                        null,
                        null,
                        "目标已经澄清，是否同意按照此目标继续执行？");
            }
        },

        /**
         * Feasibility propose fixture requesting a feasibility evaluation of the access port bandwidth expansion
         * against the cutover window constraint.
         */
        FEASIBILITY_PROPOSE(NegotiationPerformative.PROPOSE, "feasibility-negotiation", "feasibility_propose") {
            @Override
            public NegotiationContext context() {
                return NegotiationGoldenCases.defaultContext(performative());
            }

            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new FeasibilityProposeContent(
                            "Please assess whether the dedicated-line access port expansion can be completed within"
                                    + " the cutover window, see <Under Evaluation Description>; please assess.",
                            NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                            List.of(
                                    new NegotiationItem(
                                            "expansion plan",
                                            "expand the access port bandwidth from 100Mbps to 1000Mbps"),
                                    new NegotiationItem(
                                            "existing constraint",
                                            "the cutover window is limited to 02:00-06:00 on 2026-05-30 with"
                                                    + " service interruption no longer than 30 minutes")),
                            null,
                            null);
                }
                return new FeasibilityProposeContent(
                        "请评估专线接入端口扩容方案能否在割接窗口内完成，见<待评估内容说明>；请评估。",
                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                        List.of(
                                new NegotiationItem("扩容方案", "接入端口带宽由100Mbps扩容至1000Mbps"),
                                new NegotiationItem(
                                        "既有约束", "割接窗口仅限2026年5月30日02:00-06:00，业务中断不超过30分钟")),
                        null,
                        null);
            }
        },

        /**
         * Feasibility propose confirm-request fixture of the "assessed as feasible and requesting confirmation"
         * category: the assessment is complete, so the message carries only the summary and the fixed confirm
         * request of the goal-achievement wording.
         */
        FEASIBILITY_PROPOSE_CONFIRM(
                NegotiationPerformative.PROPOSE, "feasibility-negotiation", "feasibility_propose_confirm") {
            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new FeasibilityProposeContent(
                            "Regarding the adjusted rate guarantee target, the feasibility assessment has been"
                                    + " completed and the conclusion is feasible. Please reply to <Feasible Evaluation"
                                    + " Confirmation Request>.",
                            NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                            null,
                            null,
                            "The target is assessed as feasible. Do you agree to proceed with this target?");
                }
                return new FeasibilityProposeContent(
                        "针对调整后的速率保障目标，可行性评估已完成，结论为可行，请答复<评估可行时的确认请求>。",
                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                        null,
                        null,
                        "评估目标可行，是否同意按照此目标继续执行？");
            }
        },

        /** Information accept fixture delivering the access port name and the complaint category. */
        INFORMATION_ACCEPT(NegotiationPerformative.ACCEPT, "information-negotiation", "information_accept") {
            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new InformationEndingContent(
                            NegotiationConclusion.ACCEPT,
                            List.of(
                                    new NegotiationItem(
                                            "Access Port Name", "P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1"),
                                    new NegotiationItem(
                                            "Complaint Category", "dedicated-line quality degradation")));
                }
                return new InformationEndingContent(
                        NegotiationConclusion.ACCEPT,
                        List.of(
                                new NegotiationItem("接入端口名称", "P533-珠江旧城-PTN3900-23-TPA1EG24-1"),
                                new NegotiationItem("投诉分类", "专线质差")));
            }
        },

        /** Target accept fixture carrying the finally confirmed latency repair target. */
        TARGET_ACCEPT(NegotiationPerformative.ACCEPT, "target-negotiation", "target_accept") {
            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new TargetEndingContent(
                            NegotiationConclusion.ACCEPT,
                            "The finally confirmed intent: restore the average latency of the"
                                    + " Shenzhen-to-Guangzhou dedicated line to within 20ms before 2026-05-15, with a"
                                    + " peak-hour packet loss rate no higher than 1%.",
                            null);
                }
                return new TargetEndingContent(
                        NegotiationConclusion.ACCEPT,
                        "最终确认的意图：在2026年5月15日前将深圳至广州专线的平均时延恢复至20ms以内，高峰时段丢包率不高于1%。",
                        null);
            }
        },

        /** Feasibility accept fixture confirming a positive port expansion assessment in the exception slot. */
        FEASIBILITY_ACCEPT(NegotiationPerformative.ACCEPT, "feasibility-negotiation", "feasibility_accept") {
            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new FeasibilityEndingContent(
                            NegotiationConclusion.ACCEPT,
                            "The port expansion can be completed within the cutover window and satisfies the"
                                    + " 30-minute interruption constraint; this negotiation is confirmed as"
                                    + " concluded.");
                }
                return new FeasibilityEndingContent(
                        NegotiationConclusion.ACCEPT,
                        "端口扩容方案可在割接窗口内完成，业务中断时长满足不超过30分钟的约束；本次可行性协商确认结束。");
            }
        },

        /** Information reject fixture stating that the access port name cannot be provided. */
        INFORMATION_REJECT(NegotiationPerformative.REJECT, "information-negotiation", "information_reject") {
            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new InformationEndingContent(
                            NegotiationConclusion.REJECT,
                            List.of(new NegotiationItem(
                                    "Access Port Name",
                                    "cannot be provided because the port inventory is temporarily unavailable on"
                                            + " the workbench side")));
                }
                return new InformationEndingContent(
                        NegotiationConclusion.REJECT,
                        List.of(new NegotiationItem("接入端口名称", "无法提供，工作台侧端口资源台账暂不可查")));
            }
        },

        /** Target reject fixture carrying the failure reason of the unclarified latency target. */
        TARGET_REJECT(NegotiationPerformative.REJECT, "target-negotiation", "target_reject") {
            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new TargetEndingContent(
                            NegotiationConclusion.REJECT,
                            null,
                            "The latency target cannot be clarified in full because the fiber cutover window is"
                                    + " not confirmed yet.");
                }
                return new TargetEndingContent(
                        NegotiationConclusion.REJECT, null, "因光缆割接窗口尚未确认，时延目标未能完全澄清。");
            }
        },

        /** Feasibility reject fixture confirming a negative port expansion assessment in the exception slot. */
        FEASIBILITY_REJECT(NegotiationPerformative.REJECT, "feasibility-negotiation", "feasibility_reject") {
            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new FeasibilityEndingContent(
                            NegotiationConclusion.REJECT,
                            "The port expansion cannot be completed within the designated cutover window because"
                                    + " of insufficient board slots in the aggregation room; this negotiation is"
                                    + " confirmed as concluded.");
                }
                return new FeasibilityEndingContent(
                        NegotiationConclusion.REJECT,
                        "受汇聚机房板卡槽位不足限制，端口扩容无法在指定割接窗口内完成；本次可行性协商确认结束。");
            }
        },

        /** Common abort fixture terminating the negotiation at the round limit. */
        ABORT(NegotiationPerformative.ABORT, "common", "abort") {
            @Override
            public NegotiationContent content(String language) {
                if (NegotiationGoldenCases.EN_US.equals(language)) {
                    return new NegotiationAbortContent(
                            "The negotiation round limit is reached; this negotiation is confirmed as terminated.");
                }
                return new NegotiationAbortContent("已达到协商轮次上限，本次协商确认终止。");
            }
        };

        private final NegotiationPerformative performative;

        private final String typeSegment;

        private final String fileName;

        GoldenCase(NegotiationPerformative performative, String typeSegment, String fileName) {
            this.performative = performative;
            this.typeSegment = typeSegment;
            this.fileName = fileName;
        }

        /**
         * Returns the API-level performative of this fixture.
         *
         * @return propose, accept, reject or abort performative
         */
        public NegotiationPerformative performative() {
            return performative;
        }

        /**
         * Returns the built-in template URI addressed by this fixture as a typed value.
         *
         * @return template URI such as {@code Negotiation-T/information-negotiation/propose/v1}
         */
        public TemplateUri template() {
            return TemplateUri.of(StandardTemplates.NEGOTIATION_EXTENSION_NAME, typeSegment,
                    NegotiationReference.uriSegmentOf(performative));
        }

        /**
         * Returns the built-in template URI addressed by this fixture.
         *
         * @return template URI such as {@code Negotiation-T/information-negotiation/propose/v1}
         */
        public String templateUri() {
            return template().uri();
        }

        /**
         * Returns the byte-exact golden fixture text of this case for one language.
         *
         * @param language locale identifier such as {@code zh-CN}
         * @return the committed golden fixture text with LF line endings
         */
        public String goldenText(String language) {
            String text = GOLDEN_TEXTS.get(language + "/" + fileName);
            if (text == null) {
                throw new IllegalStateException("No golden fixture text pinned for " + fileName + " in " + language);
            }
            return text;
        }

        /**
         * Returns the fixed negotiation context of this fixture.
         *
         * @return fixed negotiation context
         */
        public NegotiationContext context() {
            return NegotiationGoldenCases.defaultContext(performative);
        }

        /**
         * Returns the fixed typed content of this fixture in the given language.
         *
         * @param language locale identifier such as {@code zh-CN}; selects the business data language
         * @return fixed propose or ending content carrying that language's business data
         */
        public abstract NegotiationContent content(String language);

        /**
         * Generates this fixture through one orchestrator, using the from-data method of the fixture performative.
         *
         * @param orchestrator orchestrator wired with the resources of the fixture language
         * @param language locale identifier such as {@code zh-CN}; selects the business data language
         * @return generated metadata content of this fixture
         */
        public MetadataContent generate(NegotiationGenerationOrchestrator orchestrator, String language) {
            return switch (performative) {
                case PROPOSE -> orchestrator.generateProposeFromData(
                        new NegotiationProposeData(context(), (NegotiationProposeContent) content(language)),
                        template());
                case ACCEPT -> orchestrator.generateAcceptFromData(
                        new NegotiationEndingData(context(), (NegotiationEndingContent) content(language)), template());
                case REJECT -> orchestrator.generateRejectFromData(
                        new NegotiationEndingData(context(), (NegotiationEndingContent) content(language)), template());
                case ABORT -> orchestrator.generateAbortFromData(
                        new NegotiationAbortData(context(), (NegotiationAbortContent) content(language)), template());
            };
        }
    }
}
