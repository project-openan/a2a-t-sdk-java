package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Meta-meta test of the corpus engine (design document §8.6, Q16): it guards against the deadliest failure mode of a
 * data-driven suite — an always-green rubber-stamp engine whose bug lets every case pass silently.
 *
 * <p>Every class of expectation assertion is proven sensitive on a minimal inline sample case: the sample runs green
 * with its true expectation, then one flipped expectation value must make the {@link CaseEngine} red, with the case id
 * and the JSON path of the flipped expectation in the failure message. The samples are built inline in Java and never
 * touch the corpus files.
 *
 * <p>The second guard, {@code engineMustNotCrashOnAnyCorpusCase}, walks the whole loaded corpus: the engine may report
 * red (an AssertionError is the engine correctly rejecting a mismatch — that is what the suites turn into test
 * failures), but it must never crash with an unexpected exception such as an NPE or a ClassCastException escaping the
 * run. A crash is an engine bug; a red assertion is the engine working.
 *
 * @since 2026-08
 */
class CorpusSensitivitySelfTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String ZH_CN = "zh-CN";

    private static final String INFORMATION_PROPOSE_URI = "Negotiation-T/information-negotiation/propose/v1";

    private static final String INFORMATION_ACCEPT_REJECT_URI =
            "Negotiation-T/information-negotiation/accept-reject/v1";

    private static final String PRIVATE_LINE_COMPLAINT_URI = "Task-T/network-layer/private-line-complaint/v1";

    /** The workbench raw complaint of the closed loop (样例步骤1): no port name and no complaint category. */
    private static final String COMPLAINT_TEXT =
            "深圳访问广州的专线从5月11号早上8点半开始响应时延从平均12ms骤升至320ms，柜面和手机银行的交易接口频繁报" + "“连接超时”。OSS侧事件流水号：event-id-20260511-09013。";

    /**
     * Slot-extraction payload of the closed loop's step 1 since the task_object slot became required upstream: the task
     * object carries what the raw text names (the circuit) but no port name, so the rendered prompt stays portless.
     */
    private static final String TASK_SLOTS_OBJECT_PORTLESS =
            "{\"slots\": {\"任务对象\": \"深圳访问广州的专线\", \"任务上下文\": \"投诉分类：待补充；问题发生时间：2026-05-11T08:21:46Z；"
                    + "OSS侧事件流水号：event-id-20260511-09013；投诉详情：深圳访问广州的响应时延从平均12ms骤升至320ms\"},"
                    + " \"slot_errors\": []}";

    /** Semantic payload of the closed loop's step 2: the port name and the complaint category are missing. */
    private static final String TASK_SEMANTIC_MISSING_PARAMS =
            "{\"semantic_verdict\":true,\"errors\":[],\"params\":{\"accessPort\":null,\"bizScenario\":null,"
                    + "\"faultTime\":\"2026-05-11T08:21:46Z\",\"eventSerialNo\":\"event-id-20260511-09013\"}}";

    /** The rendered task prompt the OMC receives (样例步骤1, shortened): the negotiation-causing message. */
    private static final String TASK_PROMPT_MISSING_PARAMS =
            """
            ## 任务类型(Task Type)
            传输专线业务投诉诊断

            ## 任务对象(Task Object)
            接入端口名称：

            ## 任务上下文(Task Context)
            1. 投诉分类：
            2. 问题发生时间： "2026-05-11T08:21:46Z"
            3. OSS侧事件流水号："event-id-20260511-09013"
            4. 投诉详情："深圳访问广州的响应时延从平均12ms骤升至320ms"
            """;

    /** The task parameter schema of the closed loop (server-side keys, dictionary §10). */
    private static final String TASK_PARAM_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"accessPort\":{\"type\":\"string\"},\"bizScenario\":"
                    + "{\"type\":\"string\"},\"faultTime\":{\"type\":\"string\"},\"eventSerialNo\":"
                    + "{\"type\":\"string\"}},\"required\":[\"accessPort\",\"bizScenario\"]}";

    /** Extraction payload mapping to the typed content of the information accept golden fixture. */
    private static final String ACCEPT_PAYLOAD =
            "{\"conclusion\":\"Accept\",\"items\":[{\"name\":\"接入端口名称\",\"value\":\"P533-珠江旧城"
                    + "-PTN3900-23-TPA1EG24-1\"},{\"name\":\"投诉分类\",\"value\":\"专线质差\"}]}";

    /** Extraction payload whose mapped content lacks every required slot (fails with negotiation.field_missing). */
    private static final String SLOTS_MISSING_PAYLOAD = "{\"relationship\":null}";

    /** Semantic verdict payload of a successful validation carrying one business parameter. */
    private static final String SEMANTIC_ACCEPT_PAYLOAD_WITH_PARAMS =
            "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":"
                    + "{\"accessPort\":\"P533-珠江旧城-PTN3900-23-TPA1EG24-1\"}}";

    /** Semantic verdict payload rejecting the message with two slot errors. */
    private static final String SEMANTIC_REJECT_PAYLOAD_TWO_ERRORS =
            "{\"semantic_verdict\":false,\"negotiation_type\":\"information\",\"errors\":[{\"slot_name\":"
                    + "\"accessPort\",\"code\":\"negotiation.field_missing\",\"facts\":{\"field\":"
                    + "\"接入端口名称\"}},{\"slot_name\":\"latency_target\",\"code\":"
                    + "\"negotiation.constraint_conflict\",\"facts\":{\"section_label\":\"时延目标\","
                    + "\"reason\":\"latency target is out of range\"}}],\"params\":{}}";

    private final CaseEngine engine = new CaseEngine();

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode schema;

    @BeforeEach
    void readSchema() throws Exception {
        schema = mapper.readTree("{\"type\":\"object\",\"properties\":{\"accessPort\":{\"type\":\"string\"}}}");
    }

    // ------------------------------------------------------------------ flipped expectations must be red

    @Test
    void flippedErrorCodeMustFailTheEngine() {
        NegotiationCase trueExpectation =
                fromTextCase("FT-SENS-CODE", SLOTS_MISSING_PAYLOAD, failed("negotiation.field_missing", 1, null, null));
        engine.run(trueExpectation);

        NegotiationCase flipped = fromTextCase(
                "FT-SENS-CODE-FLIP", SLOTS_MISSING_PAYLOAD, failed("negotiation.invalid_input", 1, null, null));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(flipped));

        assertTrue(
                failure.getMessage().contains("FT-SENS-CODE-FLIP")
                        && failure.getMessage().contains("$.expect.code"),
                "a swapped error code must fail with the case id and the JSON path but was: " + failure.getMessage());
    }

    @Test
    void flippedLlmCallCountMustFailTheEngine() {
        engine.run(fromTextCase("FT-SENS-CALLS", ACCEPT_PAYLOAD, ok(1, "information_accept")));

        AssertionError tooHigh = assertThrows(
                AssertionError.class,
                () -> engine.run(fromTextCase("FT-SENS-CALLS-HIGH", ACCEPT_PAYLOAD, ok(2, "information_accept"))));
        assertTrue(
                tooHigh.getMessage().contains("FT-SENS-CALLS-HIGH")
                        && tooHigh.getMessage().contains("$.expect.llmCalls"),
                "llmCalls + 1 must fail but was: " + tooHigh.getMessage());

        AssertionError tooLow = assertThrows(
                AssertionError.class,
                () -> engine.run(fromTextCase("FT-SENS-CALLS-LOW", ACCEPT_PAYLOAD, ok(0, "information_accept"))));
        assertTrue(
                tooLow.getMessage().contains("FT-SENS-CALLS-LOW")
                        && tooLow.getMessage().contains("$.expect.llmCalls"),
                "llmCalls - 1 must fail but was: " + tooLow.getMessage());
    }

    @Test
    void flippedGoldenNameMustFailTheEngine() {
        engine.run(fromTextCase("FT-SENS-GOLDEN", ACCEPT_PAYLOAD, ok(1, "information_accept")));

        NegotiationCase flipped = fromTextCase("FT-SENS-GOLDEN-FLIP", ACCEPT_PAYLOAD, ok(1, "information_reject"));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(flipped));

        assertTrue(
                failure.getMessage().contains("FT-SENS-GOLDEN-FLIP")
                        && failure.getMessage().contains("$.expect.promptTextEqualsGolden"),
                "a swapped golden fixture must fail but was: " + failure.getMessage());
    }

    @Test
    void tamperedParamsMustFailTheEngine() {
        engine.run(validateCase(
                "VAL-SENS-PARAMS",
                Map.of("id", SESSION_ID, "round", 2, "maxRounds", 5, "accessPort", "P533-珠江旧城-PTN3900-23-TPA1EG24-1")));

        NegotiationCase tampered = validateCase(
                "VAL-SENS-PARAMS-FLIP",
                Map.of("id", SESSION_ID, "round", 2, "maxRounds", 5, "accessPort", "P781-珠江新城-PTN7900-23-TPA1EG24-17"));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(tampered));

        assertTrue(
                failure.getMessage().contains("VAL-SENS-PARAMS-FLIP")
                        && failure.getMessage().contains("$.expect.params")
                        && failure.getMessage().contains("P781-珠江新城-PTN7900-23-TPA1EG24-17"),
                "a tampered params value must fail but was: " + failure.getMessage());
    }

    @Test
    void flippedOutcomeMustFailTheEngine() {
        NegotiationCase successReadAsFailure =
                fromTextCase("FT-SENS-OUTCOME-SF", ACCEPT_PAYLOAD, failed("negotiation.invalid_input", 1, null, null));
        AssertionError successFlipped = assertThrows(AssertionError.class, () -> engine.run(successReadAsFailure));
        assertTrue(
                successFlipped.getMessage().contains("FT-SENS-OUTCOME-SF")
                        && successFlipped.getMessage().contains("$.expect.outcome"),
                "a success run expected to fail must be red but was: " + successFlipped.getMessage());

        NegotiationCase failureReadAsSuccess = fromTextCase("FT-SENS-OUTCOME-FS", null, ok(1, "information_accept"));
        AssertionError failureFlipped = assertThrows(AssertionError.class, () -> engine.run(failureReadAsSuccess));
        assertTrue(
                failureFlipped.getMessage().contains("FT-SENS-OUTCOME-FS")
                        && failureFlipped.getMessage().contains("$.expect.outcome"),
                "a failing run expected to succeed must be red but was: " + failureFlipped.getMessage());
    }

    @Test
    void flippedSlotErrorsMustFailTheEngine() {
        List<Expectation.SlotError> both = List.of(
                new Expectation.SlotError("accessPort", "negotiation.field_missing"),
                new Expectation.SlotError("latency_target", "negotiation.constraint_conflict"));
        engine.run(validateCase("VAL-SENS-SLOTS", null, both));

        NegotiationCase removed = validateCase(
                "VAL-SENS-SLOTS-DEL",
                null,
                List.of(new Expectation.SlotError("accessPort", "negotiation.field_missing")));
        AssertionError removalFailure = assertThrows(AssertionError.class, () -> engine.run(removed));
        assertTrue(
                removalFailure.getMessage().contains("VAL-SENS-SLOTS-DEL")
                        && removalFailure.getMessage().contains("$.expect.slotErrors"),
                "a removed slot error must fail but was: " + removalFailure.getMessage());

        List<Expectation.SlotError> three = List.of(
                new Expectation.SlotError("accessPort", "negotiation.field_missing"),
                new Expectation.SlotError("latency_target", "negotiation.constraint_conflict"),
                new Expectation.SlotError("round", "negotiation.round_exceeded"));
        NegotiationCase added = validateCase("VAL-SENS-SLOTS-ADD", null, three);
        AssertionError additionFailure = assertThrows(AssertionError.class, () -> engine.run(added));
        assertTrue(
                additionFailure.getMessage().contains("VAL-SENS-SLOTS-ADD")
                        && additionFailure.getMessage().contains("$.expect.slotErrors"),
                "an added slot error must fail but was: " + additionFailure.getMessage());
    }

    // ------------------------------------------------------------------ task-family expectations must be sensitive

    @Test
    void flippedMissingParamsMustFailTheEngine() throws Exception {
        NegotiationCase trueExpectation = taskValidateCase(
                "VAL-SENS-MISSING",
                taskOk(1, null, List.of("accessPort", "bizScenario"), Map.of("faultTime", "2026-05-11T08:21:46Z")),
                TASK_SEMANTIC_MISSING_PARAMS);
        engine.run(trueExpectation);

        NegotiationCase flipped = taskValidateCase(
                "VAL-SENS-MISSING-FLIP",
                taskOk(1, null, List.of("accessPort"), Map.of("faultTime", "2026-05-11T08:21:46Z")),
                TASK_SEMANTIC_MISSING_PARAMS);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(flipped));

        assertTrue(
                failure.getMessage().contains("VAL-SENS-MISSING-FLIP")
                        && failure.getMessage().contains("$.expect.missingParams")
                        && failure.getMessage().contains("bizScenario"),
                "a shrunken missing-parameter set must fail but was: " + failure.getMessage());
    }

    @Test
    void flippedPromptTextContainsMustFailTheEngine() throws Exception {
        NegotiationCase trueExpectation =
                taskFromTextCase("VAL-SENS-HEADERS", taskOk(1, List.of("## 任务类型"), null, Map.of()));
        engine.run(trueExpectation);

        NegotiationCase flipped =
                taskFromTextCase("VAL-SENS-HEADERS-FLIP", taskOk(1, List.of("## 所需信息项"), null, Map.of()));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(flipped));

        assertTrue(
                failure.getMessage().contains("VAL-SENS-HEADERS-FLIP")
                        && failure.getMessage().contains("$.expect.promptTextContains"),
                "a wrong structural fragment must fail but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ engine crash guard

    @Test
    void engineMustNotCrashOnAnyCorpusCase() {
        LoadedCorpus corpus = CorpusSuites.loadCorpus();
        CaseEngine caseEngine = new CaseEngine();
        for (NegotiationCase testCase : corpus.cases()) {
            try {
                caseEngine.run(testCase);
            } catch (AssertionError redIsTheEngineWorking) {
                // A red assertion is the engine rejecting a mismatch; the suites turn it into a test failure.
            } catch (Throwable crash) {
                fail("The case engine crashed on " + testCase.errorPrefix() + ": " + crash);
            }
        }
        ScenarioEngine scenarioEngine = new ScenarioEngine();
        for (ScenarioCase scenario : corpus.scenarios()) {
            try {
                scenarioEngine.runScenario(scenario);
            } catch (AssertionError redIsTheEngineWorking) {
                // See above: red is not a crash.
            } catch (Throwable crash) {
                fail("The scenario engine crashed on " + scenario.id() + ": " + crash);
            }
        }
    }

    // ------------------------------------------------------------------ inline sample cases

    private NegotiationCase fromTextCase(String id, @Nullable String payload, Expectation expect) {
        LlmScript script = new LlmScript(
                null,
                List.of(
                        payload == null
                                ? new LlmScriptStep.Fail(LlmFailMarker.NON_JSON)
                                : new LlmScriptStep.Payload(payload)));
        return new NegotiationCase(
                id + "/zh-CN",
                id,
                "from-text/sensitivity-probes.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                null,
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                script,
                null,
                null,
                null,
                expect);
    }

    private NegotiationCase validateCase(String id, @Nullable Map<String, Object> params) {
        return validateCase(id, params, null);
    }

    private NegotiationCase validateCase(
            String id, @Nullable Map<String, Object> params, @Nullable List<Expectation.SlotError> slotErrors) {
        Expectation expect;
        if (params == null) {
            expect = new Expectation(
                    false,
                    null,
                    "negotiation.semantic_rejected",
                    List.of(),
                    slotErrors == null ? List.of() : slotErrors,
                    1,
                    null,
                    null,
                    Map.of(),
                    List.of(),
                    false);
        } else {
            expect = new Expectation(true, null, null, List.of(), List.of(), 1, null, null, params, List.of(), false);
        }
        String payload = params == null ? SEMANTIC_REJECT_PAYLOAD_TWO_ERRORS : SEMANTIC_ACCEPT_PAYLOAD_WITH_PARAMS;
        return new NegotiationCase(
                id + "/zh-CN",
                id,
                "validate/sensitivity-probes.json",
                NegotiationApi.VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING,
                ZH_CN,
                null,
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_PROPOSE_URI,
                null,
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(payload))),
                new PromptSource.Golden("information_propose"),
                schema,
                null,
                expect);
    }

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
                Map.of(),
                List.of(),
                false);
    }

    /** Task-family success expectation with the prompt-text fragments and the missing-parameter set. */
    private static Expectation taskOk(
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

    private NegotiationCase taskFromTextCase(String id, Expectation expect) {
        return new NegotiationCase(
                id + "/zh-CN",
                id,
                "task/sensitivity-probes.json",
                NegotiationApi.GENERATE_TASK_PROMPT_FROM_TEXT,
                ZH_CN,
                null,
                List.of(),
                null,
                null,
                PRIVATE_LINE_COMPLAINT_URI,
                COMPLAINT_TEXT,
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(TASK_SLOTS_OBJECT_PORTLESS))),
                null,
                null,
                null,
                expect);
    }

    private NegotiationCase taskValidateCase(String id, Expectation expect, String semanticPayload) throws Exception {
        return new NegotiationCase(
                id + "/zh-CN",
                id,
                "task/sensitivity-probes.json",
                NegotiationApi.VALIDATE_TASK_PROMPT_AND_DATA_FILLING,
                ZH_CN,
                null,
                List.of(),
                null,
                null,
                PRIVATE_LINE_COMPLAINT_URI,
                null,
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(semanticPayload))),
                new PromptSource.Text(TASK_PROMPT_MISSING_PARAMS),
                new ObjectMapper().readTree(TASK_PARAM_SCHEMA),
                null,
                expect);
    }

    private static Expectation failed(
            @Nullable String code,
            @Nullable Integer llmCalls,
            @Nullable Map<String, Object> params,
            @Nullable List<Expectation.SlotError> slotErrors) {
        return new Expectation(
                false,
                null,
                code,
                List.of(),
                slotErrors == null ? List.of() : slotErrors,
                llmCalls,
                null,
                null,
                params == null ? Map.of() : params,
                List.of(),
                false);
    }
}
