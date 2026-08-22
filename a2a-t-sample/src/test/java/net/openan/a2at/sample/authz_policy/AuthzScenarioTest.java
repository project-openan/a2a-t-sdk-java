package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthzScenarioTest {

    @Test
    void should_acceptValidFromTextScenario() {
        AuthzScenario scenario = new AuthzScenario("add-from-text", "from_text", Map.of("text", "test"), "pass");

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_acceptValidFromDataWithSchemaScenario() {
        AuthzScenario scenario = new AuthzScenario(
                "add-from-data",
                "from_data_with_schema",
                Map.of("data", Map.of("k", "v"), "schema", Map.of("k", "description")),
                "pass");

        assertDoesNotThrow(() -> AuthzScenario.validate(scenario));
    }

    @Test
    void should_rejectInvalidEntry() {
        AuthzScenario scenario = new AuthzScenario("bad", "invalid_entry", Map.of("text", "test"), "pass");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("Invalid entry: invalid_entry", ex.getMessage());
    }

    @Test
    void should_rejectInvalidExpected() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", Map.of("text", "test"), "invalid_expected");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("Invalid expected: invalid_expected", ex.getMessage());
    }

    @Test
    void should_rejectFromTextMissingText() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", Map.of(), "pass");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_text scenario requires a non-blank input.text", ex.getMessage());
    }

    @Test
    void should_rejectFromTextBlankText() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", Map.of("text", "  "), "pass");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_text scenario requires a non-blank input.text", ex.getMessage());
    }

    @Test
    void should_rejectFromDataMissingData() {
        AuthzScenario scenario =
                new AuthzScenario("bad", "from_data_with_schema", Map.of("schema", Map.of("k", "d")), "pass");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.data", ex.getMessage());
    }

    @Test
    void should_rejectFromDataMissingSchema() {
        AuthzScenario scenario =
                new AuthzScenario("bad", "from_data_with_schema", Map.of("data", Map.of("k", "v")), "pass");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.schema", ex.getMessage());
    }

    @Test
    void should_rejectFromDataWithSchema_EmptyData() {
        AuthzScenario scenario = new AuthzScenario(
                "bad", "from_data_with_schema", Map.of("data", Map.of(), "schema", Map.of("k", "d")), "pass");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.data", ex.getMessage());
    }

    @Test
    void should_rejectFromDataWithSchema_EmptySchema() {
        AuthzScenario scenario = new AuthzScenario(
                "bad", "from_data_with_schema", Map.of("data", Map.of("k", "v"), "schema", Map.of()), "pass");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("from_data_with_schema scenario requires a non-empty input.schema", ex.getMessage());
    }

    @Test
    void should_rejectNullInput() {
        AuthzScenario scenario = new AuthzScenario("bad", "from_text", null, "pass");

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(scenario));
        assertEquals("input must not be null", ex.getMessage());
    }

    @Test
    void should_rejectNullScenario() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> AuthzScenario.validate(null));
        assertEquals("scenario must not be null", ex.getMessage());
    }
}
