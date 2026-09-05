package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Offline unit tests of the {@link LiveCaseEngine} judging logic: a step-playing stub client drives the real production
 * assembly exactly the way a live endpoint would (the scripted responses reuse the response shapes the offline task
 * scenarios script), so the expectation assertions, the infra-only retry and the transcript verdicts are verified
 * without any endpoint.
 *
 * @since 2026-08
 */
class LiveCaseEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Slot-extraction response of the generate step, the shape the offline task scenarios script. */
    private static final String SLOT_EXTRACTION_RESPONSE =
            """
            {"slots":{"任务对象":"P533-珠江旧城-PTN3900-23-TPA1EG24-1",\
            "任务上下文":"投诉分类：专线质差；问题发生时间：2026-05-11T08:21:46Z；\
            OSS侧事件流水号：event-id-20260511-09013；投诉详情：深圳访问广州的专线时延从平均12ms骤升至320ms"},\
            "slot_errors":[]}""";

    /** Validate-step response of a semantically compliant prompt with every parameter filled. */
    private static final String VALIDATE_PASS_RESPONSE =
            """
            {"semantic_verdict":true,"errors":[],"params":{"accessPort":"P533-珠江旧城-PTN3900-23-TPA1EG24-1",\
            "bizScenario":"专线质差","faultTime":"2026-05-11T08:21:46Z","eventSerialNo":"event-id-20260511-09013"}}""";

    /** Validate-step response whose two schema-required parameters stay null (the paramsAbsent probes). */
    private static final String VALIDATE_PARTIAL_RESPONSE =
            """
            {"semantic_verdict":true,"errors":[],"params":{"accessPort":null,"bizScenario":null,\
            "faultTime":"2026-05-11T08:21:46Z","eventSerialNo":"event-id-20260511-09013"}}""";

    /** Validate-step response of a semantically contradictory prompt (the semantic-reject probes). */
    private static final String VALIDATE_REJECT_RESPONSE =
            """
            {"semantic_verdict":false,"errors":[{"slot_name":"bizScenario","code":"content.semantic_conflict",\
            "facts":{"section_label":"投诉分类","reason":"投诉分类声明为专线质差，投诉详情却描述完全中断"}}],"params":{}}""";

    /** Hand-authored prompt following the Task-T private-line template structure (the validate family's input). */
    private static final String VALIDATE_PROMPT =
            """
            ## 任务类型(Task Type)
            传输专线业务投诉诊断

            ## 任务描述(Task Description)
            基于<任务对象>、<任务上下文> 进行投诉场景的网络侧故障根因诊断, 达成<任务目标>中定义的投诉诊断目标，按照<预期输出>中定义的结构返回任务处理结果。

            ## 任务目标(Task Target)
            对网络侧故障进行诊断，返回故障根因和修复建议等诊断结果信息。

            ## 任务对象(Task Object)
            接入端口名称：P533-珠江旧城-PTN3900-23-TPA1EG24-1

            ## 任务上下文(Task Context)
            1. 投诉分类：“专线质差”
            2. 问题发生时间：“2026-05-11T08:21:46Z”
            3. OSS侧事件流水号：“event-id-20260511-09013”
            4. 投诉详情：“从5月11号早上8点半开始，深圳访问广州的响应延迟从平均12ms骤升至320ms，柜面和手机银行的交易接口频繁报‘连接超时’。”

            ## 预期输出(Expected Output)
            要求投诉诊断任务的结果包含如下信息：
            1. 诊断结果；参数的取值范围包括：成功、失败；(必选)
            2. 诊断结果详细信息； (必选)
            3. 修复建议； (可选)""";

    @TempDir
    Path tempDir;

    @Test
    void generateCaseRunsTheClosedLoopAndAssertsTheKeyFields() {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);
        LiveCaseEngine engine = new LiveCaseEngine(
                new StepStubClient(List.of(SLOT_EXTRACTION_RESPONSE, VALIDATE_PASS_RESPONSE)), 2, null, run);
        LiveCase testCase = generateCase(new LiveExpectation(
                true,
                "private-line-complaint",
                Map.of("accessPort", "P533-珠江旧城-PTN3900-23-TPA1EG24-1", "bizScenario", "专线质差"),
                List.of(),
                List.of("## 任务类型", "event-id-20260511-09013"),
                4));

        LiveCaseResult result = engine.run(testCase);

        assertEquals(LiveCaseResult.Outcome.PASS, result.outcome());
        assertEquals("private-line-complaint", result.scenarioCode());
        assertTrue(result.inputSummary().contains("深圳访问广州的政企专线"), "the verdict carries the input summary");
        assertEquals("P533-珠江旧城-PTN3900-23-TPA1EG24-1", result.params().get("accessPort"));
        // The generation case is a mini closed loop: one slot-extraction call plus one validate call.
        assertEquals(2, result.llmCalls().size());
        LiveRunSummary summary = run.summary();
        assertEquals(1, summary.totalCases());
        assertEquals(1, summary.passCount());
        assertEquals(2, summary.totalLlmCalls());
    }

    @Test
    void validateCaseAssertsParamsContainsAndParamsAbsent() {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);
        LiveCaseEngine engine =
                new LiveCaseEngine(new StepStubClient(List.of(VALIDATE_PARTIAL_RESPONSE)), 2, null, run);
        LiveCase testCase = validateCase(new LiveExpectation(
                true,
                null,
                Map.of("faultTime", "2026-05-11T08:21:46Z", "eventSerialNo", "event-id-20260511-09013"),
                List.of("accessPort", "bizScenario"),
                List.of(),
                3));

        LiveCaseResult result = engine.run(testCase);

        assertEquals(LiveCaseResult.Outcome.PASS, result.outcome());
        assertEquals(1, result.llmCalls().size());
        assertNull(result.params().get("accessPort"));
        assertEquals("event-id-20260511-09013", result.params().get("eventSerialNo"));
        assertEquals(1, run.summary().passCount());
    }

    @Test
    void expectedFailureVerdictPassesWhenThePipelineRejects() {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);
        LiveCaseEngine engine = new LiveCaseEngine(new StepStubClient(List.of(VALIDATE_REJECT_RESPONSE)), 2, null, run);
        LiveCase testCase = validateCase(new LiveExpectation(false, null, Map.of(), List.of(), List.of(), 3));

        LiveCaseResult result = engine.run(testCase);

        assertEquals(LiveCaseResult.Outcome.PASS, result.outcome());
        assertEquals(1, result.llmCalls().size());
        assertNull(result.params());
        assertEquals(1, run.summary().passCount());
    }

    @Test
    void assertionMismatchFailsWithTheCaseDiff() {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);
        LiveCaseEngine engine = new LiveCaseEngine(
                new StepStubClient(List.of(SLOT_EXTRACTION_RESPONSE, VALIDATE_PASS_RESPONSE)), 2, null, run);
        LiveCase testCase = generateCase(new LiveExpectation(
                true, null, Map.of("accessPort", "P999-不存在-PTN0000-0-TPA1EG24-0"), List.of(), List.of(), 4));

        AssertionError failure =
                assertThrows(AssertionError.class, () -> engine.run(testCase), "a paramsContains mismatch is a red");

        assertTrue(failure.getMessage().contains(testCase.errorPrefix()), failure.getMessage());
        assertTrue(failure.getMessage().contains("$.expect.paramsContains"), failure.getMessage());
        assertEquals(1, run.summary().failCount(), "the failed verdict still lands in the transcript");
    }

    @Test
    void infrastructureFailuresRetryOnFreshClients() {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);
        LiveCaseEngine engine = new LiveCaseEngine(
                new StepStubClient(List.of(
                        new LLMRuntimeError("endpoint unreachable"), SLOT_EXTRACTION_RESPONSE, VALIDATE_PASS_RESPONSE)),
                2,
                null,
                run);
        LiveCase testCase =
                generateCase(new LiveExpectation(true, null, Map.of("bizScenario", "专线质差"), List.of(), List.of(), 4));

        LiveCaseResult result = engine.run(testCase);

        assertEquals(LiveCaseResult.Outcome.PASS, result.outcome());
        // The failed attempt's call is kept in the transcript while the judged attempt made the two closed-loop calls.
        assertEquals(3, result.llmCalls().size());
        assertEquals(1, run.summary().passCount());
        assertEquals(3, run.summary().totalLlmCalls());
    }

    @Test
    void exhaustedInfrastructureRetriesRecordAnErrorAndStayRed() {
        LiveTranscript.Run run = LiveTranscript.createRun(tempDir);
        LiveCaseEngine engine = new LiveCaseEngine(
                new StepStubClient(List.of(new LLMRuntimeError("endpoint unreachable"))), 0, null, run);
        LiveCase testCase =
                generateCase(new LiveExpectation(true, null, Map.of("bizScenario", "专线质差"), List.of(), List.of(), 4));

        assertThrows(RuntimeException.class, () -> engine.run(testCase), "a surviving infra failure stays a red");

        // The transcript verdict matches the build verdict: the rethrown failure is an ERROR, not a skip.
        assertEquals(1, run.summary().errorCount(), "the given-up case is recorded as ERROR");
        assertEquals(1, run.summary().totalLlmCalls());
    }

    // ------------------------------------------------------------------ fixtures

    private static LiveCase generateCase(LiveExpectation expect) {
        return new LiveCase(
                "LIVE-TEST-GEN/zh-CN",
                "LIVE-TEST-GEN",
                "live/task-apis.json",
                NegotiationApi.GENERATE_TASK_PROMPT_FROM_TEXT,
                "zh-CN",
                "P0",
                List.of("live"),
                "engine test case of the generate API",
                null,
                "Task-T/network-layer/private-line-complaint/v1",
                "深圳访问广州的政企专线自5月11日起时延骤升，柜面交易频繁超时。" + "接入端口名称：P533-珠江旧城-PTN3900-23-TPA1EG24-1；投诉分类：专线质差。",
                null,
                complaintParamsSchema(),
                expect);
    }

    private static LiveCase validateCase(LiveExpectation expect) {
        return new LiveCase(
                "LIVE-TEST-VAL/zh-CN",
                "LIVE-TEST-VAL",
                "live/task-apis.json",
                NegotiationApi.VALIDATE_TASK_PROMPT_AND_DATA_FILLING,
                "zh-CN",
                "P0",
                List.of("live"),
                "engine test case of the validate API",
                null,
                "Task-T/network-layer/private-line-complaint/v1",
                null,
                new PromptSource.Text(VALIDATE_PROMPT),
                complaintParamsSchema(),
                expect);
    }

    /** The real shared complaint schema of the corpus, so the engine tests judge against the actual validate step. */
    private static JsonNode complaintParamsSchema() {
        try (InputStream stream =
                LiveCaseEngineTest.class.getResourceAsStream("/negotiation-cases/shared/schemas.json")) {
            assertNotNull(stream, "the shared schemas of the corpus must be on the test classpath");
            return MAPPER.readTree(stream).path("biz.complaint.params");
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read the shared corpus schemas", exception);
        }
    }

    /**
     * Step-playing stub client: it answers the canned response (or rethrows the canned exception) of the next script
     * step and repeats the last step once the script is exhausted — the offline stand-in for the live endpoint.
     */
    private static final class StepStubClient implements LLMClient {

        private final List<Object> steps;

        private int cursor;

        StepStubClient(List<Object> steps) {
            this.steps = List.copyOf(steps);
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            Object step = steps.get(Math.min(cursor++, steps.size() - 1));
            if (step instanceof RuntimeException error) {
                throw error;
            }
            return new LLMResponse((String) step, "stub-model", Map.of(), Map.of());
        }
    }
}
