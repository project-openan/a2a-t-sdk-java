package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.corpus.Expectation.Metadata;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests of the {@link CaseEngine} against inline case objects: the four P0 contracts, the Q17 C+
 * differential double run, the inject hook, the fail-on-overconsumption calibration through {@code llmCalls}, and the
 * red paths proving the engine is not a rubber stamp (every flipped expectation fails with the case id and the JSON
 * path of the expectation).
 *
 * <p>The engine runs against the real production wiring (builders, classpath resources, renderers, vocabulary, rule
 * gate, semantic validator); the golden comparisons use the committed golden fixtures of the test resources.
 */
class CaseEngineTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String ZH_CN = "zh-CN";

    private static final String INFORMATION_PROPOSE_URI = "Negotiation-T/information-negotiation/propose/v1";

    private static final String INFORMATION_ACCEPT_REJECT_URI =
            "Negotiation-T/information-negotiation/accept-reject/v1";

    private static final String PRIVATE_LINE_COMPLAINT_URI = "Task-T/network-layer/private-line-complaint/v1";

    /**
     * The workbench raw complaint of the five-step closed loop (样例步骤1): deliberately lacking the access port name and
     * the complaint category — the causal starting point of the information negotiation.
     */
    private static final String COMPLAINT_TEXT = "深圳访问广州的专线从5月11号早上8点半开始响应时延从平均12ms骤升至320ms，访问广州机房的核心交易系统非常慢，"
            + "柜面和手机银行的交易接口频繁报“连接超时”。OSS侧事件流水号：event-id-20260511-09013。";

    /** Slot-extraction payload of the closed loop's step 1: the task object stays empty (no port name in the text). */
    private static final String TASK_SLOTS_OBJECT_EMPTY =
            "{\"slots\": {\"任务对象\": \"\", \"任务上下文\": \"投诉分类：待补充；问题发生时间：2026-05-11T08:21:46Z；"
                    + "OSS侧事件流水号：event-id-20260511-09013；投诉详情：深圳访问广州的响应时延从平均12ms骤升至320ms\"},"
                    + " \"slot_errors\": []}";

    /**
     * Slot-extraction payload of the from-text success path since the task_object slot became required upstream: the
     * task object carries what the raw text names (the circuit) but no port name, so the rendered prompt stays
     * portless.
     */
    private static final String TASK_SLOTS_OBJECT_PORTLESS =
            "{\"slots\": {\"任务对象\": \"深圳访问广州的专线\", \"任务上下文\": \"投诉分类：待补充；问题发生时间：2026-05-11T08:21:46Z；"
                    + "OSS侧事件流水号：event-id-20260511-09013；投诉详情：深圳访问广州的响应时延从平均12ms骤升至320ms\"},"
                    + " \"slot_errors\": []}";

    /** Slot-extraction payload of the filled variant: the port name and the complaint category are present. */
    private static final String TASK_SLOTS_FILLED =
            "{\"slots\": {\"任务对象\": \"接入端口名称：P533-珠江旧城-PTN3900-23-TPA1EG24-1\", \"任务上下文\":"
                    + " \"投诉分类：专线质差；问题发生时间：2026-05-11T08:21:46Z；OSS侧事件流水号："
                    + "event-id-20260511-09013；投诉详情：深圳访问广州的响应时延从平均12ms骤升至320ms\"},"
                    + " \"slot_errors\": []}";

    /**
     * Semantic-validation payload of the closed loop's step 2: the prompt is acceptable, but the access port name and
     * the complaint category are missing — the two null-valued parameters the OMC then negotiates for.
     */
    private static final String TASK_SEMANTIC_MISSING_PARAMS =
            "{\"semantic_verdict\":true,\"errors\":[],\"params\":{\"accessPort\":null,\"bizScenario\":null,"
                    + "\"faultTime\":\"2026-05-11T08:21:46Z\",\"eventSerialNo\":\"event-id-20260511-09013\"}}";

    /** Semantic payload rejecting the task prompt (rejection-sample shape: key slots missing). */
    private static final String TASK_SEMANTIC_REJECTED =
            "{\"semantic_verdict\":false,\"errors\":[{\"slot_name\":\"accessPort\",\"code\":\"content.param_missing\","
                    + "\"facts\":{\"section_label\":\"接入端口名称\"}}],\"params\":{}}";

    /** The task parameter schema of the closed loop (server-side keys, dictionary §10). */
    private static final String TASK_PARAM_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"accessPort\":{\"type\":\"string\"},\"bizScenario\":"
                    + "{\"type\":\"string\"},\"faultTime\":{\"type\":\"string\"},\"eventSerialNo\":"
                    + "{\"type\":\"string\"}},\"required\":[\"accessPort\",\"bizScenario\"]}";

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

    /** Extraction payload that maps back to the typed content of the information accept golden fixture. */
    private static final String ACCEPT_PAYLOAD =
            "{\"conclusion\":\"Accept\",\"items\":[{\"name\":\"接入端口名称\",\"value\":\"P533-珠江旧城"
                    + "-PTN3900-23-TPA1EG24-1\"},{\"name\":\"投诉分类\",\"value\":\"专线质差\"}]}";

    /** Extraction payload that maps back to the typed content of the information propose golden fixture. */
    private static final String PROPOSE_PAYLOAD =
            "{\"items\":[{\"name\":\"接入端口名称\",\"value\":\"举例：P533-珠江旧城-PTN3900-23-TPA1EG24-1\"},"
                    + "{\"name\":\"投诉分类\",\"value\":\"举例：专线质差\"},{\"name\":\"专线业务标识\","
                    + "\"value\":null}],\"relationship\":\"OR\"}";

    /** The same content as typed data, disagreeing in one item value for the differential red path. */
    private static final String PROPOSE_DATA_DISAGREEING =
            "{\"items\":[{\"name\":\"接入端口名称\",\"value\":\"举例：P781-珠江新城-PTN7900-23-TPA1EG24-17\"},"
                    + "{\"name\":\"投诉分类\",\"value\":\"举例：专线质差\"},{\"name\":\"专线业务标识\","
                    + "\"value\":null}],\"relationship\":\"OR\"}";

    private static final String SEMANTIC_ACCEPT_PAYLOAD_WITH_PARAMS =
            "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":"
                    + "{\"accessPort\":\"P533-珠江旧城-PTN3900-23-TPA1EG24-1\"}}";

    private static final String SEMANTIC_REJECT_PAYLOAD =
            "{\"semantic_verdict\":false,\"negotiation_type\":\"information\",\"errors\":[{\"slot_name\":"
                    + "\"accessPort\",\"code\":\"negotiation.field_missing\",\"facts\":{\"field\":"
                    + "\"接入端口名称\"}}],\"params\":{}}";

    private final CaseEngine engine = new CaseEngine();

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode schema;

    @BeforeEach
    void readSchema() throws Exception {
        schema = mapper.readTree("{\"type\":\"object\",\"properties\":{\"accessPort\":{\"type\":\"string\"}}}");
    }

    // ------------------------------------------------------------------ green paths

    @Test
    void runsASuccessfulFromTextCaseAgainstTheGoldenFixture() {
        NegotiationCase testCase = acceptFromTextCase(ok(1, "information_accept", metadata(), false), null);

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        MetadataContent message = outcome.message();
        assertNotNull(message);
        assertEquals(1, outcome.llmCalls());
        assertEquals(INFORMATION_ACCEPT_REJECT_URI, message.templateUri());
        assertEquals(
                new NegotiationContext(SESSION_ID, 2, 5, NegotiationPerformative.ACCEPT), message.negotiationContext());
    }

    @Test
    void runsFromDataCasesDeterministicallyWithoutAnyLlmCall() throws Exception {
        NegotiationCase testCase = new NegotiationCase(
                "FD-HAPPY-01/zh-CN",
                "FD-HAPPY-01",
                "from-data/happy.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_DATA,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                null,
                mapper.readTree(ACCEPT_PAYLOAD),
                null,
                null,
                null,
                null,
                ok(0, "information_accept", metadata(), false));

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        assertNotNull(outcome.message());
        assertEquals(0, outcome.llmCalls(), "the from-data run must not call the LLM (assertion-only client)");
    }

    @Test
    void injectsTheFailingTemplateLoaderForTheTemplateNotFoundMatrix() {
        NegotiationCase testCase = new NegotiationCase(
                "FT-TPL-01/zh-CN",
                "FT-TPL-01",
                "from-text/template-resolution.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(ACCEPT_PAYLOAD))),
                null,
                null,
                "failingTemplateLoader",
                failed("NegotiationGenerationException", "template.not_found", 0, List.of(), List.of()));

        engine.run(testCase);
    }

    @Test
    void injectsTheFailingSemanticValidatorForTheValidateTemplateNotFoundMapping() {
        NegotiationCase testCase = new NegotiationCase(
                "VAL-MAP-05/zh-CN",
                "VAL-MAP-05",
                "validate/error-code-mapping.json",
                NegotiationApi.VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_PROPOSE_URI,
                null,
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Fail(LlmFailMarker.ASSERTION))),
                new PromptSource.Golden("information_propose"),
                schema,
                "failingSemanticValidator",
                failed("NegotiationParamExtractionException", "template.not_found", 0, List.of(), List.of()));

        engine.run(testCase);
    }

    // ------------------------------------------------------------------ P0 contracts

    @Test
    void assertConclusionLiteralPresentContract() {
        NegotiationCase testCase =
                acceptFromTextCase(okContracts(1, "information_accept", List.of("conclusionLiteralPresent")), null);

        engine.run(testCase);
    }

    @Test
    void conclusionLiteralPresentContractRejectsNonTerminalApis() {
        NegotiationCase testCase =
                proposeFromTextCase(okContracts(1, "information_propose", List.of("conclusionLiteralPresent")), null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("conclusionLiteralPresent")
                        && failure.getMessage().contains("a terminal or abort generation API"),
                "the contract must reject a propose API but was: " + failure.getMessage());
    }

    @Test
    void assertContextKeysInMergedParamsContract() {
        NegotiationCase testCase = validateProposeCase(
                okParams(
                        1,
                        Map.of(
                                "id",
                                SESSION_ID,
                                "round",
                                2,
                                "maxRounds",
                                5,
                                "accessPort",
                                "P533-珠江旧城-PTN3900-23-TPA1EG24-1"),
                        List.of("contextKeysInMergedParams")),
                SEMANTIC_ACCEPT_PAYLOAD_WITH_PARAMS);

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        FilledParamData filled = outcome.filledParams();
        assertNotNull(filled);
        assertEquals("P533-珠江旧城-PTN3900-23-TPA1EG24-1", filled.data().get("accessPort"));
    }

    @Test
    void assertNoLlmLeakInUserMessageContract() {
        NegotiationCase testCase = new NegotiationCase(
                "FT-RETRY-01/zh-CN",
                "FT-RETRY-01",
                "from-text/retry.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                new LlmScript(
                        null,
                        List.of(
                                new LlmScriptStep.Fail(LlmFailMarker.NON_JSON),
                                new LlmScriptStep.Fail(LlmFailMarker.NON_JSON),
                                new LlmScriptStep.Fail(LlmFailMarker.NON_JSON))),
                null,
                null,
                null,
                failed(
                        "NegotiationGenerationException",
                        "llm.response_invalid",
                        3,
                        List.of(),
                        List.of("noLlmLeakInUserMessage")));

        engine.run(testCase);
    }

    @Test
    void assertMetadataTripleShapeContract() {
        NegotiationCase testCase =
                acceptFromTextCase(okContracts(1, "information_accept", List.of("metadataTripleShape")), null);

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        assertEquals(
                3,
                outcome.message().buildMetadataContent().size(),
                "the contract must have verified the triple metadata shape");
    }

    @Test
    void rejectsUnknownAndNotYetLitContractNames() {
        NegotiationCase unknown =
                acceptFromTextCase(okContracts(1, "information_accept", List.of("not-a-contract")), null);
        AssertionError unknownFailure = assertThrows(AssertionError.class, () -> engine.run(unknown));
        assertTrue(
                unknownFailure.getMessage().contains("a registered contract name"),
                "an unknown contract name must fail but was: " + unknownFailure.getMessage());

        NegotiationCase notYetLit =
                acceptFromTextCase(okContracts(1, "information_accept", List.of("noRenderSlotLeak")), null);
        AssertionError notYetLitFailure = assertThrows(AssertionError.class, () -> engine.run(notYetLit));
        assertTrue(
                notYetLitFailure.getMessage().contains("not yet lit"),
                "a registered P1 contract must fail as not yet lit but was: " + notYetLitFailure.getMessage());
    }

    // ------------------------------------------------------------------ differential (Q17 C+)

    @Test
    void runsTheDifferentialDoubleRun() throws Exception {
        NegotiationCase testCase = proposeFromTextCase(
                ok(1, "information_propose", new Expectation.Metadata(INFORMATION_PROPOSE_URI, Boolean.TRUE), true),
                mapper.readTree(PROPOSE_PAYLOAD));

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        assertNotNull(outcome.message());
        assertEquals(1, outcome.llmCalls(), "only the from-text leg may call the LLM");
    }

    @Test
    void differentialFailsWhenTheTypedDataDisagreesWithTheText() throws Exception {
        NegotiationCase testCase = proposeFromTextCase(
                ok(1, "information_propose", new Expectation.Metadata(INFORMATION_PROPOSE_URI, Boolean.TRUE), true),
                mapper.readTree(PROPOSE_DATA_DISAGREEING));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.differential")
                        && failure.getMessage().contains("fromText == fromData"),
                "the differential must compare both legs but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ validate expectations

    @Test
    void comparesSlotErrorsOrderInsensitively() {
        NegotiationCase testCase = validateProposeCase(
                failed(
                        "NegotiationParamExtractionException",
                        "negotiation.semantic_rejected",
                        1,
                        List.of(new Expectation.SlotError("accessPort", "negotiation.field_missing")),
                        List.of()),
                SEMANTIC_REJECT_PAYLOAD);

        engine.run(testCase);
    }

    @Test
    void slotErrorsFailOnAnExtraExpectedPair() {
        NegotiationCase testCase = validateProposeCase(
                failed(
                        "NegotiationParamExtractionException",
                        "negotiation.semantic_rejected",
                        1,
                        List.of(
                                new Expectation.SlotError("accessPort", "negotiation.field_missing"),
                                new Expectation.SlotError("round", "negotiation.round_exceeded")),
                        List.of()),
                SEMANTIC_REJECT_PAYLOAD);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.slotErrors"),
                "an extra expected slot error must fail but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ red paths (not a rubber stamp)

    @Test
    void failsWithCaseIdAndJsonPathWhenTheExpectedCodeMismatches() {
        NegotiationCase testCase = new NegotiationCase(
                "FT-EXTRACT-01/zh-CN",
                "FT-EXTRACT-01",
                "from-text/extraction-failures.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload("{\"relationship\":null}"))),
                null,
                null,
                null,
                failed(null, "negotiation.invalid_input", 1, List.of(), List.of()));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("FT-EXTRACT-01/zh-CN")
                        && failure.getMessage().contains("$.expect.code")
                        && failure.getMessage().contains("negotiation.field_missing"),
                "the failure must carry the case id, the JSON path and both codes but was: " + failure.getMessage());
    }

    @Test
    void failsWithCaseIdAndJsonPathWhenTheLlmCallCountMismatches() {
        NegotiationCase testCase = acceptFromTextCase(ok(2, "information_accept", null, false), null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.llmCalls")
                        && failure.getMessage().contains("2"),
                "a wrong llmCalls expectation must fail but was: " + failure.getMessage());
    }

    @Test
    void failsWithCaseIdAndJsonPathWhenTheGoldenNameMismatches() {
        NegotiationCase testCase = acceptFromTextCase(ok(1, "information_reject", null, false), null);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.promptTextEqualsGolden"),
                "a wrong golden name must fail but was: " + failure.getMessage());
    }

    @Test
    void failsWhenASuccessCaseActuallyFails() {
        NegotiationCase testCase = new NegotiationCase(
                "FT-HAPPY-99/zh-CN",
                "FT-HAPPY-99",
                "from-text/happy.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "请接受。",
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Fail(LlmFailMarker.NON_JSON))),
                null,
                null,
                null,
                ok(1, "information_accept", null, false));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.outcome")
                        && failure.getMessage().contains("failure"),
                "an unexpected failure must flip the outcome expectation but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ task API dispatch family (Q21)

    @Test
    void runsATaskFromTextCaseThroughTheMirroredClientWiring() {
        NegotiationCase testCase = taskFromTextCase(
                okTask(1, List.of("## 任务类型", "## 任务对象", "## 任务上下文", "event-id-20260511-09013"), null, Map.of()));

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        MetadataContent message = outcome.message();
        assertNotNull(message);
        assertEquals(1, outcome.llmCalls(), "the task from-text pipeline makes exactly one slot-extraction call");
        assertEquals(PRIVATE_LINE_COMPLAINT_URI, message.templateUri());
        assertTrue(
                message.promptText().contains("## 任务对象(Task Object)"),
                "the task object section header must be rendered: " + message.promptText());
        assertTrue(!message.promptText().contains("P533"), "no port name may leak into the incomplete task prompt");
    }

    @Test
    void runsATaskFromDataWithSchemaCaseThroughTheMirroredClientWiring() throws Exception {
        NegotiationCase testCase = taskFromDataCase(
                okTask(1, List.of("## 任务类型", "P533-珠江旧城-PTN3900-23-TPA1EG24-1", "专线质差"), null, Map.of()));

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        MetadataContent message = outcome.message();
        assertNotNull(message);
        assertEquals(1, outcome.llmCalls(), "the from-data-with-schema pipeline also extracts slots through the LLM");
        assertEquals(PRIVATE_LINE_COMPLAINT_URI, message.templateUri());
    }

    @Test
    void validateTaskPromptReportsTheMissingParametersAsNullValuedParams() throws Exception {
        NegotiationCase testCase = taskValidateCase(
                okTask(
                        1,
                        null,
                        List.of("accessPort", "bizScenario"),
                        Map.of(
                                "faultTime", "2026-05-11T08:21:46Z",
                                "eventSerialNo", "event-id-20260511-09013")),
                TASK_SEMANTIC_MISSING_PARAMS);

        CaseEngine.CaseOutcome outcome = engine.run(testCase);

        FilledParamData filled = outcome.filledParams();
        assertNotNull(filled);
        assertEquals(1, outcome.llmCalls(), "the task validation pipeline makes exactly one semantic call");
        assertEquals("2026-05-11T08:21:46Z", filled.data().get("faultTime"));
        assertEquals("event-id-20260511-09013", filled.data().get("eventSerialNo"));
        assertTrue(
                filled.data().containsKey("accessPort") && filled.data().get("accessPort") == null,
                "a missing parameter must surface as a null-valued entry, not as an absent key");
    }

    @Test
    void taskValidateMissingParamsFlipFails() throws Exception {
        NegotiationCase testCase = taskValidateCase(
                okTask(1, null, List.of("accessPort", "bizScenario", "faultDetail"), Map.of()),
                TASK_SEMANTIC_MISSING_PARAMS);

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.missingParams")
                        && failure.getMessage().contains("faultDetail"),
                "an extra expected missing parameter must fail but was: " + failure.getMessage());
    }

    @Test
    void taskValidateSemanticRejectionCarriesTheValidationCode() throws Exception {
        NegotiationCase testCase = taskValidateCase(
                failed("ContentValidationException", "negotiation.semantic_rejected", 1, List.of(), List.of()),
                TASK_SEMANTIC_REJECTED);

        engine.run(testCase);
    }

    @Test
    void taskFromTextFailsOnAMissingRequiredSlot() throws Exception {
        NegotiationCase testCase = new NegotiationCase(
                "TASK-ERR-01/zh-CN",
                "TASK-ERR-01",
                "task/programming-errors.json",
                NegotiationApi.GENERATE_TASK_PROMPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                null,
                PRIVATE_LINE_COMPLAINT_URI,
                COMPLAINT_TEXT,
                null,
                new LlmScript(
                        null,
                        List.of(new LlmScriptStep.Payload(
                                "{\"slots\": {\"任务对象\": \"\", \"任务上下文\": \"\"}, \"slot_errors\": []}"))),
                null,
                null,
                null,
                failed("PromptGenerationException", "slot.not_provided", 1, List.of(), List.of()));

        engine.run(testCase);
    }

    @Test
    void taskPromptTextContainsFailsOnAnAbsentFragment() {
        NegotiationCase testCase = taskFromTextCase(okTask(1, List.of("## 不存在的节头"), null, Map.of()));

        AssertionError failure = assertThrows(AssertionError.class, () -> engine.run(testCase));

        assertTrue(
                failure.getMessage().contains("$.expect.promptTextContains")
                        && failure.getMessage().contains("不存在的节头"),
                "an absent structural fragment must fail but was: " + failure.getMessage());
    }

    @Test
    void paramsFromStepIsReservedForTheScenarioEngine() throws Exception {
        NegotiationCase testCase = taskValidateCase(okTask(1, null, null, Map.of()), TASK_SEMANTIC_MISSING_PARAMS);
        NegotiationCase withFromStep = new NegotiationCase(
                testCase.id(),
                testCase.baseId(),
                testCase.sourceFile(),
                testCase.api(),
                testCase.language(),
                testCase.priority(),
                testCase.tags(),
                testCase.summary(),
                testCase.context(),
                testCase.templateUri(),
                testCase.inputText(),
                testCase.inputData(),
                testCase.llm(),
                testCase.prompt(),
                testCase.schema(),
                testCase.inject(),
                new Expectation(
                        testCase.expect().success(),
                        testCase.expect().exception(),
                        testCase.expect().code(),
                        testCase.expect().messageContains(),
                        testCase.expect().slotErrors(),
                        testCase.expect().llmCalls(),
                        testCase.expect().promptTextEqualsGolden(),
                        testCase.expect().metadata(),
                        testCase.expect().params(),
                        testCase.expect().contracts(),
                        testCase.expect().differential(),
                        testCase.expect().promptTextContains(),
                        testCase.expect().missingParams(),
                        1));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> engine.run(withFromStep));

        assertTrue(
                failure.getMessage().contains("paramsFromStep 1")
                        && failure.getMessage().contains("ScenarioEngine"),
                "a standalone case cannot resolve paramsFromStep but was: " + failure.getMessage());
    }

    // ------------------------------------------------------------------ expectation helpers

    private static Expectation ok(
            @Nullable Integer llmCalls,
            @Nullable String promptTextEqualsGolden,
            @Nullable Metadata metadata,
            boolean differential) {
        return new Expectation(
                true,
                null,
                null,
                List.of(),
                List.of(),
                llmCalls,
                promptTextEqualsGolden,
                metadata,
                Map.of(),
                List.of(),
                differential);
    }

    private static Expectation okContracts(
            @Nullable Integer llmCalls, @Nullable String promptTextEqualsGolden, List<String> contracts) {
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
                contracts,
                false);
    }

    private static Expectation okParams(
            @Nullable Integer llmCalls, Map<String, Object> params, List<String> contracts) {
        return new Expectation(true, null, null, List.of(), List.of(), llmCalls, null, null, params, contracts, false);
    }

    private static Expectation failed(
            @Nullable String exception,
            @Nullable String code,
            @Nullable Integer llmCalls,
            List<Expectation.SlotError> slotErrors,
            List<String> contracts) {
        return new Expectation(
                false, exception, code, List.of(), slotErrors, llmCalls, null, null, Map.of(), contracts, false);
    }

    // ------------------------------------------------------------------ case builders

    private NegotiationCase acceptFromTextCase(Expectation expect, @Nullable JsonNode inputData) {
        return new NegotiationCase(
                "FT-HAPPY-01/zh-CN",
                "FT-HAPPY-01",
                "from-text/happy.json",
                NegotiationApi.GENERATE_ACCEPT_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_ACCEPT_REJECT_URI,
                "我确认第一阶段的信息。",
                inputData,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(ACCEPT_PAYLOAD))),
                null,
                null,
                null,
                expect);
    }

    private NegotiationCase proposeFromTextCase(Expectation expect, @Nullable JsonNode inputData) {
        return new NegotiationCase(
                "FT-HAPPY-02/zh-CN",
                "FT-HAPPY-02",
                "from-text/happy.json",
                NegotiationApi.GENERATE_PROPOSE_FROM_TEXT,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_PROPOSE_URI,
                "请补充接入端口名称（如P533-珠江旧城-PTN3900-23-TPA1EG24-1）、投诉分类（如专线质差）与专线业务标识信息。",
                inputData,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(PROPOSE_PAYLOAD))),
                null,
                null,
                null,
                expect);
    }

    private NegotiationCase validateProposeCase(Expectation expect, String semanticPayload) {
        return new NegotiationCase(
                "VAL-HAPPY-01/zh-CN",
                "VAL-HAPPY-01",
                "validate/happy.json",
                NegotiationApi.VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING,
                ZH_CN,
                "P0",
                List.of(),
                null,
                new ContextSpec(SESSION_ID, 2, 5),
                INFORMATION_PROPOSE_URI,
                null,
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(semanticPayload))),
                new PromptSource.Golden("information_propose"),
                schema,
                null,
                expect);
    }

    private static Expectation.Metadata metadata() {
        return new Expectation.Metadata(INFORMATION_ACCEPT_REJECT_URI, Boolean.TRUE);
    }

    // ------------------------------------------------------------------ task family helpers

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

    private NegotiationCase taskFromTextCase(Expectation expect) {
        return new NegotiationCase(
                "TASK-FT-01/zh-CN",
                "TASK-FT-01",
                "task/from-text.json",
                NegotiationApi.GENERATE_TASK_PROMPT_FROM_TEXT,
                ZH_CN,
                "P0",
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

    private NegotiationCase taskFromDataCase(Expectation expect) throws Exception {
        return new NegotiationCase(
                "TASK-FD-01/zh-CN",
                "TASK-FD-01",
                "task/from-data.json",
                NegotiationApi.GENERATE_TASK_PROMPT_FROM_DATA_WITH_SCHEMA,
                ZH_CN,
                "P0",
                List.of(),
                null,
                null,
                PRIVATE_LINE_COMPLAINT_URI,
                null,
                mapper.readTree("{\"portName\": \"P533-珠江旧城-PTN3900-23-TPA1EG24-1\", \"complaintScenario\": \"专线质差\","
                        + " \"faultStartTime\": \"2026-05-11T08:21:46Z\", \"ticketNo\":"
                        + " \"event-id-20260511-09013\", \"faultDetailText\": \"深圳访问广州的响应时延从平均12ms"
                        + "骤升至320ms\"}"),
                new LlmScript(null, List.of(new LlmScriptStep.Payload(TASK_SLOTS_FILLED))),
                null,
                mapper.readTree("{\"type\": \"object\", \"properties\": {\"portName\": {\"type\": \"string\"},"
                        + " \"complaintScenario\": {\"type\": \"string\"}, \"faultStartTime\":"
                        + " {\"type\": \"string\"}, \"ticketNo\": {\"type\": \"string\"},"
                        + " \"faultDetailText\": {\"type\": \"string\"}}}"),
                null,
                expect);
    }

    private NegotiationCase taskValidateCase(Expectation expect, String semanticPayload) throws Exception {
        return new NegotiationCase(
                "TASK-VAL-01/zh-CN",
                "TASK-VAL-01",
                "task/validate.json",
                NegotiationApi.VALIDATE_TASK_PROMPT_AND_DATA_FILLING,
                ZH_CN,
                "P0",
                List.of(),
                null,
                null,
                PRIVATE_LINE_COMPLAINT_URI,
                null,
                null,
                new LlmScript(null, List.of(new LlmScriptStep.Payload(semanticPayload))),
                new PromptSource.Text(TASK_PROMPT_MISSING_PARAMS),
                mapper.readTree(TASK_PARAM_SCHEMA),
                null,
                expect);
    }
}
