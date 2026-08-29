package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ClientExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ServerExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.SlotErrorExpectation;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import org.junit.jupiter.api.Test;

class AuthzScenarioRunnerTest {

    private static final String TEMPLATE_URI = StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI;
    private static final Map<String, Object> PARAM_SCHEMA = Map.of("properties", Map.of(), "required", Map.of());
    private static final AuthzExpected SUCCESS = new AuthzExpected(
            new ClientExpected(null, "generated prompt", null), new ServerExpected("success", null, null));
    private static final AuthzExpected EXPECTED_SLOT_ERROR =
            new AuthzExpected(new ClientExpected("slot.rule_violation", null, null), null);
    private static final AuthzExpected EXPECTED_SEMANTIC_REJECTED = new AuthzExpected(
            new ClientExpected(null, "generated prompt", null),
            new ServerExpected("negotiation.semantic_rejected", null, null));

    @Test
    void should_dispatchToFromTextGenerator() {
        AtomicReference<String> calledEntry = new AtomicReference<>();
        AuthzPromptGenerator generator = scenario -> {
            calledEntry.set(scenario.entry());
            return new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertEquals("from_text", calledEntry.get());
    }

    @Test
    void should_dispatchToFromDataWithSchemaGenerator() {
        AtomicReference<String> calledEntry = new AtomicReference<>();
        AuthzPromptGenerator generator = scenario -> {
            calledEntry.set(scenario.entry());
            return new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario(
                "test", "from_data_with_schema", Map.of("data", Map.of("k", "v"), "schema", Map.of("k", "d")), SUCCESS);

        runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertEquals("from_data_with_schema", calledEntry.get());
    }

    @Test
    void should_match_WhenExpectedSuccessAndValidationPasses() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of("k", "v"));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals("success", outcome.result().outcome());
        assertNotNull(outcome.metadata());
        assertNotNull(outcome.filled());
    }

    @Test
    void should_match_WhenExpectedSemanticRejectedAndSemanticRejected() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(
                    ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode(), "semantic rejected");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario =
                new AuthzScenario("test", "from_text", Map.of("text", "hello"), EXPECTED_SEMANTIC_REJECTED);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals("negotiation.semantic_rejected", outcome.result().outcome());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndValidationRejected() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(
                    ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode(), "semantic rejected");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("negotiation.semantic_rejected", outcome.result().outcome());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSemanticRejectedAndValidationPasses() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of("k", "v"));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario =
                new AuthzScenario("test", "from_text", Map.of("text", "hello"), EXPECTED_SEMANTIC_REJECTED);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("success", outcome.result().outcome());
        assertNotNull(outcome.metadata());
        assertNotNull(outcome.filled());
    }

    @Test
    void should_match_WhenPromptTextDiffersFromExpectation() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "different prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals(Boolean.FALSE, outcome.result().clientPromptMatch());
        assertEquals(Boolean.TRUE, outcome.result().serverOutcomeMatch());
        assertTrue(outcome.result().serverParamsMatch());
    }

    @Test
    void should_match_WhenPromptTextDiffersAndServerMatches() {
        AuthzExpected expected = new AuthzExpected(
                new ClientExpected(null, "expected prompt", null),
                new ServerExpected("success", null, Map.of("操作类型", "新增授权策略")));
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "drifted prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of("操作类型", "新增授权策略"));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals(Boolean.FALSE, outcome.result().clientPromptMatch());
        assertEquals(Boolean.TRUE, outcome.result().serverParamsMatch());
    }

    @Test
    void should_match_WhenPromptTextDiffersOnlyInTrailingWhitespace() {
        AuthzExpected expected = new AuthzExpected(
                new ClientExpected(null, "generated prompt  \n", null), new ServerExpected("success", null, null));
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "  generated prompt\n", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndGeneratorThrowsPromptGenerationException() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException("slot.schema_not_found", "schema not found");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("slot.schema_not_found", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_match_WhenExpectedSlotValidationErrorAndGeneratorThrowsSlotValidationError() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(
                    ErrorCatalog.SLOT_RULE_VIOLATION.getCode(), "Required slots are missing or empty: operation_type");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), EXPECTED_SLOT_ERROR);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals("slot.rule_violation", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSlotValidationErrorAndGeneratorThrowsInfrastructureError() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(ErrorCatalog.LLM_INVOCATION_FAILED.getCode(), "LLM invocation failed");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), EXPECTED_SLOT_ERROR);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("llm.invocation_failed", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndValidatorThrowsInfrastructureError() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(ErrorCatalog.LLM_INVOCATION_FAILED.getCode(), "infrastructure error");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("llm.invocation_failed", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndValidatorThrowsResourceNotFound() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException(ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(), "resource not found");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("template.not_found", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndValidatorThrowsUnknownCode() {
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            throw new ContentValidationException("unknown_code", "unknown error");
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("unknown_code", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNotNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_matchSlotErrors_WhenExpectedSlotErrorsMatchActual() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(
                    ErrorCatalog.SLOT_NOT_PROVIDED.getCode(),
                    "输入中未提供「授权策略的操作类型」。",
                    List.of(new SlotValidationError("授权策略的操作类型", "slot.not_provided", "输入中未提供「授权策略的操作类型」。")));
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzExpected expected = new AuthzExpected(
                new ClientExpected(
                        "slot.not_provided", null, List.of(new SlotErrorExpectation("授权策略的操作类型", "slot.not_provided"))),
                null);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals(1, outcome.result().slotErrors().size());
        assertEquals("授权策略的操作类型", outcome.result().slotErrors().get(0).slotName());
        assertEquals("slot.not_provided", outcome.result().slotErrors().get(0).code());
    }

    @Test
    void should_notMatchSlotErrors_WhenExpectedSlotErrorsDontMatchActual() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(
                    ErrorCatalog.SLOT_NOT_PROVIDED.getCode(),
                    "输入中未提供「授权策略的操作类型」。",
                    List.of(new SlotValidationError("授权策略的操作类型", "slot.not_provided", "输入中未提供「授权策略的操作类型」。")));
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzExpected expected = new AuthzExpected(
                new ClientExpected(
                        "slot.not_provided",
                        null,
                        List.of(new SlotErrorExpectation("动网操作的授权策略列表", "slot.not_provided"))),
                null);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("slot.not_provided", outcome.result().outcome());
    }

    @Test
    void should_notMatchSlotErrors_WhenSlotNameMatchesButCodeDiffers() {
        AuthzPromptGenerator generator = scenario -> {
            throw new PromptGenerationException(
                    ErrorCatalog.SLOT_NOT_PROVIDED.getCode(),
                    "输入中未提供「授权策略的操作类型」。",
                    List.of(new SlotValidationError("授权策略的操作类型", "slot.not_provided", "输入中未提供「授权策略的操作类型」。")));
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzExpected expected = new AuthzExpected(
                new ClientExpected(
                        "slot.not_provided",
                        null,
                        List.of(new SlotErrorExpectation("授权策略的操作类型", "slot.constraint_violated"))),
                null);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
    }

    @Test
    void should_matchParams_WhenActualIsSupersetOfExpected() {
        AuthzExpected expected = new AuthzExpected(
                new ClientExpected(null, "generated prompt", null),
                new ServerExpected("success", null, Map.of("操作类型", "新增授权策略", "策略列表", List.of(Map.of("业务场景", "校园专网")))));
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        Map<String, Object> actualEntry = new java.util.LinkedHashMap<>();
        actualEntry.put("策略标识", null);
        actualEntry.put("业务场景", "校园专网");
        actualEntry.put("处置类型", "紧急扩容");
        AuthzPromptValidator validator = (prompt, schema, templateUri) ->
                new FilledParamData(Map.of("操作类型", "新增授权策略", "策略列表", List.of(actualEntry)));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertEquals(Boolean.TRUE, outcome.result().serverParamsMatch());
    }

    @Test
    void should_notMatchParams_WhenListItemValueDiffers() {
        AuthzExpected expected = new AuthzExpected(
                new ClientExpected(null, "generated prompt", null),
                new ServerExpected("success", null, Map.of("策略列表", List.of(Map.of("业务场景", "校园专网")))));
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator =
                (prompt, schema, templateUri) -> new FilledParamData(Map.of("策略列表", List.of(Map.of("业务场景", "医疗专线"))));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals(Boolean.FALSE, outcome.result().serverParamsMatch());
    }

    @Test
    void should_notMatchParams_WhenListLengthDiffers() {
        AuthzExpected expected = new AuthzExpected(
                new ClientExpected(null, "generated prompt", null),
                new ServerExpected("success", null, Map.of("策略列表", List.of(Map.of("业务场景", "校园专网")))));
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) ->
                new FilledParamData(Map.of("策略列表", List.of(Map.of("业务场景", "校园专网"), Map.of("业务场景", "医疗专线"))));
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), expected);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals(Boolean.FALSE, outcome.result().serverParamsMatch());
    }

    @Test
    void should_notMatch_WhenExpectedSuccessAndGeneratorThrowsRuntimeException() {
        AuthzPromptGenerator generator = scenario -> {
            throw new NullPointerException("unexpected NPE");
        };
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertFalse(outcome.result().match());
        assertEquals("infra.internal_error", outcome.result().outcome());
        assertNotNull(outcome.result().error());
        assertNull(outcome.metadata());
        assertNull(outcome.filled());
    }

    @Test
    void should_warn_WhenMutationAndEmptyPolicyListSection() {
        String promptText = "## 授权策略的操作类型\n新增授权策略\n\n## 授权策略的操作描述\n描述\n\n## 动网操作的授权策略列表\n\n\n\n## 预期输出\n输出格式";
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, promptText, "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertTrue(outcome.result().warnings().contains("empty_policy_list_section"));
    }

    @Test
    void should_warn_WhenModifyAndEmptyPolicyListSection() {
        String promptText = "## 授权策略的操作类型\n修改授权策略\n\n## 授权策略的操作描述\n描述\n\n## 动网操作的授权策略列表\n\n## 预期输出\n输出格式";
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, promptText, "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertTrue(outcome.result().warnings().contains("empty_policy_list_section"));
    }

    @Test
    void should_notWarn_WhenQueryAndEmptyPolicyListSection() {
        String promptText = "## 授权策略的操作类型\n查询授权策略\n\n## 授权策略的操作描述\n描述\n\n## 动网操作的授权策略列表\n\n## 预期输出\n输出格式";
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, promptText, "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertTrue(outcome.result().warnings().isEmpty());
    }

    @Test
    void should_notWarn_WhenMutationAndNonEmptyPolicyListSection() {
        String promptText = "## 授权策略的操作类型\n新增授权策略\n\n## 授权策略的操作描述\n描述\n\n## 动网操作的授权策略列表\n校园专网，紧急扩容\n\n## 预期输出\n输出格式";
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, promptText, "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertTrue(outcome.result().warnings().isEmpty());
    }

    @Test
    void should_notWarn_WhenPromptTextHasUnknownSectionOrder() {
        String promptText = "## 授权策略的操作描述\n描述\n\n## 授权策略的操作类型\n新增授权策略\n\n## 预期输出\n输出格式\n\n## 动网操作的授权策略列表\n\n";
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, promptText, "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertTrue(outcome.result().warnings().contains("empty_policy_list_section"));
    }

    @Test
    void should_notWarn_WhenNoOperationTypeSection() {
        String promptText = "## 授权策略的操作描述\n描述\n\n## 动网操作的授权策略列表\n\n## 预期输出\n输出格式";
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, promptText, "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> new FilledParamData(Map.of());
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        ScenarioOutcome outcome = runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertTrue(outcome.result().match());
        assertTrue(outcome.result().warnings().isEmpty());
    }

    @Test
    void should_useScenarioSchema_WhenValidateSchemaPresent() {
        Map<String, Object> scenarioSchema = Map.of("处置规则", "应为短语");
        AtomicReference<Map<String, Object>> receivedSchema = new AtomicReference<>();
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            receivedSchema.set(schema);
            return new FilledParamData(Map.of());
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario =
                new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS, scenarioSchema);

        runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertSame(scenarioSchema, receivedSchema.get());
    }

    @Test
    void should_useDefaultSchema_WhenValidateSchemaAbsent() {
        AtomicReference<Map<String, Object>> receivedSchema = new AtomicReference<>();
        AuthzPromptGenerator generator =
                scenario -> new MetadataContent(TEMPLATE_URI, "generated prompt", "Authorization-T/v1");
        AuthzPromptValidator validator = (prompt, schema, templateUri) -> {
            receivedSchema.set(schema);
            return new FilledParamData(Map.of());
        };
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);

        runner.run(scenario, PARAM_SCHEMA, TEMPLATE_URI);

        assertSame(PARAM_SCHEMA, receivedSchema.get());
    }
}
