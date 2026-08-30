package net.openan.a2at.sample.task_t;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the field/sample scoring logic of the Task-T accuracy demo.
 *
 * <p>Two match modes exist: {@link TaskTAccuracyEvaluator.MatchMode#EXACT} applies to structured identifier fields
 * (access port, scenario, time, serial) where a truncated or partially matching value must NOT score a hit; and
 * {@link TaskTAccuracyEvaluator.MatchMode#CONTAINS} applies to the free-text complaint detail where the extracted value
 * and the expected keyword are allowed to contain each other after normalization.
 *
 * <p>The match mode is now determined by the slot name rather than passed explicitly. Use
 * {@link TaskTPrivateLineComplaintSamples#SERVER_PORT} for EXACT mode tests and
 * {@link TaskTPrivateLineComplaintSamples#SERVER_DETAIL} for CONTAINS mode tests.
 */
class TaskTAccuracyEvaluatorTest {

    private static final String PORT = "P781-KXVN-PTN7900-23-TPA1EG24-17";
    private static final String EXACT_SLOT = TaskTPrivateLineComplaintSamples.SERVER_PORT;
    private static final String CONTAINS_SLOT = TaskTPrivateLineComplaintSamples.SERVER_DETAIL;

    // ---------------------------------------------------------------------
    // matches(): EXACT mode (via SERVER_PORT slot)
    // ---------------------------------------------------------------------

    @Test
    void exact_shouldHit_WhenValuesEqualAfterNormalization() {
        assertTrue(TaskTAccuracyEvaluator.matches(PORT, PORT, EXACT_SLOT));
        // whitespace and case are ignored before comparison
        assertTrue(TaskTAccuracyEvaluator.matches("event-Id-20260511-09013", "EVENT-ID-20260511-09013", EXACT_SLOT));
    }

    @Test
    void exact_shouldNotHit_WhenExtractedIsOnlyAPrefixOfExpected() {
        // the port identifier must be extracted in full; a bare "P781" prefix must not score a hit
        assertFalse(TaskTAccuracyEvaluator.matches("P781", PORT, EXACT_SLOT));
    }

    @Test
    void exact_shouldNotHit_WhenExtractedContainsExpectedButIsLonger() {
        // "connect timeout..." is not equal to the expected serial — trailing content must not pass as equal
        assertFalse(
                TaskTAccuracyEvaluator.matches("event-id-20260511-09013-ext", "event-id-20260511-09013", EXACT_SLOT));
    }

    @Test
    void exact_shouldNotHit_WhenValuesAreSimilarButNotEqual() {
        assertFalse(TaskTAccuracyEvaluator.matches("专线掉线", "专线中断", EXACT_SLOT));
        assertFalse(TaskTAccuracyEvaluator.matches("2026-05-11T08:21:46", "2026-05-11", EXACT_SLOT));
    }

    // ---------------------------------------------------------------------
    // matches(): CONTAINS mode (free-text complaint detail, via SERVER_DETAIL slot)
    // ---------------------------------------------------------------------

    @Test
    void contains_shouldHit_WhenExtractedDetailMentionsTheExpectedKeyword() {
        String detail = "专线时延抖动明显，平均丢包率约5%、峰值达15%，视频会议频繁卡顿。";
        assertTrue(TaskTAccuracyEvaluator.matches(detail, "丢包", CONTAINS_SLOT));
        assertTrue(TaskTAccuracyEvaluator.matches(detail, "时延", CONTAINS_SLOT));
    }

    @Test
    void contains_shouldHit_WhenExpectedKeywordContainsTheExtractedValue() {
        // expected ground truth is a longer phrase that happens to contain the extracted words
        assertTrue(TaskTAccuracyEvaluator.matches("时延抖动", "专线时延抖动明显，峰值达15%", CONTAINS_SLOT));
    }

    @Test
    void contains_shouldNotHit_WhenKeywordIsAbsent() {
        assertFalse(TaskTAccuracyEvaluator.matches("专线完全中断，流量归零。", "丢包", CONTAINS_SLOT));
    }

    // ---------------------------------------------------------------------
    // matches(): empty / null handling
    // ---------------------------------------------------------------------

    @Test
    void shouldNotHit_WhenExtractedIsNullOrBlank() {
        assertFalse(TaskTAccuracyEvaluator.matches(null, PORT, EXACT_SLOT));
        assertFalse(TaskTAccuracyEvaluator.matches("", PORT, EXACT_SLOT));
        assertFalse(TaskTAccuracyEvaluator.matches("   ", PORT, EXACT_SLOT));
    }

    @Test
    void shouldNotHit_WhenExpectedIsBlank() {
        assertFalse(TaskTAccuracyEvaluator.matches(PORT, "", EXACT_SLOT));
    }

    // ---------------------------------------------------------------------
    // matches(): date normalization (faultTime slot)
    // ---------------------------------------------------------------------

    private static final String TIME_SLOT = TaskTPrivateLineComplaintSamples.SERVER_TIME;

    @Test
    void time_shouldHit_WhenIsoMatchesChineseMonthDay() {
        // ISO with timezone offset vs Chinese month-day format
        assertTrue(TaskTAccuracyEvaluator.matches("2026-05-11T08:30:00+08:00", "5月11号", TIME_SLOT));
        // ISO without timezone vs Chinese format
        assertTrue(TaskTAccuracyEvaluator.matches("2026-05-11T08:30:00", "5月11号", TIME_SLOT));
        // ISO with Z suffix vs Chinese format
        assertTrue(TaskTAccuracyEvaluator.matches("2026-06-08T15:00:00Z", "6月8号", TIME_SLOT));
    }

    @Test
    void time_shouldHit_WhenBothAreSameFormat() {
        // both Chinese format
        assertTrue(TaskTAccuracyEvaluator.matches("5月11号", "5月11号", TIME_SLOT));
        // both ISO format
        assertTrue(TaskTAccuracyEvaluator.matches("2026-05-11T08:30:00", "2026-05-11", TIME_SLOT));
    }

    @Test
    void time_shouldNotHit_WhenMonthDayDiffers() {
        assertFalse(TaskTAccuracyEvaluator.matches("2026-05-11T08:30:00", "5月12号", TIME_SLOT));
        assertFalse(TaskTAccuracyEvaluator.matches("2026-06-08T15:00:00Z", "6月9号", TIME_SLOT));
    }

    // ---------------------------------------------------------------------
    // scoreFields(): per-field mode selection
    // ---------------------------------------------------------------------

    @Test
    void scoreFields_shouldScoreStructuredFieldsExactly_AndDetailByContains() {
        TaskTSample sample = new TaskTSample(
                "text-prefix-leak",
                "text",
                null,
                null,
                Map.of(
                        TaskTPrivateLineComplaintSamples.SERVER_PORT, PORT,
                        TaskTPrivateLineComplaintSamples.SERVER_SCENARIO, "专线质差",
                        TaskTPrivateLineComplaintSamples.SERVER_TICKET, "event-id-20260511-09013",
                        TaskTPrivateLineComplaintSamples.SERVER_DETAIL, "丢包"),
                Map.of());

        // extracted accessPort is truncated to the prefix; detail is a full sentence mentioning the keyword
        Map<String, Object> extracted = Map.of(
                TaskTPrivateLineComplaintSamples.SERVER_PORT, "P781",
                TaskTPrivateLineComplaintSamples.SERVER_SCENARIO, "专线质差",
                TaskTPrivateLineComplaintSamples.SERVER_TICKET, "event-id-20260511-09013",
                TaskTPrivateLineComplaintSamples.SERVER_DETAIL, "专线时延抖动明显，丢包峰值达15%");

        List<TaskTAccuracyEvaluator.FieldScore> fields = TaskTAccuracyEvaluator.scoreFields(sample, extracted);

        assertEquals(4, fields.size());
        assertFalse(
                field(fields, TaskTPrivateLineComplaintSamples.SERVER_PORT).matched(),
                "truncated port prefix must not hit");
        assertTrue(
                field(fields, TaskTPrivateLineComplaintSamples.SERVER_SCENARIO).matched(), "exact scenario must hit");
        assertTrue(field(fields, TaskTPrivateLineComplaintSamples.SERVER_TICKET).matched(), "exact serial must hit");
        assertTrue(
                field(fields, TaskTPrivateLineComplaintSamples.SERVER_DETAIL).matched(),
                "detail containing the keyword must hit");
    }

    private static TaskTAccuracyEvaluator.FieldScore field(
            List<TaskTAccuracyEvaluator.FieldScore> fields, String slot) {
        return fields.stream().filter(f -> f.slot().equals(slot)).findFirst().orElseThrow();
    }

    @Test
    void sampleScore_shouldPass_OnlyWhenEveryFieldHit() {
        Map<String, String> expected = Map.of(
                TaskTPrivateLineComplaintSamples.SERVER_PORT,
                PORT,
                TaskTPrivateLineComplaintSamples.SERVER_DETAIL,
                "丢包");
        TaskTSample sample = new TaskTSample("hit-all", "text", null, null, expected, Map.of());

        Map<String, Object> fullExtraction = Map.of(
                TaskTPrivateLineComplaintSamples.SERVER_PORT,
                PORT,
                TaskTPrivateLineComplaintSamples.SERVER_DETAIL,
                "故障现象：丢包率最高15%。");
        TaskTAccuracyEvaluator.SampleScore pass = new TaskTAccuracyEvaluator.SampleScore(
                sample.name(), true, TaskTAccuracyEvaluator.scoreFields(sample, fullExtraction));
        assertTrue(pass.passed());

        Map<String, Object> brokenExtraction = Map.of(
                TaskTPrivateLineComplaintSamples.SERVER_PORT, "P781",
                TaskTPrivateLineComplaintSamples.SERVER_DETAIL, "故障现象：丢包率最高15%。");
        TaskTAccuracyEvaluator.SampleScore fail = new TaskTAccuracyEvaluator.SampleScore(
                sample.name(), true, TaskTAccuracyEvaluator.scoreFields(sample, brokenExtraction));
        assertFalse(fail.passed());
    }

    @Test
    void summarize_shouldAggregateMatchedFieldsAndPassedSamples() {
        Map<String, String> expected = Map.of(TaskTPrivateLineComplaintSamples.SERVER_PORT, PORT);
        TaskTSample sample = new TaskTSample("agg", "text", null, null, expected, Map.of());

        Map<String, Object> oneHit = Map.of(TaskTPrivateLineComplaintSamples.SERVER_PORT, PORT);
        List<TaskTAccuracyEvaluator.SampleScore> scores = List.of(
                new TaskTAccuracyEvaluator.SampleScore(
                        "agg-1", true, TaskTAccuracyEvaluator.scoreFields(sample, oneHit)),
                new TaskTAccuracyEvaluator.SampleScore(
                        "agg-2", true, TaskTAccuracyEvaluator.scoreFields(sample, Map.of())));

        TaskTAccuracyEvaluator.Summary summary = TaskTAccuracyEvaluator.summarize("api", scores);

        assertEquals(2, summary.sampleCount());
        assertEquals(1, summary.passedSamples());
        assertEquals(1, summary.matchedFields());
        assertEquals(2, summary.expectedFields());
        assertEquals(50.0d, summary.fieldAccuracyPercent(), 1e-6);
        assertEquals(50.0d, summary.samplePassRatePercent(), 1e-6);
    }
}
