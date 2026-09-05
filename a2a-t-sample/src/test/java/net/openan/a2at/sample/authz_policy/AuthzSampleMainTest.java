package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ClientExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ServerExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioResult;
import net.openan.a2at.sdk.core.model.MetadataContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthzSampleMainTest {

    private static final AuthzExpected SUCCESS = new AuthzExpected(
            new ClientExpected(null, "## rendered prompt", null), new ServerExpected("success", null, null));
    private static final ScenarioResult MATCH_RESULT =
            new ScenarioResult("success", true, null, List.of(), true, true, true, List.of());
    private static final ScenarioResult MISMATCH_RESULT =
            new ScenarioResult("slot.rule_violation", false, null, List.of(), null, null, null, List.of());

    private PrintStream originalOut;
    private ByteArrayOutputStream outContent;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void should_returnZeroExitCode_WhenAllScenariosMatch() {
        assertEquals(0, AuthzSampleMain.exitCode(List.of(MATCH_RESULT, MATCH_RESULT, MATCH_RESULT)));
    }

    @Test
    void should_returnNonZeroExitCode_WhenAnyMismatch() {
        assertTrue(AuthzSampleMain.exitCode(List.of(MATCH_RESULT, MISMATCH_RESULT, MATCH_RESULT)) != 0);
    }

    @Test
    void should_resolveEnvPath_ArgsFirst() {
        Path result = AuthzSampleMain.resolveEnvPath(new String[] {"custom.env"});
        assertEquals(Path.of("custom.env"), result);
    }

    @Test
    void should_resolveEnvPath_WorkingDirFallback() throws IOException {
        Path cwdEnv = Path.of("authz.env");
        try {
            Files.writeString(cwdEnv, "A2AT_LLM_PROVIDER=openai\nA2AT_LLM_MODEL=gpt-4\nA2AT_LLM_API_KEY=sk-test\n");
            Path result = AuthzSampleMain.resolveEnvPath(new String[0]);
            assertEquals(cwdEnv.toAbsolutePath(), result.toAbsolutePath());
        } finally {
            Files.deleteIfExists(cwdEnv);
        }
    }

    @Test
    void should_resolveEnvPath_ClasspathFallback() {
        Path result = AuthzSampleMain.resolveEnvPath(new String[0]);
        assertTrue(
                result.toString().contains("authz-policy") && result.toString().endsWith("authz.env"));
    }

    @Test
    void should_hasRequiredLlmKeys_ReturnTrue_WhenAllKeysPresent(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "A2AT_LLM_PROVIDER=openai\nA2AT_LLM_MODEL=gpt-4\nA2AT_LLM_API_KEY=sk-12345\n");
        assertTrue(AuthzSampleMain.hasRequiredLlmKeys(envFile));
    }

    @Test
    void should_hasRequiredLlmKeys_ReturnFalse_WhenKeyMissing(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "A2AT_LLM_PROVIDER=openai\nA2AT_LLM_MODEL=gpt-4\n");
        assertFalse(AuthzSampleMain.hasRequiredLlmKeys(envFile));
    }

    @Test
    void should_hasRequiredLlmKeys_ReturnFalse_WhenKeyBlank(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, "A2AT_LLM_PROVIDER=openai\nA2AT_LLM_MODEL=gpt-4\nA2AT_LLM_API_KEY=\n");
        assertFalse(AuthzSampleMain.hasRequiredLlmKeys(envFile));
    }

    @Test
    void should_hasRequiredLlmKeys_ReturnFalse_WhenFileNotFound() {
        assertFalse(AuthzSampleMain.hasRequiredLlmKeys(Path.of("nonexistent.env")));
    }

    @Test
    void should_loadParamSchema_ReturnBusinessLevelSchema() {
        Map<String, Object> schema = AuthzSampleMain.loadParamSchema();

        assertNotNull(schema);
        assertTrue(schema.containsKey("properties"));
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertTrue(properties.containsKey("operationType"));
        assertTrue(properties.containsKey("policyList"));
    }

    @Test
    void should_printScenarioReport_WhenMetadataNull() {
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);
        ScenarioOutcome outcome = new ScenarioOutcome(MISMATCH_RESULT, null, null);
        AuthzSampleMain.printScenarioReport(scenario, outcome);
        String output = outContent.toString();
        assertTrue(output.contains("--- Scenario: test ---"));
        assertTrue(output.contains("[Client]"));
        assertTrue(output.contains("slot.rule_violation"));
        assertTrue(output.contains("(跳过 - 客户端生成失败)"));
    }

    @Test
    void should_printScenarioReport_WhenFilledNull() {
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);
        MetadataContent metadata = new MetadataContent("template-uri", "prompt text", "extension-uri");
        ScenarioResult result = new ScenarioResult(
                "negotiation.semantic_rejected", false, null, List.of(), true, false, null, List.of());
        ScenarioOutcome outcome = new ScenarioOutcome(result, metadata, null);
        AuthzSampleMain.printScenarioReport(scenario, outcome);
        String output = outContent.toString();
        assertTrue(output.contains("[Server]"));
        assertTrue(output.contains("negotiation.semantic_rejected"));
        assertTrue(output.contains("校验结果"));
    }

    @Test
    void should_printSummary() {
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), SUCCESS);
        AuthzSampleMain.printSummary(List.of(scenario), List.of(MATCH_RESULT, MISMATCH_RESULT, MISMATCH_RESULT));
        String output = outContent.toString();
        assertTrue(output.contains("Match"));
        assertTrue(output.contains("Mismatch"));
    }

    @Test
    void should_writeReport_WithStagedExpectationsAndAssertions(@TempDir Path tempDir) throws IOException {
        AuthzScenario scenario = new AuthzScenario(
                "test",
                "from_text",
                Map.of("text", "hello"),
                new AuthzExpected(
                        new ClientExpected(null, "prompt text", null),
                        new ServerExpected("success", null, Map.of("slot1", "expected_value"))));
        MetadataContent metadata = new MetadataContent("template-uri", "prompt text", "extension-uri");
        ScenarioOutcome outcome = new ScenarioOutcome(
                MATCH_RESULT,
                metadata,
                new net.openan.a2at.sdk.core.model.FilledParamData(Map.of("slot1", "actual_value")));

        Path reportPath = AuthzSampleMain.writeReport(
                List.of(scenario),
                List.of(outcome),
                tempDir,
                8,
                12.5,
                "sample/authz-policy/scenarios.json",
                false,
                null);

        assertTrue(Files.exists(reportPath));
        String content = Files.readString(reportPath);
        assertTrue(content.contains("\"expected_client\""));
        assertTrue(content.contains("\"expected_server\""));
        assertTrue(content.contains("\"actual_outcome\""));
        assertTrue(content.contains("\"success\""));
        assertTrue(content.contains("\"match\""));
        assertTrue(content.contains("\"expected_value\""));
        assertTrue(content.contains("\"actual_params\""));
        assertTrue(content.contains("\"actual_value\""));
        assertTrue(content.contains("\"prompt_text\""));
        assertTrue(content.contains("\"prompt text\""));
        assertTrue(content.contains("\"assertions\""));
        assertTrue(content.contains("\"actual_slot_errors\""));
    }

    @Test
    void should_writeReport_WithSlotErrors(@TempDir Path tempDir) throws IOException {
        AuthzScenario scenario = new AuthzScenario(
                "test",
                "from_text",
                Map.of("text", "hello"),
                new AuthzExpected(
                        new ClientExpected(
                                "slot.not_provided",
                                null,
                                List.of(new AuthzScenario.SlotErrorExpectation("授权策略的操作类型", "slot.not_provided"))),
                        null));
        ScenarioResult result = new ScenarioResult(
                "slot.not_provided",
                true,
                null,
                List.of(new net.openan.a2at.sdk.core.model.SlotValidationError(
                        "授权策略的操作类型", "slot.not_provided", "输入中未提供「授权策略的操作类型」。")),
                null,
                null,
                null,
                List.of());
        ScenarioOutcome outcome = new ScenarioOutcome(result, null, null);

        Path reportPath = AuthzSampleMain.writeReport(
                List.of(scenario),
                List.of(outcome),
                tempDir,
                8,
                12.5,
                "sample/authz-policy/scenarios.json",
                false,
                null);

        String content = Files.readString(reportPath);
        assertTrue(content.contains("授权策略的操作类型"));
        assertTrue(content.contains("slot.not_provided"));
        assertTrue(content.contains("输入中未提供「授权策略的操作类型」。"));
    }
}
