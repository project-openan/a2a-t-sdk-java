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
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioResult;
import net.openan.a2at.sdk.core.model.MetadataContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthzSampleMainTest {

    private static final ScenarioResult PASS = new ScenarioResult("PASS", null);
    private static final ScenarioResult FAIL = new ScenarioResult("FAIL", null);
    private static final ScenarioResult ERROR = new ScenarioResult("ERROR", null);

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
    void should_returnZeroExitCode_WhenAllScenariosPass() {
        assertEquals(0, AuthzSampleMain.exitCode(List.of(PASS, PASS, PASS)));
    }

    @Test
    void should_returnNonZeroExitCode_WhenAnyFail() {
        assertTrue(AuthzSampleMain.exitCode(List.of(PASS, FAIL, PASS)) != 0);
    }

    @Test
    void should_returnNonZeroExitCode_WhenAnyError() {
        assertTrue(AuthzSampleMain.exitCode(List.of(PASS, PASS, ERROR)) != 0);
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
    void should_loadSlotSchemaMap_ReturnMapWithPropertiesAndRequired() {
        Map<String, Object> schema = AuthzSampleMain.loadSlotSchemaMap("zh-CN");
        assertNotNull(schema);
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));
        assertTrue(schema.containsKey("$schema"));
        assertTrue(schema.containsKey("type"));
        assertTrue(schema.containsKey("additionalProperties"));
    }

    @Test
    void should_printScenarioReport_WhenMetadataNull() {
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");
        ScenarioOutcome outcome = new ScenarioOutcome(FAIL, null, null);
        AuthzSampleMain.printScenarioReport(scenario, outcome);
        assertTrue(outContent.toString().contains("<生成失败>"));
    }

    @Test
    void should_printScenarioReport_WhenFilledNull() {
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");
        MetadataContent metadata = new MetadataContent("template-uri", "prompt text", "extension-uri");
        ScenarioOutcome outcome = new ScenarioOutcome(PASS, metadata, null);
        AuthzSampleMain.printScenarioReport(scenario, outcome);
        assertTrue(outContent.toString().contains("<未提取参数>"));
    }

    @Test
    void should_printSummary() {
        AuthzScenario scenario = new AuthzScenario("test", "from_text", Map.of("text", "hello"), "pass");
        AuthzSampleMain.printSummary(List.of(scenario), List.of(PASS, FAIL, ERROR));
        String output = outContent.toString();
        assertTrue(output.contains("PASS"));
        assertTrue(output.contains("FAIL"));
        assertTrue(output.contains("ERROR"));
    }
}
