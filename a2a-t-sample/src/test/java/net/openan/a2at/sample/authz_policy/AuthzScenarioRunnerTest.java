package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import org.junit.jupiter.api.Test;

class AuthzScenarioRunnerTest {

    private static final TemplateUri TEMPLATE_URI = StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT;
    private static final Map<String, Object> SLOT_SCHEMA = Map.of("properties", Map.of(), "required", Map.of());

    @Test
    void should_dispatchToFromTextGenerator() {
        AtomicReference<String> calledEntry = new AtomicReference<>();
        AuthzPromptGenerator generator = scenario -> {
            calledEntry.set(scenario.entry());
            return new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");

        runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("from_text", calledEntry.get());
    }

    @Test
    void should_dispatchToFromDataWithSchemaGenerator() {
        AtomicReference<String> calledEntry = new AtomicReference<>();
        AuthzPromptGenerator generator = scenario -> {
            calledEntry.set(scenario.entry());
            return new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario(
                "test", "from_data_with_schema", Map.of("data", Map.of("k", "v"), "schema", Map.of("k", "d")), "pass");

        runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("from_data_with_schema", calledEntry.get());
    }

    @Test
    void should_returnPass_WhenExpectedPassAndValidationPasses() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of("k", "v"));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("PASS", outcome.result().name());
        assertNotNull(outcome.metadata());
        assertNotNull(outcome.filled());
    }

    @Test
    void should_returnPass_WhenExpectedRejectAndSemanticRejected() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(A2ATErrorCodes.VALIDATION_SEMANTIC_REJECTED, "semantic rejected");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "reject");

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("PASS", outcome.result().name());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_returnFail_WhenExpectedPassAndValidationRejected() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(A2ATErrorCodes.VALIDATION_SEMANTIC_REJECTED, "semantic rejected");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("FAIL", outcome.result().name());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_returnFail_WhenExpectedRejectAndValidationPasses() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of("k", "v"));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "reject");

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("FAIL", outcome.result().name());
        assertNotNull(outcome.metadata());
        assertNotNull(outcome.filled());
    }

    @Test
    void should_returnFail_WhenGeneratorThrowsPromptGenerationException() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException("slot_schema_not_found", "schema not found");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("FAIL", outcome.result().name());
        assertNotNull(outcome.result().error());
        assertNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void
            should_returnError_WhenValidatorThrowsContentValidationException_WithCode_validation_llm_infrastructure_error() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_LLM_INFRASTRUCTURE_ERROR, "infrastructure error");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("ERROR", outcome.result().name());
        assertNotNull(outcome.result().error());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void
            should_returnError_WhenValidatorThrowsContentValidationException_WithCode_validation_prompt_resource_not_found() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(
                    A2ATErrorCodes.VALIDATION_PROMPT_RESOURCE_NOT_FOUND, "resource not found");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("ERROR", outcome.result().name());
        assertNotNull(outcome.result().error());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_returnError_WhenValidatorThrowsContentValidationException_WithUnknownCode() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI.uri(), "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException("unknown_code", "unknown error");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");

        ScenarioOutcome outcome = runner.run(scenario, SLOT_SCHEMA, TEMPLATE_URI);

        assertEquals("ERROR", outcome.result().name());
        assertNotNull(outcome.result().error());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }
}
