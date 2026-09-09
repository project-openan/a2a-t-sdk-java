package net.openan.a2at.sample.authz_policy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;

/**
 * Immutable configuration item for a single demo scenario.
 *
 * <p>The expected outcome is staged: {@link ClientExpected} asserts the client SDK behaviour (generation failure with
 * slot errors, or the exact rendered prompt text), {@link ServerExpected} asserts the server SDK behaviour (validation
 * outcome, slot-level error details and the extracted parameters).
 *
 * @param label human-readable scenario label
 * @param entry client entry point ({@code from_text} or {@code from_data_with_schema})
 * @param input scenario input data
 * @param expected staged expected outcome
 * @param validateSchema optional scenario-level validation schema that overrides the suite-level default parameter
 *     schema; {@code null} when the suite-level default applies
 * @since 2026-08
 */
public record AuthzScenario(
        String label,
        String entry,
        Map<String, Object> input,
        AuthzExpected expected,
        Map<String, Object> validateSchema) {

    /**
     * Convenience constructor for scenarios without a scenario-level validation schema.
     *
     * @param label human-readable scenario label
     * @param entry client entry point ({@code from_text} or {@code from_data_with_schema})
     * @param input scenario input data
     * @param expected staged expected outcome
     */
    public AuthzScenario(String label, String entry, Map<String, Object> input, AuthzExpected expected) {
        this(label, entry, input, expected, null);
    }

    static final String FROM_TEXT = "from_text";
    static final String FROM_DATA_WITH_SCHEMA = "from_data_with_schema";
    static final String EXPECTED_SUCCESS = "success";

    static final Set<String> VALID_SERVER_OUTCOMES = Set.of(
            EXPECTED_SUCCESS,
            ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode(),
            ErrorCatalog.LLM_INVOCATION_FAILED.getCode(),
            ErrorCatalog.TEMPLATE_NOT_FOUND.getCode());

    static final Set<String> VALID_CLIENT_OUTCOMES = Set.of(
            ErrorCatalog.SLOT_NOT_PROVIDED.getCode(),
            ErrorCatalog.SLOT_CONSTRAINT_VIOLATED.getCode(),
            ErrorCatalog.SLOT_RULE_VIOLATION.getCode(),
            ErrorCatalog.LLM_INVOCATION_FAILED.getCode(),
            ErrorCatalog.LLM_RESPONSE_INVALID.getCode(),
            ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(),
            ErrorCatalog.SLOT_SCHEMA_NOT_FOUND.getCode(),
            ErrorCatalog.TEMPLATE_RENDER_FAILED.getCode(),
            ErrorCatalog.TEMPLATE_LOAD_FAILED.getCode());

    /**
     * Staged expected outcome of one scenario.
     *
     * @param client client-stage expectation; a non-null client outcome declares an expected generation failure (in
     *     which case {@code server} must be {@code null}), a null client outcome declares an expected successful
     *     generation whose {@code promptText} is asserted
     * @param server server-stage expectation; {@code null} when the client stage is expected to fail
     */
    public record AuthzExpected(ClientExpected client, ServerExpected server) {}

    /**
     * Client-stage expectation.
     *
     * @param outcome expected client failure code; {@code null} means generation is expected to succeed
     * @param promptText expected rendered prompt text; asserted with trailing-whitespace-insensitive equality when
     *     {@code outcome} is {@code null}
     * @param slotErrors expected per-slot error details of the failed generation; may be {@code null} or empty when
     *     only the outcome code matters
     */
    public record ClientExpected(String outcome, String promptText, List<SlotErrorExpectation> slotErrors) {}

    /**
     * Server-stage expectation.
     *
     * @param outcome expected server outcome code ({@code success} or {@code negotiation.semantic_rejected})
     * @param slotErrors expected per-slot error details; may be {@code null} or empty when only the outcome code
     *     matters
     * @param params expected extracted parameters; asserted as a subset match (recursive over maps and ordered lists)
     *     and only meaningful when {@code outcome} is {@code success}
     */
    public record ServerExpected(String outcome, List<SlotErrorExpectation> slotErrors, Map<String, Object> params) {}

    /**
     * Expected error detail for a single slot.
     *
     * @param slotName name of the slot expected to fail
     * @param code machine-readable error code expected for this slot
     */
    public record SlotErrorExpectation(String slotName, String code) {}

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
        if (scenario.validateSchema != null && scenario.validateSchema.isEmpty()) {
            throw new IllegalArgumentException("validateSchema must be non-empty when set");
        }
        if (scenario.expected == null || scenario.expected.client() == null) {
            throw new IllegalArgumentException("expected.client must not be null");
        }
        ClientExpected client = scenario.expected.client();
        ServerExpected server = scenario.expected.server();
        if (client.outcome() != null) {
            if (!VALID_CLIENT_OUTCOMES.contains(client.outcome())) {
                throw new IllegalArgumentException("Invalid expected client outcome: " + client.outcome());
            }
            if (server != null) {
                throw new IllegalArgumentException("expected.server must be null when expected.client.outcome is set");
            }
        } else {
            if (client.promptText() == null || client.promptText().isBlank()) {
                throw new IllegalArgumentException(
                        "expected.client.promptText must be non-blank when client outcome is not set");
            }
            if (server == null || server.outcome() == null || !VALID_SERVER_OUTCOMES.contains(server.outcome())) {
                throw new IllegalArgumentException(
                        "expected.server.outcome must be one of " + VALID_SERVER_OUTCOMES + " when client succeeds");
            }
            // server.params on a non-success outcome is documentation-only (not asserted), mirroring the probe.
        }
        if (!FROM_TEXT.equals(scenario.entry) && !FROM_DATA_WITH_SCHEMA.equals(scenario.entry)) {
            throw new IllegalArgumentException("Invalid entry: " + scenario.entry);
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
