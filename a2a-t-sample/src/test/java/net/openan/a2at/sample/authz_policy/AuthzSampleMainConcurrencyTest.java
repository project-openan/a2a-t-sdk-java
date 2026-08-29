package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ClientExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ServerExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioResult;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthzSampleMainConcurrencyTest {

    private static final String SLOT_EXTRACTION_SYSTEM = "/prompt_resources/prompts/slot_extraction/zh-CN/system.md";
    private static final String CONTENT_VALIDATION_SYSTEM =
            "/prompt_resources/prompts/content_validation/zh-CN/system.md";

    private static final String TEMPLATE_URI = StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI;

    private static final String ENV_CONTENT =
            "A2AT_LLM_PROVIDER=openai\nA2AT_LLM_MODEL=gpt-4\nA2AT_LLM_API_KEY=sk-test\n"
                    + "A2AT_LANGUAGE=zh-CN\nA2AT_PROMPT_SOURCE_TYPE=classpath\n"
                    + "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=\nA2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory\n";

    private static String loadResource(String path) {
        try (InputStream in = AuthzSampleMainConcurrencyTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }

    private static List<Map<String, String>> messagesWithSystem(String systemContent) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemContent));
        messages.add(Map.of("role", "user", "content", "dummy"));
        return messages;
    }

    private static AuthzScenario successScenario(String label) {
        return new AuthzScenario(
                label,
                "from_text",
                Map.of("text", "hello"),
                new AuthzExpected(
                        new ClientExpected(null, "prompt text", null), new ServerExpected("success", null, null)));
    }

    private static ScenarioOutcome successOutcome() {
        MetadataContent metadata = new MetadataContent("template-uri", "prompt text", "extension-uri");
        return new ScenarioOutcome(
                new ScenarioResult("success", true, null, List.of(), true, true, null, List.of()),
                metadata,
                new FilledParamData(Map.of()));
    }

    @Test
    void should_buildGenerator_withoutCapture_returnsNonNull(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, ENV_CONTENT);

        AuthzPromptGenerator generator = AuthzSampleMain.buildGenerator(envFile, TEMPLATE_URI, null);
        assertNotNull(generator);
    }

    @Test
    void should_buildValidator_withoutCapture_returnsNonNull(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, ENV_CONTENT);

        AuthzPromptValidator validator = AuthzSampleMain.buildValidator(envFile, null);
        assertNotNull(validator);
    }

    @Test
    void should_buildGenerator_withCapture_returnsNonNull(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, ENV_CONTENT);

        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        AuthzPromptGenerator generator = AuthzSampleMain.buildGenerator(envFile, TEMPLATE_URI, capture);
        assertNotNull(generator);
    }

    @Test
    void should_buildValidator_withCapture_returnsNonNull(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("test.env");
        Files.writeString(envFile, ENV_CONTENT);

        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        AuthzPromptValidator validator = AuthzSampleMain.buildValidator(envFile, capture);
        assertNotNull(validator);
    }

    @Test
    void should_writeReport_includeReasoningFields_whenReasoningEnabled(@TempDir Path tempDir) throws IOException {
        AuthzScenario s = successScenario("test");
        ScenarioOutcome outcome = successOutcome();

        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        String[] content = {"unused"};
        LLMClient fake = (messages, jsonSchema, temperature, maxTokens) ->
                new LLMResponse(content[0], "test-model", Map.of(), Map.of());
        LLMClient wrapped = capture.wrap(fake);
        capture.beginScenario("test");
        content[0] = "{\"slots\":{},\"reasoning\":\"client-reason\"}";
        wrapped.structured(messagesWithSystem(loadResource(SLOT_EXTRACTION_SYSTEM)), Map.of(), null, null);
        content[0] = "{\"semantic_verdict\":true,\"errors\":[],\"params\":{},\"reasoning\":\"server-reason\"}";
        wrapped.structured(messagesWithSystem(loadResource(CONTENT_VALIDATION_SYSTEM)), Map.of(), null, null);
        capture.endScenario();

        Path reportPath = AuthzSampleMain.writeReport(
                List.of(s), List.of(outcome), tempDir, 8, 42.5, "sample/scenarios.json", true, capture);

        String reportContent = Files.readString(reportPath);
        assertTrue(reportContent.contains("\"meta\""));
        assertTrue(reportContent.contains("\"workers\""));
        assertTrue(reportContent.contains("\"wallTimeSeconds\""));
        assertTrue(reportContent.contains("\"scenariosResource\""));
        assertTrue(reportContent.contains("\"reasoning\""));
        assertTrue(reportContent.contains("\"client_reasoning\""));
        assertTrue(reportContent.contains("\"server_reasoning\""));
        assertTrue(reportContent.contains("client-reason"));
        assertTrue(reportContent.contains("server-reason"));
    }

    @Test
    void should_writeReport_omitReasoningFields_whenReasoningDisabled(@TempDir Path tempDir) throws IOException {
        AuthzScenario s = successScenario("test");
        ScenarioOutcome outcome = successOutcome();

        Path reportPath = AuthzSampleMain.writeReport(
                List.of(s), List.of(outcome), tempDir, 8, 10.0, "sample/scenarios.json", false, null);

        String reportContent = Files.readString(reportPath);
        assertTrue(reportContent.contains("\"meta\""));
        assertTrue(reportContent.contains("\"reasoning\""));
        assertFalse(reportContent.contains("client_reasoning"));
        assertFalse(reportContent.contains("server_reasoning"));
    }

    @Test
    void should_writeReport_includeClientReasoningOnly_whenServerMissing(@TempDir Path tempDir) throws IOException {
        AuthzScenario s = new AuthzScenario(
                "a-only",
                "from_text",
                Map.of("text", "hello"),
                new AuthzExpected(new ClientExpected("slot.rule_violation", null, null), null));
        ScenarioOutcome outcome = new ScenarioOutcome(
                new ScenarioResult("slot.rule_violation", false, null, List.of(), null, null, null, List.of()),
                null,
                null);

        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        String[] content = {"unused"};
        LLMClient fake = (messages, jsonSchema, temperature, maxTokens) ->
                new LLMResponse(content[0], "test-model", Map.of(), Map.of());
        LLMClient wrapped = capture.wrap(fake);
        capture.beginScenario("a-only");
        content[0] = "{\"slots\":{},\"reasoning\":\"only-client\"}";
        wrapped.structured(messagesWithSystem(loadResource(SLOT_EXTRACTION_SYSTEM)), Map.of(), null, null);
        capture.endScenario();

        Path reportPath = AuthzSampleMain.writeReport(
                List.of(s), List.of(outcome), tempDir, 8, 5.0, "sample/scenarios.json", true, capture);

        String reportContent = Files.readString(reportPath);
        assertTrue(reportContent.contains("\"client_reasoning\""));
        assertTrue(reportContent.contains("only-client"));
        assertFalse(reportContent.contains("server_reasoning"));
    }

    @Test
    void should_writeReport_useTimestampedFilename(@TempDir Path tempDir) throws IOException {
        AuthzScenario s = successScenario("test");
        ScenarioOutcome outcome = successOutcome();

        Path reportPath = AuthzSampleMain.writeReport(
                List.of(s), List.of(outcome), tempDir, 8, 10.0, "sample/scenarios.json", false, null);

        String fileName = reportPath.getFileName().toString();
        assertTrue(fileName.startsWith("authz-report-"));
        assertTrue(fileName.endsWith(".json"));
        String timestamp = fileName.substring("authz-report-".length(), fileName.length() - ".json".length());
        assertEquals(15, timestamp.length());
    }

    @Test
    void should_writeReport_notOverwrite_WhenCalledTwice(@TempDir Path tempDir)
            throws IOException, InterruptedException {
        AuthzScenario s = successScenario("test");
        ScenarioOutcome outcome = successOutcome();

        Path report1 = AuthzSampleMain.writeReport(
                List.of(s), List.of(outcome), tempDir, 8, 10.0, "sample/scenarios.json", false, null);
        Thread.sleep(1100);
        Path report2 = AuthzSampleMain.writeReport(
                List.of(s), List.of(outcome), tempDir, 8, 10.0, "sample/scenarios.json", false, null);

        assertNotEquals(report1, report2);
        assertTrue(Files.exists(report1));
        assertTrue(Files.exists(report2));
    }
}
