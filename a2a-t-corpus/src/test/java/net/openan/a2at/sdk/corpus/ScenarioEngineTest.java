package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.corpus.ScenarioCase.ExpectFlow;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests of the {@link ScenarioEngine} against inline scenario objects: the {@code prompt.fromStep}
 * resolution, the fail-fast step execution, and the three {@code expectFlow} fields (terminal condition, rounds used,
 * pairwise distinct messages).
 */
class ScenarioEngineTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String ZH_CN = "zh-CN";

    private static final String INFORMATION_PROPOSE_URI = "Negotiation-T/information-negotiation/propose/v1";

    private static final String INFORMATION_ACCEPT_REJECT_URI =
            "Negotiation-T/information-negotiation/accept-reject/v1";

    private static final String ACCEPT_PAYLOAD =
            "{\"conclusion\":\"Accept\",\"items\":[{\"name\":\"接入端口名称\",\"value\":\"P533-珠江旧城"
                    + "-PTN3900-23-TPA1EG24-1\"},{\"name\":\"投诉分类\",\"value\":\"专线质差\"}]}";

    private static final String PROPOSE_PAYLOAD =
            "{\"items\":[{\"name\":\"接入端口名称\",\"value\":\"举例：P533-珠江旧城-PTN3900-23-TPA1EG24-1\"},"
                    + "{\"name\":\"投诉分类\",\"value\":\"举例：专线质差\"},{\"name\":\"专线业务标识\","
                    + "\"value\":null}],\"relationship\":\"OR\"}";

    private static final String SEMANTIC_ACCEPT_PAYLOAD =
            "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}";

    private static final String PRIVATE_LINE_COMPLAINT_URI = "Task-T/network-layer/private-line-complaint/v1";

    private static final Map<String, String> CLOSED_LOOP_ROLES =
            Map.of("A", "工作台（client，任务发起/补数方）", "B", "OMC（server，执行/要数方，协商发起方）");

    /** The workbench raw complaint (样例步骤1): no access port name and no complaint category in the text. */
    private static final String COMPLAINT_TEXT =
            "深圳访问广州的专线从5月11号早上8点半开始响应时延从平均12ms骤升至320ms，柜面和手机银行的交易接口频繁报" + "“连接超时”。OSS侧事件流水号：event-id-20260511-09013。";

    /**
     * Slot-extraction payload of the closed loop's step 1 since the task_object slot became required upstream: the task
     * object carries what the raw text names (the circuit) but no port name, so the rendered prompt stays portless and
     * the two schema parameters stay missing for the negotiation to fill.
     */
    private static final String TASK_SLOTS_OBJECT_PORTLESS =
            "{\"slots\": {\"任务对象\": \"深圳访问广州的专线\", \"任务上下文\": \"投诉分类：待补充；问题发生时间：2026-05-11T08:21:46Z；"
                    + "OSS侧事件流水号：event-id-20260511-09013；投诉详情：深圳访问广州的响应时延从平均12ms骤升至320ms\"},"
                    + " \"slot_errors\": []}";

    /**
     * Semantic payload of the closed loop's step 2: the OMC finds the access port name and the complaint category
     * missing — the two null-valued parameters driving the information negotiation.
     */
    private static final String TASK_SEMANTIC_MISSING_PARAMS =
            "{\"semantic_verdict\":true,\"errors\":[],\"params\":{\"accessPort\":null,\"bizScenario\":null,"
                    + "\"faultTime\":\"2026-05-11T08:21:46Z\",\"eventSerialNo\":\"event-id-20260511-09013\"}}";

    /** Semantic payload of the closed loop's final step: the accept message carries both previously missing values. */
    private static final String SEMANTIC_ACCEPT_WITH_FILLED_PARAMS =
            "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":"
                    + "{\"accessPort\":\"P533-珠江旧城-PTN3900-23-TPA1EG24-1\",\"bizScenario\":\"专线质差\"}}";

    /** Semantic payload filling only one of the two missing parameters (the unfilled-parameter red path). */
    private static final String SEMANTIC_ACCEPT_PARTIALLY_FILLED =
            "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":"
                    + "{\"accessPort\":\"P533-珠江旧城-PTN3900-23-TPA1EG24-1\"}}";

    private static final String TASK_PARAM_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"accessPort\":{\"type\":\"string\"},\"bizScenario\":"
                    + "{\"type\":\"string\"},\"faultTime\":{\"type\":\"string\"},\"eventSerialNo\":"
                    + "{\"type\":\"string\"}},\"required\":[\"accessPort\",\"bizScenario\"]}";

    private final ScenarioEngine engine = new ScenarioEngine();

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode schema;

    @BeforeEach
    void readSchema() throws Exception {
        schema = mapper.readTree("{\"type\":\"object\",\"properties\":{\"accessPort\":{\"type\":\"string\"}}}");
    }

    // ------------------------------------------------------------------ fromStep resolution

    @Test
    void runsATwoStepScenarioResolvingTheFromStepPrompt() {
        ScenarioCase scenario = scenario(
                "SC-INFO-01",
                List.of(
                        step(1, "A", acceptStep("SC-INFO-01", ok(1, "information_accept"))),
                        step(2, "B", validateAcceptStep("SC-INFO-01", 1, ok(1, null)))),
                new ScenarioCase.ExpectFlow("accept", 2, null));

        engine.runScenario(scenario);
    }

    @Test
    void failsWhenAFromStepReferencesAnUnknownStep() {
        ScenarioCase scenario =
                scenario("SC-INFO-02", List.of(step(1, "A", validateAcceptStep("SC-INFO-02", 3, ok(1, null)))), null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("SC-INFO-02/zh-CN")
                        && failure.getMessage().contains("prompt.fromStep 3")
                        && failure.getMessage().contains("produced no prompt text"),
                "the unknown fromStep reference must fail with the scenario id and the step number but was: "
                        + failure.getMessage());
    }

    // ------------------------------------------------------------------ fail-fast

    @Test
    void failsFastWhenAStepFails() {
        NegotiationCase failingStep = stepCase(
                "SC-ERR-01",
                1,
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                new LlmScript(null, List.of(new LlmScriptStep.Fail(LlmFailMarker.NON_JSON))),
                null,
                null,
                failed("NegotiationGenerationException", "llm.response_invalid", null));
        ScenarioCase scenario = scenario(
                "SC-ERR-01",
                List.of(step(1, "A", failingStep), step(2, "B", validateAcceptStep("SC-ERR-01", 1, ok(1, null)))),
                null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("#step-1") && failure.getMessage().contains("$.expect.code"),
                "the scenario must abort on the failing step 1 but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ expectFlow

    @Test
    void assertsDistinctMessagesAndRoundsUsedAcrossSteps() {
        ScenarioCase scenario = scenario(
                "SC-INFO-03",
                List.of(
                        step(1, "A", proposeStep("SC-INFO-03", 2, ok(1, "information_propose"))),
                        step(2, "B", acceptStep("SC-INFO-03", 3, ok(1, "information_accept")))),
                new ScenarioCase.ExpectFlow("accept", 3, Boolean.TRUE));

        engine.runScenario(scenario);
    }

    @Test
    void distinctMessagesFailsOnDuplicateMessages() {
        ScenarioCase scenario = scenario(
                "SC-INFO-04",
                List.of(
                        step(1, "A", acceptStep("SC-INFO-04", 2, ok(1, "information_accept"))),
                        step(2, "B", acceptStep("SC-INFO-04", 3, ok(1, "information_accept")))),
                new ScenarioCase.ExpectFlow("accept", 3, Boolean.TRUE));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("$.expectFlow.distinctMessages"),
                "duplicate messages must fail the distinctMessages expectation but was: " + failure.getMessage());
    }

    @Test
    void exhaustedRequiresTheRoundLimitToBeReached() {
        ScenarioCase exhausted = scenario(
                "SC-EXH-01",
                List.of(step(1, "A", acceptStep("SC-EXH-01", 5, ok(1, "information_accept")))),
                new ScenarioCase.ExpectFlow("exhausted", 5, null));
        engine.runScenario(exhausted);

        ScenarioCase notExhausted = scenario(
                "SC-EXH-02",
                List.of(step(1, "A", acceptStep("SC-EXH-02", 2, ok(1, "information_accept")))),
                new ScenarioCase.ExpectFlow("exhausted", 2, null));
        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(notExhausted));

        assertTrue(
                failure.getMessage().contains("$.expectFlow.terminalCondition"),
                "a round below the limit must fail the exhausted expectation but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ closed loop: 缺参 -> 补参 (Q21)

    @Test
    void runsTheClosedLoopScenarioLinkingMissingParamsToTheFilledParams() throws Exception {
        ScenarioCase scenario = closedLoopScenario(
                SEMANTIC_ACCEPT_WITH_FILLED_PARAMS, new ScenarioCase.ExpectFlow("accept", 2, Boolean.TRUE, 2), 2);

        engine.runScenario(scenario);
    }

    @Test
    void paramsFromStepFailsWhenTheReferencedStepProducedNoFilledParams() throws Exception {
        ScenarioCase scenario = closedLoopScenario(SEMANTIC_ACCEPT_WITH_FILLED_PARAMS, null, 1);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("expect.paramsFromStep 1")
                        && failure.getMessage().contains("produced no filled parameter data")
                        && failure.getMessage().contains("B=OMC（server，执行/要数方，协商发起方）"),
                "the dangling paramsFromStep reference must fail with role semantics but was: " + failure.getMessage());
    }

    @Test
    void paramsFromStepFailsWhenAParameterStaysUnfilled() throws Exception {
        ScenarioCase scenario = closedLoopScenario(SEMANTIC_ACCEPT_PARTIALLY_FILLED, null, 2);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("expect.paramsFromStep 2")
                        && failure.getMessage().contains("still missing: bizScenario")
                        && failure.getMessage().contains("#step-4"),
                "an unfilled missing parameter must fail the causal assertion but was: " + failure.getMessage());
    }

    @Test
    void missingParamsFilledFailsWhenNoLaterStepFillsTheParameters() throws Exception {
        ScenarioCase scenario = closedLoopScenario(
                SEMANTIC_ACCEPT_PARTIALLY_FILLED, new ScenarioCase.ExpectFlow("accept", 2, Boolean.TRUE, 2), null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("$.expectFlow.missingParamsFilled")
                        && failure.getMessage().contains("still missing after step 2: bizScenario"),
                "the flow-level causal expectation must fail on an unfilled parameter but was: "
                        + failure.getMessage());
    }

    @Test
    void missingParamsFilledFailsWhenTheReferencedStepFoundNothingMissing() throws Exception {
        ScenarioCase scenario = closedLoopScenario(
                SEMANTIC_ACCEPT_WITH_FILLED_PARAMS, new ScenarioCase.ExpectFlow("accept", 2, Boolean.TRUE, 4), 2);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.runScenario(scenario));

        assertTrue(
                failure.getMessage().contains("$.expectFlow.missingParamsFilled")
                        && failure.getMessage().contains("no missing parameters"),
                "a referenced step without missing parameters must fail the flow expectation but was: "
                        + failure.getMessage());
    }

    // ------------------------------------------------------------------ builders

    private static ScenarioCase scenario(
            String id, List<ScenarioCase.ScenarioStep> steps, @Nullable ExpectFlow expectFlow) {
        return new ScenarioCase(
                id + "/zh-CN",
                id,
                "scenarios/information-flows.json",
                ZH_CN,
                null,
                List.of("A", "B"),
                steps,
                expectFlow);
    }

    /**
     * Builds the four-step closed loop of the private-line complaint diagnosis (Q21): the workbench generates the
     * incomplete task prompt (step 1), the OMC validates it and finds the two missing parameters (step 2), the
     * workbench accepts the information negotiation and supplies the values (step 3), and the OMC validates the accept
     * message and extracts the filled parameters (step 4).
     */
    private ScenarioCase closedLoopScenario(
            String acceptSemanticPayload, @Nullable ExpectFlow expectFlow, @Nullable Integer paramsFromStep)
            throws Exception {
        NegotiationCase taskGeneration = stepCase(
                "SC-TASK-01",
                1,
                NegotiationApi.GENERATE_TASK_PROMPT_FROM_TEXT,
                null,
                PRIVATE_LINE_COMPLAINT_URI,
                COMPLAINT_TEXT,
                script(TASK_SLOTS_OBJECT_PORTLESS),
                null,
                null,
                ok(1, null));
        NegotiationCase taskValidation = stepCase(
                "SC-TASK-01",
                2,
                NegotiationApi.VALIDATE_TASK_PROMPT_AND_DATA_FILLING,
                null,
                PRIVATE_LINE_COMPLAINT_URI,
                null,
                script(TASK_SEMANTIC_MISSING_PARAMS),
                new PromptSource.FromStep(1),
                mapper.readTree(TASK_PARAM_SCHEMA),
                okTask(1, null, List.of("accessPort", "bizScenario"), Map.of("faultTime", "2026-05-11T08:21:46Z")));
        NegotiationCase acceptGeneration = stepCase(
                "SC-TASK-01",
                3,
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "我方补充：接入端口名称P533-珠江旧城-PTN3900-23-TPA1EG24-1，投诉分类为专线质差。",
                script(ACCEPT_PAYLOAD),
                null,
                null,
                ok(1, null));
        NegotiationCase acceptValidation = stepCase(
                "SC-TASK-01",
                4,
                NegotiationApi.VALIDATE_ACCEPT_PROMPT_AND_DATA_FILLING,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                null,
                script(acceptSemanticPayload),
                new PromptSource.FromStep(3),
                schema,
                okFromStep(1, paramsFromStep));
        return new ScenarioCase(
                "SC-TASK-01/zh-CN",
                "SC-TASK-01",
                "scenarios/task-closed-loop.json",
                ZH_CN,
                null,
                List.of("A", "B"),
                List.of(
                        new ScenarioCase.ScenarioStep(1, "A", taskGeneration),
                        new ScenarioCase.ScenarioStep(2, "B", taskValidation),
                        new ScenarioCase.ScenarioStep(3, "A", acceptGeneration),
                        new ScenarioCase.ScenarioStep(4, "B", acceptValidation)),
                expectFlow,
                CLOSED_LOOP_ROLES);
    }

    private static ScenarioCase.ScenarioStep step(int number, String role, NegotiationCase caseData) {
        return new ScenarioCase.ScenarioStep(number, role, caseData);
    }

    private NegotiationCase acceptStep(String scenarioId, Expectation expect) {
        return stepCase(
                scenarioId,
                1,
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "我确认第一阶段的信息。",
                script(ACCEPT_PAYLOAD),
                null,
                null,
                expect);
    }

    private NegotiationCase acceptStep(String scenarioId, int round, Expectation expect) {
        return stepCase(
                scenarioId,
                1,
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                new ContextSpec(SESSION_ID, round, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "我确认第一阶段的信息。",
                script(ACCEPT_PAYLOAD),
                null,
                null,
                expect);
    }

    private NegotiationCase proposeStep(String scenarioId, int round, Expectation expect) {
        return stepCase(
                scenarioId,
                1,
                NegotiationApi.GENERATE_PROPOSE_FROM_TEXT,
                new ContextSpec(SESSION_ID, round, 5),
                INFORMATION_PROPOSE_URI,
                "请补充接入端口名称（如P533-珠江旧城-PTN3900-23-TPA1EG24-1）、投诉分类（如专线质差）与专线业务标识信息。",
                script(PROPOSE_PAYLOAD),
                null,
                null,
                expect);
    }

    private NegotiationCase validateAcceptStep(String scenarioId, int fromStep, Expectation expect) {
        return stepCase(
                scenarioId,
                2,
                NegotiationApi.VALIDATE_ACCEPT_PROMPT_AND_DATA_FILLING,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                null,
                script(SEMANTIC_ACCEPT_PAYLOAD),
                new PromptSource.FromStep(fromStep),
                schema,
                expect);
    }

    private NegotiationCase stepCase(
            String scenarioId,
            int stepNumber,
            NegotiationApi api,
            ContextSpec context,
            String templateUri,
            @Nullable String inputText,
            LlmScript llmScript,
            @Nullable PromptSource prompt,
            @Nullable JsonNode schema,
            Expectation expect) {
        return new NegotiationCase(
                scenarioId + "/zh-CN#step-" + stepNumber,
                scenarioId,
                "scenarios/information-flows.json",
                api,
                ZH_CN,
                null,
                List.of(),
                null,
                context,
                templateUri,
                inputText,
                null,
                llmScript,
                prompt,
                schema,
                null,
                expect);
    }

    private static LlmScript script(String payload) {
        return new LlmScript(null, List.of(new LlmScriptStep.Payload(payload)));
    }

    // ------------------------------------------------------------------ expectation helpers

    private static Expectation ok(@Nullable Integer llmCalls, @Nullable String promptTextEqualsGolden) {
        return new Expectation(
                true,
                null,
                null,
                List.of(),
                List.of(),
                llmCalls,
                promptTextEqualsGolden,
                null,
                java.util.Map.of(),
                List.of(),
                false);
    }

    private static Expectation failed(String exception, String code, @Nullable Integer llmCalls) {
        return new Expectation(
                false,
                exception,
                code,
                List.of(),
                List.of(),
                llmCalls,
                null,
                null,
                java.util.Map.of(),
                List.of(),
                false);
    }

    /** Task-family success expectation with the missing-parameter set of the task validation. */
    private static Expectation okTask(
            @Nullable Integer llmCalls,
            @Nullable List<String> promptTextContains,
            @Nullable List<String> missingParams,
            Map<String, Object> params) {
        return new Expectation(
                true,
                null,
                null,
                List.of(),
                List.of(),
                llmCalls,
                null,
                null,
                params,
                List.of(),
                false,
                promptTextContains == null ? List.of() : promptTextContains,
                missingParams,
                null);
    }

    /** Success expectation with the step-level causal reference {@code expect.paramsFromStep}. */
    private static Expectation okFromStep(@Nullable Integer llmCalls, @Nullable Integer paramsFromStep) {
        return new Expectation(
                true,
                null,
                null,
                List.of(),
                List.of(),
                llmCalls,
                null,
                null,
                java.util.Map.of(),
                List.of(),
                false,
                List.of(),
                null,
                paramsFromStep);
    }
}
