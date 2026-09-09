package net.openan.a2at.sdk.corpus.engine.assertion;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.openan.a2at.sdk.corpus.engine.engine.StepOutcome;
import net.openan.a2at.sdk.corpus.engine.loader.ExpectedStep;

/**
 * Compares one step outcome against its expectation.
 *
 * <ul>
 * <li>{@code result} matches exactly ({@code success} / {@code error});</li>
 * <li>on success, {@code data} is a subset assertion: every listed key must equal; the asserted map is the payload
 * root, or its nested {@code data} map when the payload carries one (FilledParamData). {@code dataExact} tightens the
 * match to exactly the listed keys;</li>
 * <li>on error, {@code error.errCode} matches the SDK catalog code exactly, {@code error.errMessage} matches exactly
 * when present (otherwise any non-empty message passes) and {@code error.errMessageContains} is a substring test.</li>
 * </ul>
 */
public final class ExpectationComparator {

    /** Comparison verdict: failed flag plus one-sentence pointer-style message. */
    public record StepAssertion(boolean passed, String failMessage) {

        static StepAssertion pass() {
            return new StepAssertion(true, "");
        }

        static StepAssertion fail(String message) {
            return new StepAssertion(false, message);
        }
    }

    private ExpectationComparator() {}

    public static StepAssertion compare(ExpectedStep expected, StepOutcome outcome) {
        if ("skipped".equals(outcome.actualResult())) {
            return StepAssertion.fail("step" + outcome.stepNo() + " was skipped because a previous step assertion failed");
        }
        if ("success".equals(expected.result())) {
            return compareSuccess(expected, outcome);
        }
        return compareError(expected, outcome);
    }

    private static StepAssertion compareSuccess(ExpectedStep expected, StepOutcome outcome) {
        if (!"success".equals(outcome.actualResult())) {
            String actual = outcome.crashed()
                    ? "engine crash(" + exceptionName(outcome) + ")"
                    : "error(" + (outcome.errCode() == null ? "no error code" : outcome.errCode()) + ")";
            return StepAssertion.fail("step" + outcome.stepNo() + " expected success, actual " + actual);
        }
        Map<String, Object> target = assertionTarget(outcome.payload());
        for (Map.Entry<String, Object> entry : expected.data().entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = target.get(key);
            if (!target.containsKey(key) || !Objects.equals(expectedValue, actualValue)) {
                return StepAssertion.fail("step" + outcome.stepNo() + ".data." + key + " expected "
                        + expectedValue + " actual " + actualValue);
            }
        }
        if (expected.dataExact() && !target.keySet().equals(expected.data().keySet())) {
            Set<String> onlyActual = new LinkedHashSet<>(target.keySet());
            onlyActual.removeAll(expected.data().keySet());
            Set<String> onlyExpected = new LinkedHashSet<>(expected.data().keySet());
            onlyExpected.removeAll(target.keySet());
            return StepAssertion.fail("step" + outcome.stepNo() + " data exact assertion failed (dataExact): extra="
                    + onlyActual + " missing=" + onlyExpected);
        }
        return StepAssertion.pass();
    }

    private static StepAssertion compareError(ExpectedStep expected, StepOutcome outcome) {
        if (!"error".equals(outcome.actualResult())) {
            return StepAssertion.fail("step" + outcome.stepNo() + " expected error"
                    + (expected.errCode().isBlank() ? "" : "(" + expected.errCode() + ")") + ", actual success");
        }
        if (outcome.crashed()) {
            return StepAssertion.fail("step" + outcome.stepNo() + " expected error("
                    + expected.errCode() + "), actual engine crash(" + exceptionName(outcome) + ")");
        }
        if (!expected.errCode().isBlank() && !expected.errCode().equals(outcome.errCode())) {
            return StepAssertion.fail("step" + outcome.stepNo() + " errCode expected "
                    + expected.errCode() + " actual " + outcome.errCode());
        }
        if (!expected.errMessage().isBlank() && !expected.errMessage().equals(outcome.errMessage())) {
            return StepAssertion.fail("step" + outcome.stepNo() + " errMessage expected ["
                    + expected.errMessage() + "] actual [" + outcome.errMessage() + "]");
        }
        if (isBlank(outcome.errMessage())) {
            return StepAssertion.fail("step" + outcome.stepNo() + " expected a non-empty error message, actual is empty");
        }
        if (!expected.errMessageContains().isBlank()
                && (outcome.errMessage() == null
                        || !outcome.errMessage().contains(expected.errMessageContains()))) {
            return StepAssertion.fail("step" + outcome.stepNo() + " errMessage does not contain ["
                    + expected.errMessageContains() + "], actual [" + outcome.errMessage() + "]");
        }
        return StepAssertion.pass();
    }

    /**
     * Returns the map the data subset assertion runs against: the nested {@code data} map of a FilledParamData-like
     * payload, or the payload root.
     */
    private static Map<String, Object> assertionTarget(Map<String, Object> payload) {
        Object data = payload.get("data");
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) data;
            return nested;
        }
        return payload;
    }

    private static String exceptionName(StepOutcome outcome) {
        if (outcome.errorResponse() == null) {
            return "unknown error";
        }
        Object name = outcome.errorResponse().get("exception");
        return name == null ? "unknown error" : String.valueOf(name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}