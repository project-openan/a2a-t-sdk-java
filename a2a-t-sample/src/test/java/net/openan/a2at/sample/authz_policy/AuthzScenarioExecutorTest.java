package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ClientExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ServerExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuthzScenarioExecutorTest {

    private static final String TEMPLATE_URI = StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI;
    private static final Map<String, Object> PARAM_SCHEMA = Map.of("properties", Map.of(), "required", Map.of());
    private static final AuthzExpected SUCCESS = new AuthzExpected(
            new ClientExpected(null, "generated prompt", null), new ServerExpected("success", null, null));

    @AfterEach
    void clearProperty() {
        System.clearProperty("authz.workers");
    }

    private static AuthzScenario scenario(String label) {
        return new AuthzScenario(label, "from_text", Map.of("text", "hello"), SUCCESS);
    }

    @Test
    void should_preserveInputOrder_WhenSlowerScenarioFinishesLast() throws Exception {
        CountDownLatch gate = new CountDownLatch(2);
        AuthzPromptGenerator generator = scenario -> {
            if ("c0".equals(scenario.label())) {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                gate.countDown();
            }
            return new MetadataContent(TEMPLATE_URI, scenario.label(), "Authorization-T/v1");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenarioExecutor executor = new AuthzScenarioExecutor(runner);

        List<AuthzScenario> scenarios = List.of(scenario("c0"), scenario("c1"), scenario("c2"));
        List<ScenarioOutcome> outcomes = executor.executeAll(scenarios, PARAM_SCHEMA, TEMPLATE_URI, 3, null);

        assertEquals(3, outcomes.size());
        assertEquals("c0", outcomes.get(0).metadata().promptText());
        assertEquals("c1", outcomes.get(1).metadata().promptText());
        assertEquals("c2", outcomes.get(2).metadata().promptText());
    }

    @Test
    void should_isolateFailures_WhenTaskThrows() {
        AuthzPromptGenerator generator = scenario -> {
            switch (scenario.label()) {
                case "boom-runtime":
                    throw new RuntimeException("runtime failure");
                case "boom-assert":
                    throw new AssertionError("assertion failure");
                default:
                    return new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
            }
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenarioExecutor executor = new AuthzScenarioExecutor(runner);

        List<AuthzScenario> scenarios =
                List.of(scenario("ok1"), scenario("boom-runtime"), scenario("ok2"), scenario("boom-assert"));
        List<ScenarioOutcome> outcomes = executor.executeAll(scenarios, PARAM_SCHEMA, TEMPLATE_URI, 4, null);

        assertEquals(4, outcomes.size());
        assertTrue(outcomes.get(0).result().match());
        assertEquals("infra.internal_error", outcomes.get(1).result().outcome());
        assertFalse(outcomes.get(1).result().match());
        assertTrue(outcomes.get(2).result().match());
        assertEquals("infra.internal_error", outcomes.get(3).result().outcome());
        assertFalse(outcomes.get(3).result().match());
        assertNotNull(outcomes.get(3).result().slotErrors());
        assertEquals(1, outcomes.get(3).result().slotErrors().size());
        assertEquals("_llm", outcomes.get(3).result().slotErrors().get(0).slotName());
    }

    @Test
    void should_deliverProgressCallbacks_WithDistinctCompletionIndices() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenarioExecutor executor = new AuthzScenarioExecutor(runner);

        ConcurrentHashMap<Integer, Double> seen = new ConcurrentHashMap<>();
        List<AuthzScenario> scenarios = List.of(scenario("a"), scenario("b"), scenario("c"), scenario("d"));

        List<ScenarioOutcome> outcomes =
                executor.executeAll(scenarios, PARAM_SCHEMA, TEMPLATE_URI, 4, (index, outcome, elapsed) -> {
                    assertTrue(elapsed >= 0.0);
                    seen.put(index, elapsed);
                });

        assertEquals(4, outcomes.size());
        assertEquals(Set.of(1, 2, 3, 4), seen.keySet());
    }

    @Test
    void should_runAllScenariosSerially_WhenSingleWorker() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenarioExecutor executor = new AuthzScenarioExecutor(runner);

        List<AuthzScenario> scenarios = List.of(scenario("a"), scenario("b"), scenario("c"));
        List<ScenarioOutcome> outcomes = executor.executeAll(scenarios, PARAM_SCHEMA, TEMPLATE_URI, 1, null);

        assertEquals(3, outcomes.size());
        for (ScenarioOutcome outcome : outcomes) {
            assertTrue(outcome.result().match());
        }
    }

    @Test
    void should_returnEmptyList_WhenNoScenarios() {
        AuthzScenarioRunner runner = new AuthzScenarioRunner(
                s -> new MetadataContent("uri", "prompt", "ext"), (p, s, t) -> new FilledParamData(Map.of()));
        AuthzScenarioExecutor executor = new AuthzScenarioExecutor(runner);

        AtomicInteger callbackCalls = new AtomicInteger();
        List<ScenarioOutcome> outcomes = executor.executeAll(
                List.of(), PARAM_SCHEMA, TEMPLATE_URI, 8, (index, outcome, elapsed) -> callbackCalls.incrementAndGet());

        assertTrue(outcomes.isEmpty());
        assertEquals(0, callbackCalls.get());
    }

    @Test
    void should_notThrow_WhenProgressListenerIsNull() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenarioExecutor executor = new AuthzScenarioExecutor(runner);

        List<ScenarioOutcome> outcomes =
                executor.executeAll(List.of(scenario("a")), PARAM_SCHEMA, TEMPLATE_URI, 1, null);

        assertEquals(1, outcomes.size());
        assertTrue(outcomes.get(0).result().match());
    }

    @Test
    void should_resolveWorkers_DefaultTo8() {
        System.clearProperty("authz.workers");
        assertEquals(8, AuthzScenarioExecutor.resolveWorkers());
    }

    @Test
    void should_resolveWorkers_FromSystemProperty() {
        System.setProperty("authz.workers", "3");
        assertEquals(3, AuthzScenarioExecutor.resolveWorkers());
    }

    @Test
    void should_resolveWorkers_ThrowOnNonPositive() {
        for (String value : List.of("0", "-1")) {
            System.setProperty("authz.workers", value);
            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, AuthzScenarioExecutor::resolveWorkers);
            assertTrue(ex.getMessage().contains("authz.workers"));
        }
    }

    @Test
    void should_resolveWorkers_ThrowOnNonNumeric() {
        System.setProperty("authz.workers", "abc");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, AuthzScenarioExecutor::resolveWorkers);
        assertTrue(ex.getMessage().contains("authz.workers"));
    }
}
