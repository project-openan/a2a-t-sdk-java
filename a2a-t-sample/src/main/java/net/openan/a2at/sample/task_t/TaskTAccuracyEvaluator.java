package net.openan.a2at.sample.task_t;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Field-level and sample-level accuracy scoring for the {@code Task-T} demo.
 *
 * <p>Each {@link SampleScore} scores one sample: every expected ground-truth field is compared with the corresponding
 * value extracted by {@code A2ATServer#validateTaskPromptAndDataFilling}. {@link Summary} aggregates the scores per
 * client API both as a field hit rate (matched expected fields over total expected fields) and as a sample pass rate
 * (samples whose expected fields all hit over total samples).
 *
 * <p>Fields are matched in one of two modes: structured identifier fields ({@code accessPort}, {@code bizScenario},
 * {@code faultTime}, {@code eventSerialNo}) must be <em>exactly</em> equal after whitespace-and-case normalization — a
 * truncated prefix such as {@code P781} must not score a hit against the full port identifier; the free-text complaint
 * detail ({@code faultDetail}) is matched by containment, since the extracted description naturally carries more words
 * than the expected keyword. The mode is selected per slot in {@link #scoreFields}.
 */
final class TaskTAccuracyEvaluator {

    /** How one expected value is compared with the extracted value. */
    enum MatchMode {
        /** Structured identifiers: equal after normalization, no containment. */
        EXACT,
        /** Free-text fields: equal or mutually contained after normalization. */
        CONTAINS
    }

    private TaskTAccuracyEvaluator() {}

    /**
     * One expected slot compared against the extracted value.
     *
     * @param slot Chinese slot name
     * @param expected ground-truth value
     * @param extracted value extracted by the server facade; {@code null} when it was missing
     * @param matched whether the extracted value hit the expected value
     * @param detail explanation for a mismatch or a validation failure
     */
    record FieldScore(String slot, String expected, String extracted, boolean matched, String detail) {}

    /**
     * Accuracy outcome of one sample.
     *
     * @param name sample identifier
     * @param validationPassed whether the server validation step completed without a {@code ContentValidationException}
     * @param fields per-field scores; empty when generation or validation failed
     */
    record SampleScore(String name, boolean validationPassed, List<FieldScore> fields) {

        int expectedFieldCount() {
            return fields.size();
        }

        int matchedFieldCount() {
            return (int) fields.stream().filter(FieldScore::matched).count();
        }

        /** A sample passes when validation succeeded and every expected field hit. */
        boolean passed() {
            return validationPassed && matchedFieldCount() == expectedFieldCount();
        }
    }

    /**
     * Aggregated accuracy numbers for one client API case.
     *
     * @param api case label shown in the report
     * @param sampleCount total scored samples
     * @param passedSamples samples whose expected fields all hit
     * @param matchedFields field hits over all scored samples
     * @param expectedFields total expected fields over all scored samples
     */
    record Summary(String api, int sampleCount, int passedSamples, int matchedFields, int expectedFields) {

        double fieldAccuracyPercent() {
            return expectedFields == 0 ? 0d : 100d * matchedFields / expectedFields;
        }

        double samplePassRatePercent() {
            return sampleCount == 0 ? 0d : 100d * passedSamples / sampleCount;
        }
    }

    /**
     * Scores one sample: each expected value is looked up in the extracted parameter map and compared.
     *
     * @param sample source sample
     * @param extractedParams values extracted by the server facade; may carry extra slots beyond the expected ones
     * @return per-field score list in sample insertion order
     */
    static List<FieldScore> scoreFields(TaskTSample sample, Map<String, Object> extractedParams) {
        List<FieldScore> scores = new ArrayList<>();
        sample.expectedParams().forEach((slot, expected) -> {
            Object extracted = extractedParams.get(slot);
            String extractedText = extracted == null ? null : String.valueOf(extracted);
            boolean matched = matches(extractedText, expected, slot);
            String detail = matched
                    ? ""
                    : "expected=" + expected + ", extracted=" + (extractedText == null ? "<missing>" : extractedText);
            scores.add(new FieldScore(slot, expected, extractedText, matched, detail));
        });
        return scores;
    }

    /**
     * Picks the match mode for one slot: the free-text complaint detail and fault time are matched by containment,
     * every other structured field is matched exactly.
     *
     * <p>Fault time uses containment because the LLM may return the time in various formats (ISO with/without timezone,
     * Chinese natural language, etc.) while the expected value is a canonical subset.
     *
     * @param slot expected slot name keyed by the server field names
     * @return containment mode for the complaint detail and fault time, exact mode otherwise
     */
    private static MatchMode matchMode(String slot) {
        if (TaskTPrivateLineComplaintSamples.SERVER_DETAIL.equals(slot)) {
            return MatchMode.CONTAINS;
        }
        if (TaskTPrivateLineComplaintSamples.SERVER_TIME.equals(slot)) {
            return MatchMode.CONTAINS;
        }
        return MatchMode.EXACT;
    }

    /**
     * Hit rule for one slot. For structured fields, equal after normalization. For the fault detail, one contains the
     * other. For the fault time, the numeric month-day digits are extracted and compared, so both ISO
     * ({@code 2026-05-22T14:00:00Z}) and Chinese ({@code 5月22号下午两点多}) formats match the same canonical expected value.
     *
     * @param extracted extracted value, may be {@code null}
     * @param expected ground-truth value
     * @param slot server-side slot name
     * @return {@code true} when both are non-blank and the normalized values match
     */
    static boolean matches(String extracted, String expected, String slot) {
        String normalizedExtracted = normalize(extracted);
        String normalizedExpected = normalize(expected);
        if (normalizedExtracted.isEmpty() || normalizedExpected.isEmpty()) {
            return false;
        }
        if (TaskTPrivateLineComplaintSamples.SERVER_TIME.equals(slot)) {
            return matchDatePart(normalizedExtracted, normalizedExpected);
        }
        MatchMode mode = matchMode(slot);
        return switch (mode) {
            case EXACT -> normalizedExtracted.equals(normalizedExpected);
            case CONTAINS -> normalizedExtracted.contains(normalizedExpected)
                    || normalizedExpected.contains(normalizedExtracted);
        };
    }

    /**
     * Extracts the numeric month-day part from a date string and compares. Handles both ISO
     * ({@code 2026-05-22t14:00:00z}) and Chinese ({@code 5月22号下午两点多}) formats by extracting the month and day digits.
     */
    private static boolean matchDatePart(String extracted, String expected) {
        String extractedMd = extractMonthDay(extracted);
        String expectedMd = extractMonthDay(expected);
        return !extractedMd.isEmpty() && !expectedMd.isEmpty() && extractedMd.equals(expectedMd);
    }

    /** Extracts month-day digits from a date string, e.g. "2026-05-22" → "0522", "5月22号" → "0522". */
    private static String extractMonthDay(String value) {
        // Try Chinese format: "5月22号" or "2026年5月11号"
        Matcher chineseMatcher = CHINESE_DATE_PATTERN.matcher(value);
        if (chineseMatcher.find()) {
            return String.format(
                    "%02d%02d", Integer.parseInt(chineseMatcher.group(1)), Integer.parseInt(chineseMatcher.group(2)));
        }
        // Try ISO format: "2026-05-22" in "2026-05-22t14:00:00z"
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(value);
        if (isoMatcher.find()) {
            return isoMatcher.group(2) + isoMatcher.group(3);
        }
        return "";
    }

    private static final Pattern CHINESE_DATE_PATTERN = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[号日]");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Aggregates scored samples into one per-API summary.
     *
     * @param api case label shown in the report
     * @param scores scored samples
     * @return aggregated summary
     */
    static Summary summarize(String api, List<SampleScore> scores) {
        int passedSamples = (int) scores.stream().filter(SampleScore::passed).count();
        int matchedFields =
                scores.stream().mapToInt(SampleScore::matchedFieldCount).sum();
        int expectedFields =
                scores.stream().mapToInt(SampleScore::expectedFieldCount).sum();
        return new Summary(api, scores.size(), passedSamples, matchedFields, expectedFields);
    }
}
