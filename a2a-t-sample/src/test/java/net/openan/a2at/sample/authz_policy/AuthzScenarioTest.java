package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ClientExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ServerExpected;
import org.junit.jupiter.api.Test;

class AuthzScenarioTest {

    private static AuthzExpected success() {
        return new AuthzExpected(
                new ClientExpected(null, "## rendered prompt", null), new ServerExpected("success", null, null));
    }

    @Test
    void should_acceptValidFromTextScenario() {
        AuthzScenario scenario = new AuthzScenario("add-from-text", "from_text", Map.of("text", "test"), success());

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_acceptValidFromDataWithSchemaScenario() {
        AuthzScenario scenario = new AuthzScenario(
                "add-from-data",
                "from_data_with_schema",
                Map.of("data", Map.of("k", "v"), "schema", Map.of("k", "description")),
                success());

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_rejectInvalidEntry() {
        AuthzScenario scenario = new AuthzScenario("bad", "invalid_entry", Map.of("text", "test"), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("Invalid entry: invalid_entry", ex.getMessage());
    }

    @Test
    void should_rejectInvalidClientOutcome() {
        AuthzScenario scenario = new AuthzScenario(
                "bad",
                "from_text",
                Map.of("text", "test"),
                new AuthzExpected(new ClientExpected("invalid_outcome", null, null), null));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("Invalid expected client outcome: invalid_outcome", ex.getMessage());
    }

    @Test
    void should_rejectClientOutcomeWithServerExpectation() {
        AuthzScenario scenario = new AuthzScenario(
                "bad",
                "from_text",
                Map.of("text", "test"),
                new AuthzExpected(
                        new ClientExpected("slot.rule_violation", null, null),
                        new ServerExpected("success", null, null)));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("expected.server must be null when expected.client.outcome is set", ex.getMessage());
    }

    @Test
    void should_rejectClientSuccessWithoutPromptText() {
        AuthzScenario scenario = new AuthzScenario(
                "bad",
                "from_text",
                Map.of("text", "test"),
                new AuthzExpected(new ClientExpected(null, " ", null), new ServerExpected("success", null, null)));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("expected.client.promptText must be non-blank when client outcome is not set", ex.getMessage());
    }

    @Test
    void should_rejectClientSuccessWithoutServerExpectation() {
        AuthzScenario scenario = new AuthzScenario(
                "bad",
                "from_text",
                Map.of("text", "test"),
                new AuthzExpected(new ClientExpected(null, "prompt", null), null));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertTrue(ex.getMessage().startsWith("expected.server.outcome must be one of"));
        assertTrue(ex.getMessage().contains("when client succeeds"));
    }

    @Test
    void should_acceptSlotValidationErrorOutcome() {
        AuthzScenario scenario = new AuthzScenario(
                "reject",
                "from_text",
                Map.of("text", "test"),
                new AuthzExpected(new ClientExpected("slot.rule_violation", null, null), null));

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_acceptSemanticRejectedServerOutcome() {
        AuthzScenario scenario = new AuthzScenario(
                "reject",
                "from_text",
                Map.of("text", "test"),
                new AuthzExpected(
                        new ClientExpected(null, "prompt", null),
                        new ServerExpected("negotiation.semantic_rejected", null, null)));

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_rejectNullExpected() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", Map.of("text", "test"), null);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("expected.client must not be null", ex.getMessage());
    }

    @Test
    void should_rejectFromTextMissingText() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", Map.of(), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_text scenario requires a non-blank input.text", ex.getMessage());
    }

    @Test
    void should_rejectFromTextBlankText() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", Map.of("text", "  "), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_text scenario requires a non-blank input.text", ex.getMessage());
    }

    @Test
    void should_rejectFromDataMissingData() {
        AuthzScenario scenario =
                new AuthzScenario("bad", "from_data_with_schema", Map.of("schema", Map.of("k", "d")), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.data", ex.getMessage());
    }

    @Test
    void should_rejectFromDataMissingSchema() {
        AuthzScenario scenario =
                new AuthzScenario("bad", "from_data_with_schema", Map.of("data", Map.of("k", "v")), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.schema", ex.getMessage());
    }

    @Test
    void should_rejectFromDataWithSchema_EmptyData() {
        AuthzScenario scenario = new AuthzScenario(
                "bad", "from_data_with_schema", Map.of("data", Map.of(), "schema", Map.of("k", "d")), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.data", ex.getMessage());
    }

    @Test
    void should_rejectFromDataWithSchema_EmptySchema() {
        AuthzScenario scenario = new AuthzScenario(
                "bad", "from_data_with_schema", Map.of("data", Map.of("k", "v"), "schema", Map.of()), success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.schema", ex.getMessage());
    }

    @Test
    void should_rejectNullInput() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", null, success());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("input must not be null", ex.getMessage());
    }

    @Test
    void should_rejectNullScenario() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(null));
        assertEquals("scenario must not be null", ex.getMessage());
    }

    @Test
    void should_haveNullValidateSchema_WhenFourArgConstructor() {
        AuthzScenario scenario = new AuthzScenario("add-from-text", "from_text", Map.of("text", "test"), success());

        assertNull(scenario.validateSchema());
    }

    @Test
    void should_acceptNonEmptyValidateSchema() {
        AuthzScenario scenario = new AuthzScenario(
                "add-from-text",
                "from_text",
                Map.of("text", "test"),
                success(),
                Map.of("动网操作的授权策略列表", Map.of("处置类型", "应为简短的处置动作短语")));

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_rejectEmptyValidateSchema() {
        AuthzScenario scenario =
                new AuthzScenario("add-from-text", "from_text", Map.of("text", "test"), success(), Map.of());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("validateSchema must be non-empty when set", ex.getMessage());
    }
}
