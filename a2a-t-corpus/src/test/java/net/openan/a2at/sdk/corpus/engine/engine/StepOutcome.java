package net.openan.a2at.sdk.corpus.engine.engine;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.corpus.engine.llm.RecordingMeteredLlmClient;

/**
 * Result of executing one input step.
 *
 * @param stepNo 1-based step number
 * @param api executed API method name
 * @param actualResult {@code success} / {@code error} / {@code skipped}
 * @param request resolved request arguments actually passed to the SDK
 * @param payload serialized response payload on success
 * @param errorResponse serialized exception dump on error
 * @param errCode machine-readable error code; {@code null} when the failure is not an SDK coded error
 * @param errMessage failure message
 * @param durationMs step wall-clock duration
 * @param llmCalls LLM invocations recorded during this step
 * @param crashed true when the step failed with a non-coded error (engine-level crash)
 */
public record StepOutcome(
        int stepNo,
        String api,
        String actualResult,
        Map<String, Object> request,
        Map<String, Object> payload,
        Map<String, Object> errorResponse,
        String errCode,
        String errMessage,
        long durationMs,
        List<RecordingMeteredLlmClient.RecordedLlmCall> llmCalls,
        boolean crashed) {

    public static StepOutcome success(
            int stepNo,
            String api,
            Map<String, Object> request,
            Map<String, Object> payload,
            long durationMs,
            List<RecordingMeteredLlmClient.RecordedLlmCall> llmCalls) {
        return new StepOutcome(stepNo, api, "success", request, payload, null, null, null, durationMs, llmCalls, false);
    }

    public static StepOutcome error(
            int stepNo,
            String api,
            Map<String, Object> request,
            Map<String, Object> errorResponse,
            String errCode,
            String errMessage,
            boolean crashed,
            long durationMs,
            List<RecordingMeteredLlmClient.RecordedLlmCall> llmCalls) {
        return new StepOutcome(
                stepNo, api, "error", request, null, errorResponse, errCode, errMessage, durationMs, llmCalls, crashed);
    }

    public static StepOutcome skipped(int stepNo, String api, Map<String, Object> request, long durationMs) {
        return new StepOutcome(stepNo, api, "skipped", request, null, null, null, null, durationMs, List.of(), false);
    }
}