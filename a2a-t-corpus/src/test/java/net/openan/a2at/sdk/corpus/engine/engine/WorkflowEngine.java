package net.openan.a2at.sdk.corpus.engine.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.corpus.engine.assertion.ExpectationComparator;
import net.openan.a2at.sdk.corpus.engine.llm.RecordingMeteredLlmClient;
import net.openan.a2at.sdk.corpus.engine.loader.ExpectedStep;
import net.openan.a2at.sdk.corpus.engine.loader.InputCase;
import net.openan.a2at.sdk.corpus.engine.loader.InputStep;
import net.openan.a2at.sdk.corpus.engine.registry.ApiRegistry;
import net.openan.a2at.sdk.corpus.engine.util.ThrowableSerializer;

/**
 * Case engine: dispatches the ordered steps of one case to the registered SDK APIs, records the full transcript and
 * compares every step against its expectation. Cases are fully isolated: one failing case never aborts the run.
 */
public final class WorkflowEngine {

    private final ApiRegistry registry;

    private final RecordingMeteredLlmClient recorder;

    public WorkflowEngine(ApiRegistry registry, RecordingMeteredLlmClient recorder) {
        this.registry = registry;
        this.recorder = recorder;
    }

    /** Runs one case and returns its transcript; this method never throws for case-level failures. */
    public CaseResult run(String scenarioName, InputCase inputCase) {
        recorder.drain();

        List<Map<String, Object>> priorPayloads = new ArrayList<>();
        List<StepOutcome> outcomes = new ArrayList<>();
        String failReason = "";
        boolean priorAssertionFailed = false;
        boolean crashed = false;

        for (int i = 0; i < inputCase.input().size(); i++) {
            InputStep step = inputCase.input().get(i);
            ExpectedStep expected = inputCase.expected().get(i);
            StepOutcome outcome;

            if (priorAssertionFailed && !"error".equals(expected.result())) {
                outcome = StepOutcome.skipped(i + 1, step.api(), softResolve(step, i + 1, priorPayloads), 0L);
            } else {
                outcome = executeStep(i + 1, step, priorPayloads);
                ExpectationComparator.StepAssertion assertion =
                        ExpectationComparator.compare(expected, outcome);
                if (!assertion.passed()) {
                    if (failReason.isEmpty()) {
                        failReason = assertion.failMessage();
                    }
                    priorAssertionFailed = true;
                }
                if (outcome.crashed()) {
                    crashed = true;
                }
            }
            priorPayloads.add(outcome.payload());
            outcomes.add(outcome);
        }

        String verdict = "success";
        if (crashed) {
            verdict = "error";
        } else if (priorAssertionFailed) {
            verdict = "failure";
        }

        long totalDuration = 0L;
        int totalInputToken = 0;
        int totalOutputToken = 0;
        List<Interaction> interactions = new ArrayList<>();
        int counter = 0;
        for (StepOutcome outcome : outcomes) {
            counter++;
            int stepInputToken = tokenSum(outcome.llmCalls(), true);
            int stepOutputToken = tokenSum(outcome.llmCalls(), false);
            totalInputToken += stepInputToken;
            totalOutputToken += stepOutputToken;
            totalDuration += outcome.durationMs();
            interactions.add(new Interaction(
                    counter,
                    outcome.api(),
                    outcome.actualResult(),
                    outcome.durationMs(),
                    outcome.request() == null ? Map.of() : outcome.request(),
                    displayResponse(outcome),
                    stepInputToken,
                    stepOutputToken));
            for (RecordingMeteredLlmClient.RecordedLlmCall call : outcome.llmCalls()) {
                counter++;
                interactions.add(new Interaction(
                        counter,
                        "LLM",
                        call.result(),
                        call.durationMs(),
                        call.request(),
                        call.response(),
                        call.inputToken(),
                        call.outputToken()));
            }
        }

        return new CaseResult(
                scenarioName,
                inputCase,
                verdict,
                failReason,
                totalDuration,
                totalInputToken,
                totalOutputToken,
                interactions);
    }

    private StepOutcome executeStep(
            int stepNo, InputStep step, List<Map<String, Object>> priorPayloads) {
        long startedAt = System.nanoTime();
        Map<String, Object> request;
        try {
            request = FromStepResolver.resolve(step.args(), stepNo, priorPayloads);
        } catch (RuntimeException error) {
            long duration = elapsedMillis(startedAt);
            return StepOutcome.error(
                    stepNo,
                    step.api(),
                    step.args(),
                    ThrowableSerializer.toMap(error),
                    null,
                    error.getMessage(),
                    true,
                    duration,
                    recorder.drain());
        }
        recorder.drain();
        try {
            Map<String, Object> payload = registry.handler(step.api())
                    .orElseThrow(() -> new IllegalArgumentException("api not registered: " + step.api()))
                    .run(request);
            List<RecordingMeteredLlmClient.RecordedLlmCall> llmCalls = recorder.drain();
            return StepOutcome.success(stepNo, step.api(), request, payload, elapsedMillis(startedAt), llmCalls);
        } catch (Throwable error) {
            List<RecordingMeteredLlmClient.RecordedLlmCall> llmCalls = recorder.drain();
            boolean coded = error instanceof A2ATError;
            return StepOutcome.error(
                    stepNo,
                    step.api(),
                    request,
                    ThrowableSerializer.toMap(error),
                    coded ? ((A2ATError) error).getCode() : null,
                    error.getMessage(),
                    !coded,
                    elapsedMillis(startedAt),
                    llmCalls);
        }
    }

    private static Map<String, Object> softResolve(
            InputStep step, int stepNo, List<Map<String, Object>> priorPayloads) {
        try {
            return FromStepResolver.resolve(step.args(), stepNo, priorPayloads);
        } catch (RuntimeException error) {
            return step.args();
        }
    }

    private static Map<String, Object> displayResponse(StepOutcome outcome) {
        if ("error".equals(outcome.actualResult())) {
            return outcome.errorResponse() == null ? Map.of() : outcome.errorResponse();
        }
        if ("success".equals(outcome.actualResult())) {
            return outcome.payload() == null ? Map.of() : outcome.payload();
        }
        Map<String, Object> skipped = new LinkedHashMap<>();
        skipped.put("reason", "Previous step assertion failed; this step was skipped");
        return skipped;
    }

    private static int tokenSum(
            List<RecordingMeteredLlmClient.RecordedLlmCall> calls, boolean input) {
        int sum = 0;
        for (RecordingMeteredLlmClient.RecordedLlmCall call : calls) {
            sum += input ? call.inputToken() : call.outputToken();
        }
        return sum;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    /** Execution transcript of one case. */
    public record CaseResult(
            String scenario,
            InputCase inputCase,
            String verdict,
            String failReason,
            long totalDurationMs,
            int totalInputToken,
            int totalOutputToken,
            List<Interaction> interactions) {}

    /** One transcript row: either an SDK API step or one of its underlying LLM calls, globally numbered. */
    public record Interaction(
            int step,
            String type,
            String result,
            long durationMs,
            Map<String, Object> request,
            Map<String, Object> response,
            int inputToken,
            int outputToken) {}
}