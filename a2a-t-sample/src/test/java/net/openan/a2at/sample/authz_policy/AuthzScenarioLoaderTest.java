package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthzScenarioLoaderTest {

    @Test
    void should_loadAndValidateAllScenarios() {
        List<AuthzScenario> scenarios = AuthzScenarioLoader.load("sample/authz-policy/scenarios.json");

        assertEquals(15, scenarios.size());
        // 预期成功在前（index 0-7），预期拒绝在中（index 8-12），客户端拦截在末尾（index 13-14）
        assertEquals("c1-nl-add-01", scenarios.get(0).label());
        assertEquals("from_text", scenarios.get(0).entry());
        assertEquals("success", scenarios.get(0).expected().server().outcome());
        assertEquals("c1-nl-add-01-varname", scenarios.get(1).label());
        assertEquals("from_text", scenarios.get(1).entry());
        assertEquals("success", scenarios.get(1).expected().server().outcome());
        assertNotNull(scenarios.get(1).validateSchema());
        assertEquals("c3-nl-mod-06", scenarios.get(3).label());
        assertEquals("success", scenarios.get(3).expected().server().outcome());
        assertEquals("c3-nl-mod-06-varfields", scenarios.get(4).label());
        assertEquals("success", scenarios.get(4).expected().server().outcome());
        assertNotNull(scenarios.get(4).validateSchema());
        assertEquals("b4-nl-bad-id-01", scenarios.get(7).label());
        assertEquals("success", scenarios.get(7).expected().server().outcome());
        assertEquals("b3-nl-invalid-mod-01", scenarios.get(8).label());
        assertEquals(
                "negotiation.semantic_rejected",
                scenarios.get(8).expected().server().outcome());
        assertEquals("b2-nl-format-01", scenarios.get(9).label());
        assertEquals(
                "negotiation.semantic_rejected",
                scenarios.get(9).expected().server().outcome());
        assertEquals("b2-nl-format-01-varreq", scenarios.get(10).label());
        assertEquals(
                "negotiation.semantic_rejected",
                scenarios.get(10).expected().server().outcome());
        assertNotNull(scenarios.get(10).validateSchema());
        assertEquals("b1-nl-missing-01", scenarios.get(11).label());
        assertEquals(
                "negotiation.semantic_rejected",
                scenarios.get(11).expected().server().outcome());
        assertEquals("c6-nl-mixed-07", scenarios.get(12).label());
        assertEquals(
                "negotiation.semantic_rejected",
                scenarios.get(12).expected().server().outcome());
        assertEquals("a-data-starve-01", scenarios.get(13).label());
        assertEquals("from_data_with_schema", scenarios.get(13).entry());
        assertEquals("slot.not_provided", scenarios.get(13).expected().client().outcome());
        assertNull(scenarios.get(13).expected().server());
        assertEquals("a-nl-neg-01", scenarios.get(14).label());
        assertEquals("slot.not_provided", scenarios.get(14).expected().client().outcome());
        assertNull(scenarios.get(14).expected().server());
    }

    @Test
    void should_loadAndValidateFullSuiteScenarios() {
        List<AuthzScenario> scenarios = AuthzScenarioLoader.load("sample/authz-policy/scenarios-100.json");

        assertEquals(102, scenarios.size());
        long variants = scenarios.stream()
                .filter(s -> s.label().endsWith("-varname")
                        || s.label().endsWith("-varfields")
                        || s.label().endsWith("-varflat")
                        || s.label().endsWith("-varreq")
                        || s.label().endsWith("-varsch")
                        || s.label().endsWith("-varuuid")
                        || s.label().endsWith("-vardesc")
                        || s.label().endsWith("-dual"))
                .count();
        assertEquals(47, variants);
        long clientInterceptions = scenarios.stream()
                .filter(s ->
                        s.expected().client() != null && s.expected().client().outcome() != null)
                .count();
        assertEquals(9, clientInterceptions);
        long serverRejections = scenarios.stream()
                .filter(s -> s.expected().server() != null
                        && "negotiation.semantic_rejected"
                                .equals(s.expected().server().outcome()))
                .count();
        assertEquals(37, serverRejections);
        long validateSchemas =
                scenarios.stream().filter(s -> s.validateSchema() != null).count();
        assertEquals(43, validateSchemas);
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

    @Test
    void should_parseValidateSchema_WhenPresent() {
        List<AuthzScenario> scenarios =
                AuthzScenarioLoader.load("sample/authz-policy/test/validate-schema-scenarios.json");

        assertEquals(2, scenarios.size());
        AuthzScenario present = scenarios.get(0);
        assertEquals("vs-present", present.label());
        assertNotNull(present.validateSchema());
        assertEquals("应为简短的处置动作短语", ((Map<?, ?>) present.validateSchema().get("动网操作的授权策略列表")).get("处置类型"));
    }

    @Test
    void should_haveNullValidateSchema_WhenAbsent() {
        List<AuthzScenario> scenarios =
                AuthzScenarioLoader.load("sample/authz-policy/test/validate-schema-scenarios.json");

        assertEquals("vs-missing", scenarios.get(1).label());
        assertNull(scenarios.get(1).validateSchema());
    }

    @Test
    void should_throwIllegalStateException_WhenValidateSchemaIsEmpty() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> AuthzScenarioLoader.load("sample/authz-policy/test/validate-schema-empty.json"));

        assertTrue(ex.getMessage().contains("validate_schema"));
    }
}
