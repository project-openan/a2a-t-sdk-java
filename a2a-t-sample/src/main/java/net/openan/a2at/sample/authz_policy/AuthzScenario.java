package net.openan.a2at.sample.authz_policy;

import java.util.Map;

/**
 * Immutable configuration item for a single demo scenario.
 *
 * @param label human-readable scenario label
 * @param entry client entry point ({@code from_text} or {@code from_data_with_schema})
 * @param input scenario input data
 * @param expected expected outcome label ({@code pass} or {@code reject})
 * @since 2026-08
 */
public record AuthzScenario(String label, String entry, Map<String, Object> input, String expected) {

    static final String FROM_TEXT = "from_text";
    static final String FROM_DATA_WITH_SCHEMA = "from_data_with_schema";
    static final String EXPECTED_PASS = "pass";
    static final String EXPECTED_REJECT = "reject";

    /**
     * Validates that the scenario configuration is well-formed.
     *
     * @param scenario the scenario to validate
     * @throws IllegalArgumentException if the configuration is invalid
     */
    public static void validate(AuthzScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }
        if (scenario.input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (!FROM_TEXT.equals(scenario.entry) && !FROM_DATA_WITH_SCHEMA.equals(scenario.entry)) {
            throw new IllegalArgumentException("Invalid entry: " + scenario.entry);
        }
        if (!EXPECTED_PASS.equals(scenario.expected) && !EXPECTED_REJECT.equals(scenario.expected)) {
            throw new IllegalArgumentException("Invalid expected: " + scenario.expected);
        }
        if (FROM_TEXT.equals(scenario.entry)) {
            Object text = scenario.input.get("text");
            if (!(text instanceof String) || ((String) text).isBlank()) {
                throw new IllegalArgumentException("from_text scenario requires a non-blank input.text");
            }
        }
        if (FROM_DATA_WITH_SCHEMA.equals(scenario.entry)) {
            Object data = scenario.input.get("data");
            if (!(data instanceof Map) || ((Map<?, ?>) data).isEmpty()) {
                throw new IllegalArgumentException("from_data_with_schema scenario requires a non-empty input.data");
            }
            Object schema = scenario.input.get("schema");
            if (!(schema instanceof Map) || ((Map<?, ?>) schema).isEmpty()) {
                throw new IllegalArgumentException("from_data_with_schema scenario requires a non-empty input.schema");
            }
        }
    }
}
