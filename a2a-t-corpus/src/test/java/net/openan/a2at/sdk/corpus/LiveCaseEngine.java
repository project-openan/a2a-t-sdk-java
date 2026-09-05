package net.openan.a2at.sdk.corpus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.jspecify.annotations.Nullable;

/**
 * Executes one {@link LiveCase} against the real LLM endpoint of the live family (design document §4) and asserts its
 * {@link LiveExpectation}.
 *
 * <p>Everything except the model itself is production assembly: the engine wraps the real provider client built from
 * {@link LiveLlmConfig} in a {@link RecordingLLMClient} and hands the wrapper to the {@link TaskApiAssembler} — the
 * real facade builders' assembly of the closed loop — driven by the {@code LiveLlmEnvWriter} bridge, so the pipeline
 * retry limit comes from the bridge's {@code A2AT_LLM_MAX_ATTEMPTS=3} ([R6]/[R7], not a per-record value); the offline
 * engine-test seam keeps the scripted minimal env instead. A generation case runs the whole mini closed loop its
 * records assert against: the generated prompt is fed straight into {@code validateTaskPromptAndDataFilling} with the
 * record's schema, so {@code paramsContains}/{@code paramsAbsent} judge the validate-step parameter keys and the two
 * calls together make up the {@code maxLlmCalls} budget. {@code scenarioCode} compares the scenario segment of the
 * template URI the pipeline echoes — the from-text task API resolves the template from the caller's URI and reports no
 * separate recognition result.
 *
 * <p>Retry semantics (Q7): only infrastructure failures retry — LLM runtime errors and IO/timeout exceptions, raw or
 * wrapped by the pipeline's LLM-infrastructure error codes — at most {@code -Dcorpus.live.infraRetries} times (default
 * 2), each attempt on a fresh recording client; an assertion mismatch fails immediately. Every case verdict is appended
 * to the run's {@link LiveTranscript} before it surfaces: the transcript keeps the recorded LLM calls of the failed
 * attempts too, while {@code maxLlmCalls} bounds only the final, judged attempt.
 *
 * @since 2026-08
 */
public final class LiveCaseEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * System property overriding the infra-retry limit (live design document §4, same property channel as case.filter).
     */
    static final String INFRA_RETRIES_PROPERTY = "corpus.live.infraRetries";

    /** Default of the infra-retry limit: at most two retries, three attempts in total. */
    private static final int DEFAULT_INFRA_RETRIES = 2;

    /** Fixed pipeline retry limit of the live env bridge ([R6]: the record does not declare it). */
    private static final int PIPELINE_MAX_ATTEMPTS = 3;

    private static final int MAX_REPORTED_TEXT_LENGTH = 400;

    private final LLMClient llmClient;

    private final int infraRetries;

    /** The {@code LiveLlmEnvWriter} bridge of the configured run; null on the offline engine-test seam. */
    private final @Nullable Path envFile;

    private final LiveTranscript.Run transcript;

    /**
     * Creates the engine for one suite run: the real provider client of the given configuration, the default
     * infra-retry limit, the live {@code .env} bridge of the configuration and the run the verdicts are appended to.
     *
     * @param config resolved live test configuration
     * @param transcript run handle the case verdicts are appended to
     */
    public LiveCaseEngine(LiveLlmConfig config, LiveTranscript.Run transcript) {
        this(LiveLlmConfig.createLlmClient(config), infraRetryLimit(), LiveLlmEnvWriter.envFileFor(config), transcript);
    }

    /**
     * Creates the engine around an explicit LLM client, env file and infra-retry limit — the seam of the offline engine
     * tests, which drive the assertion logic with a stub client and the scripted minimal env instead of a real endpoint
     * and the live env bridge.
     *
     * @param llmClient the LLM client every recording wrapper delegates to
     * @param infraRetries number of infrastructure retries after the first attempt (negative values clamp to 0)
     * @param envFile the {@code .env} file the task assembly loads (the {@code LiveLlmEnvWriter} bridge), or null to
     *     fall back to the scripted minimal env with the fixed pipeline retry limit
     * @param transcript run handle the case verdicts are appended to
     */
    LiveCaseEngine(LLMClient llmClient, int infraRetries, @Nullable Path envFile, LiveTranscript.Run transcript) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.infraRetries = Math.max(0, infraRetries);
        this.envFile = envFile;
        this.transcript = Objects.requireNonNull(transcript, "transcript");
    }

    /**
     * Runs one live case, appends its verdict to the transcript and returns it.
     *
     * @param testCase expanded live corpus case
     * @return the verdict of the executed case
     * @throws AssertionError when a live expectation mismatches (the transcript carries the same diff)
     * @throws RuntimeException when the engine itself failed, or an infrastructure failure survived all retries
     */
    public LiveCaseResult run(LiveCase testCase) {
        long startNanos = System.nanoTime();
        int attempts = infraRetries + 1;
        List<LiveLlmCall> recordedCalls = new ArrayList<>();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            RecordingLLMClient recording = new RecordingLLMClient(llmClient);
            TaskApiAssembler assembler = envFile == null
                    ? new TaskApiAssembler(testCase.language(), PIPELINE_MAX_ATTEMPTS, recording)
                    : new TaskApiAssembler(envFile, recording);
            try {
                Judged judged = judge(assembler, recording, testCase);
                recordedCalls.addAll(recording.snapshot());
                boolean passed = judged.failure() == null;
                LiveCaseResult result = asResult(
                        testCase,
                        judged,
                        passed ? LiveCaseResult.Outcome.PASS : LiveCaseResult.Outcome.FAIL,
                        recordedCalls,
                        Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                        passed ? null : judged.failure().getMessage());
                transcript.appendCase(result);
                if (!passed) {
                    throw judged.failure();
                }
                return result;
            } catch (RuntimeException error) {
                recordedCalls.addAll(recording.snapshot());
                if (isInfrastructureFailure(error) && attempt < attempts) {
                    continue;
                }
                // A surviving failure is recorded as ERROR and rethrown, so the transcript verdict and the build
                // verdict agree on the same red instead of the summary counting a skip the build reports as an error.
                String failureDiff = isInfrastructureFailure(error)
                        ? "infrastructure failure after " + attempts + " attempts: " + error
                        : error.toString();
                LiveCaseResult result = asResult(
                        testCase,
                        null,
                        LiveCaseResult.Outcome.ERROR,
                        recordedCalls,
                        Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                        failureDiff);
                transcript.appendCase(result);
                throw error;
            }
        }
        throw new IllegalStateException(
                testCase.errorPrefix() + " the live engine left the retry loop without a verdict");
    }

    // ------------------------------------------------------------------ judging

    /**
     * Judges one attempt without throwing for an expectation mismatch: the returned {@link Judged} carries the pipeline
     * output plus the first failed assertion, so the transcript can embed what the pipeline actually produced even for
     * a failed case. Only infrastructure failures propagate — they are retried by {@link #run}.
     */
    private static Judged judge(TaskApiAssembler assembler, RecordingLLMClient recording, LiveCase testCase) {
        LiveExpectation expect = testCase.liveExpect();
        Judgement judgement = null;
        RuntimeException pipelineFailure = null;
        try {
            judgement = invoke(assembler, testCase);
        } catch (RuntimeException error) {
            if (isInfrastructureFailure(error)) {
                throw error;
            }
            pipelineFailure = error;
        }
        @Nullable AssertionError failure = null;
        if (expect.success()) {
            failure = pipelineFailure != null
                    ? fail(testCase, "$.expect.success", "success", "failure (" + pipelineFailure + ")")
                    : assertValueExpectations(testCase, judgement);
        } else if (pipelineFailure == null) {
            failure = fail(
                    testCase,
                    "$.expect.success",
                    "failure",
                    "success (" + render(judgement == null ? null : judgement.params()) + ")");
        }
        if (failure == null && expect.maxLlmCalls() != null && recording.callCount() > expect.maxLlmCalls()) {
            failure = fail(
                    testCase,
                    "$.expect.maxLlmCalls",
                    "at most " + expect.maxLlmCalls() + " LLM calls",
                    String.valueOf(recording.callCount()));
        }
        return new Judged(judgement, failure);
    }

    /**
     * Asserts the value-level live expectations; returns the first mismatch instead of throwing so the caller keeps the
     * pipeline output for the transcript.
     */
    private static @Nullable AssertionError assertValueExpectations(LiveCase testCase, Judgement judgement) {
        LiveExpectation expect = testCase.liveExpect();
        if (expect.scenarioCode() != null && !expect.scenarioCode().equals(judgement.scenarioCode())) {
            return fail(
                    testCase, "$.expect.scenarioCode", expect.scenarioCode(), String.valueOf(judgement.scenarioCode()));
        }
        Map<String, Object> params =
                judgement.params() == null ? Map.of() : judgement.params().data();
        for (Map.Entry<String, Object> entry : expect.paramsContains().entrySet()) {
            Object actual = params.get(entry.getKey());
            if (actual == null) {
                return fail(
                        testCase,
                        "$.expect.paramsContains",
                        entry.getKey() + "=" + entry.getValue(),
                        entry.getKey() + "=" + (params.containsKey(entry.getKey()) ? "null" : "(missing)"));
            }
            if (!valueMatches(entry.getValue(), actual)) {
                return fail(
                        testCase,
                        "$.expect.paramsContains",
                        entry.getKey() + "=" + entry.getValue(),
                        entry.getKey() + "=" + actual);
            }
        }
        for (String slot : expect.paramsAbsent()) {
            if (params.get(slot) != null) {
                return fail(
                        testCase, "$.expect.paramsAbsent", slot + " null or missing", slot + "=" + params.get(slot));
            }
        }
        for (String fragment : expect.promptTextContains()) {
            String promptText =
                    judgement.message() == null || judgement.message().promptText() == null
                            ? ""
                            : judgement.message().promptText();
            if (!normalize(promptText).contains(normalize(fragment))) {
                return fail(
                        testCase,
                        "$.expect.promptTextContains",
                        "a prompt text containing '" + fragment + "'",
                        quoted(truncate(normalize(promptText))));
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ API dispatch

    /**
     * Invokes the case's API on the real task assembly: the generation API runs the mini closed loop (generate, then
     * validate the generated prompt against the record's schema), the validation API validates the record's prompt.
     */
    private static Judgement invoke(TaskApiAssembler assembler, LiveCase testCase) {
        TemplateUri templateUri = parseTemplateUri(testCase);
        Map<String, Object> schema = requireSchema(testCase);
        return switch (testCase.api()) {
            case GENERATE_TASK_PROMPT_FROM_TEXT -> {
                MetadataContent message = assembler.generateTaskPromptFromText(requireInputText(testCase), templateUri);
                FilledParamData params =
                        assembler.validateTaskPromptAndDataFilling(message.promptText(), schema, templateUri);
                yield new Judgement(message, params, scenarioOf(message.templateUri()));
            }
            case VALIDATE_TASK_PROMPT_AND_DATA_FILLING -> {
                FilledParamData params =
                        assembler.validateTaskPromptAndDataFilling(requirePromptText(testCase), schema, templateUri);
                yield new Judgement(null, params, scenarioOf(templateUri.uri()));
            }
            default -> throw new IllegalStateException(
                    testCase.errorPrefix() + " the live engine covers the two phase-1 task APIs but got "
                            + testCase.api().jsonName());
        };
    }

    // ------------------------------------------------------------------ result assembly

    private static LiveCaseResult asResult(
            LiveCase testCase,
            @Nullable Judged judged,
            LiveCaseResult.Outcome outcome,
            List<LiveLlmCall> recordedCalls,
            long durationMs,
            @Nullable String failureDiff) {
        Judgement judgement = judged == null ? null : judged.judgement();
        return new LiveCaseResult(
                testCase.id(),
                outcome,
                assertionSummary(testCase, recordedCalls.size()),
                inputSummaryOf(testCase),
                judgement == null ? null : judgement.scenarioCode(),
                judgement == null || judgement.params() == null
                        ? null
                        : judgement.params().data(),
                recordedCalls,
                durationMs,
                failureDiff);
    }

    /**
     * Excerpt of what the case fed the pipeline (design document §5: the transcript's input summary): the
     * natural-language text of a generation case, or the prompt text a validation case validates, truncated to the
     * reporting length.
     */
    private static @Nullable String inputSummaryOf(LiveCase testCase) {
        String input = testCase.api() == NegotiationApi.GENERATE_TASK_PROMPT_FROM_TEXT
                ? testCase.inputText()
                : testCase.prompt() instanceof PromptSource.Text text ? text.text() : null;
        return input == null ? null : truncate(normalize(input));
    }

    /** One-line summary of what the case asserted, with the actual LLM call count for quick budget reading. */
    private static String assertionSummary(LiveCase testCase, int llmCalls) {
        LiveExpectation expect = testCase.liveExpect();
        StringBuilder summary = new StringBuilder("expect.success=").append(expect.success());
        if (expect.scenarioCode() != null) {
            summary.append(" scenarioCode=").append(expect.scenarioCode());
        }
        if (!expect.paramsContains().isEmpty()) {
            summary.append(" paramsContains=").append(expect.paramsContains().keySet());
        }
        if (!expect.paramsAbsent().isEmpty()) {
            summary.append(" paramsAbsent=").append(expect.paramsAbsent());
        }
        if (!expect.promptTextContains().isEmpty()) {
            summary.append(" promptTextContains=")
                    .append(expect.promptTextContains().size());
        }
        if (expect.maxLlmCalls() != null) {
            summary.append(" maxLlmCalls<=").append(expect.maxLlmCalls());
        }
        return summary.append(" llmCalls=").append(llmCalls).toString();
    }

    // ------------------------------------------------------------------ classification

    /**
     * Classifies one pipeline exception as an infrastructure failure (Q7): the LLM client's runtime errors and
     * IO/timeout exceptions, raw or anywhere in the pipeline's wrapping cause chain, or an error carrying one of the
     * retryable {@code llm.*} codes. Semantic rejections and rule violations are verdicts, not outages, and never
     * retry.
     */
    private static boolean isInfrastructureFailure(Throwable error) {
        for (Throwable current = error;
                current != null;
                current = current.getCause() == current ? null : current.getCause()) {
            if (current instanceof LLMRuntimeError || current instanceof IOException) {
                return true;
            }
            if (current instanceof A2ATError a2atError
                    && (ErrorCatalog.LLM_INVOCATION_FAILED.getCode().equals(a2atError.getCode())
                            || ErrorCatalog.LLM_RESPONSE_INVALID.getCode().equals(a2atError.getCode())
                            || ErrorCatalog.LLM_NOT_CONFIGURED.getCode().equals(a2atError.getCode()))) {
                return true;
            }
        }
        return false;
    }

    private static int infraRetryLimit() {
        Integer limit = Integer.getInteger(INFRA_RETRIES_PROPERTY, DEFAULT_INFRA_RETRIES);
        return limit == null ? DEFAULT_INFRA_RETRIES : Math.max(0, limit);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Compares one expected slot value with the extracted one: strings compare trimmed (model outputs may pad), and a
     * number matches its JSON string form so a schema-declared number and its quoted extraction stay equivalent.
     */
    private static boolean valueMatches(Object expected, Object actual) {
        Double expectedNumber = toDoubleOrNull(expected);
        Double actualNumber = toDoubleOrNull(actual);
        if (expectedNumber != null
                && actualNumber != null
                && (expected instanceof Number || actual instanceof Number)) {
            return Double.compare(expectedNumber, actualNumber) == 0;
        }
        return String.valueOf(expected).trim().equals(String.valueOf(actual).trim());
    }

    private static @Nullable Double toDoubleOrNull(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException error) {
                return null;
            }
        }
        return null;
    }

    /**
     * Returns the scenario segment of a template URI — the last path segment of the Task-T layout
     * ({@code Task-T/network-layer/private-line-complaint/v1} names the scenario {@code private-line-complaint}); null
     * when the URI does not parse.
     */
    private static @Nullable String scenarioOf(@Nullable String templateUri) {
        return TemplateUri.parse(templateUri)
                .map(uri -> uri.pathSegments().get(uri.pathSegments().size() - 1))
                .orElse(null);
    }

    private static TemplateUri parseTemplateUri(LiveCase testCase) {
        String raw = testCase.templateUri();
        if (raw == null) {
            throw new IllegalStateException(
                    testCase.errorPrefix() + " templateUri: the live task APIs require a template URI");
        }
        return TemplateUri.parse(raw)
                .orElseThrow(() -> new IllegalStateException(
                        testCase.errorPrefix() + " templateUri: unparseable template URI " + raw));
    }

    private static String requireInputText(LiveCase testCase) {
        String text = testCase.inputText();
        if (text == null) {
            throw new IllegalStateException(
                    testCase.errorPrefix() + " input.text: the task from-text API requires a text input");
        }
        return text;
    }

    private static String requirePromptText(LiveCase testCase) {
        if (testCase.prompt() instanceof PromptSource.Text text) {
            return text.text();
        }
        throw new IllegalStateException(
                testCase.errorPrefix() + " prompt.text: the live validate API requires an inline prompt text");
    }

    private static Map<String, Object> requireSchema(LiveCase testCase) {
        JsonNode schema = testCase.schema();
        if (schema == null) {
            throw new IllegalStateException(testCase.errorPrefix() + " schema: the live task APIs require a schema");
        }
        return MAPPER.convertValue(schema, new TypeReference<Map<String, Object>>() {});
    }

    private static String render(@Nullable FilledParamData params) {
        return params == null ? "(no parameter data)" : String.valueOf(params.data());
    }

    private static String normalize(@Nullable String text) {
        return text == null ? "" : text.replace("\r\n", "\n");
    }

    private static String truncate(String text) {
        return text.length() <= MAX_REPORTED_TEXT_LENGTH ? text : text.substring(0, MAX_REPORTED_TEXT_LENGTH) + "...";
    }

    private static String quoted(@Nullable String text) {
        return text == null ? "null" : "<" + text + ">";
    }

    private static AssertionError fail(LiveCase testCase, String jsonPath, String expected, String actual) {
        return new AssertionError(
                testCase.errorPrefix() + " " + jsonPath + ": expected " + expected + " but was " + actual);
    }

    // ------------------------------------------------------------------ internal records

    /**
     * The judgement of one attempt: the pipeline output plus the first failed assertion, or null when the attempt
     * passed.
     */
    private record Judged(@Nullable Judgement judgement, @Nullable AssertionError failure) {}

    /**
     * What the pipeline produced for one case: the generated message of a generation case (null for a pure validation
     * case or an expected failure), the filled parameter data of the validate step, and the scenario code the pipeline
     * reported.
     */
    private record Judgement(
            @Nullable MetadataContent message, @Nullable FilledParamData params, @Nullable String scenarioCode) {}
}
