package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuthzScenarioLoaderTest {

    @Test
    void should_loadAndValidateAllScenarios() {
        List<AuthzScenario> scenarios = AuthzScenarioLoader.load("sample/authz-policy/scenarios.json");

        assertEquals(3, scenarios.size());
        assertEquals("add-from-text", scenarios.get(0).label());
        assertEquals("from_text", scenarios.get(0).entry());
        assertEquals("pass", scenarios.get(0).expected());
        assertEquals("add-from-data", scenarios.get(1).label());
        assertEquals("from_data_with_schema", scenarios.get(1).entry());
        assertEquals("pass", scenarios.get(1).expected());
        assertEquals("invalid-request", scenarios.get(2).label());
        assertEquals("from_text", scenarios.get(2).entry());
        assertEquals("reject", scenarios.get(2).expected());
    }

    @Test
    void should_throwIllegalStateException_WhenResourceNotFound() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> AuthzScenarioLoader.load("sample/authz-policy/nonexistent.json"));

        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void should_throwIllegalStateException_WhenJsonMalformed() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> AuthzScenarioLoader.load("sample/authz-policy/test/malformed.json"));

        assertTrue(ex.getMessage().contains("malformed") || ex.getMessage().contains("parse"));
    }

    @Test
    void should_throwIllegalArgumentException_WhenScenarioHasInvalidEntry() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AuthzScenarioLoader.load("sample/authz-policy/test/invalid-scenarios.json"));

        assertTrue(ex.getMessage().contains("bad-scenario"));
    }

    @Test
    void should_throwIllegalStateException_WhenScenariosIsNotAnArray() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> AuthzScenarioLoader.load("sample/authz-policy/test/scenarios-as-string.json"));

        assertTrue(ex.getMessage().contains("scenarios"));
    }

    @Test
    void should_throwIllegalStateException_WhenScenarioLabelIsNotAString() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> AuthzScenarioLoader.load("sample/authz-policy/test/scenario-label-non-string.json"));

        assertTrue(ex.getMessage().contains("label"));
    }
}
