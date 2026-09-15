package net.openan.a2at.sdk.prompt.resources.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

class PromptResourceAccessTest {

    @TempDir
    Path promptRootDir;

    private final ListAppender<ILoggingEvent> accessAppender = new ListAppender<>();

    private final Logger accessLogger = (Logger) LoggerFactory.getLogger(PromptResourceAccess.class);

    private final ListAppender<ILoggingEvent> fallbackAppender = new ListAppender<>();

    private final Logger fallbackLogger = (Logger) LoggerFactory.getLogger(BuiltinFallbackWarnings.class);

    @BeforeEach
    void attachAppender() {
        accessAppender.start();
        accessLogger.addAppender(accessAppender);
        fallbackAppender.start();
        fallbackLogger.addAppender(fallbackAppender);
    }

    @AfterEach
    void detachAppender() {
        accessLogger.detachAppender(accessAppender);
        fallbackLogger.detachAppender(fallbackAppender);
        accessAppender.stop();
        fallbackAppender.stop();
    }

    @Test
    void classpathModeIgnoresConfiguredLocalRootWithSingleWarning() {
        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "classpath", promptRootDir.toString()));

        assertTrue(access.classpath());
        List<String> warnings = warningMessages(accessAppender);
        assertEquals(1, warnings.size());
        assertContains(
                warnings.get(0), "prompt_resource_local_root_ignored", "root=" + promptRootDir, "source=classpath");
    }

    @ParameterizedTest(name = "local-file mode fails fast for an invalid root [{0}]")
    @MethodSource("invalidLocalRoots")
    void localFileModeFailsFastForInvalidRoot(String localRoot) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", localRoot)));

        assertContains(exception.getMessage(), "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR");
    }

    static Stream<Arguments> invalidLocalRoots() {
        Path missing = Path.of(System.getProperty("java.io.tmpdir"), "a2a-t-prompt", "missing-" + System.nanoTime());
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of("   "),
                Arguments.of(missing.toString()));
    }

    @Test
    void localFileModeLoadsPromptsFromClasspath() throws IOException {
        write(
                promptRootDir
                        .resolve("prompts")
                        .resolve("scenario_recognition")
                        .resolve("en-US")
                        .resolve("system.md"),
                "Local prompt copy.");

        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));

        assertFalse(access.classpath());
        String prompt = access.loadPrompt("scenario_recognition", "en-US", "system.md");
        assertTrue(prompt.startsWith("You are a scenario recognition agent."));
        assertFalse(prompt.equals("Local prompt copy."));
    }

    @Test
    void localFileModeLoadsBusinessContentFromLocalRoot() throws IOException {
        write(
                promptRootDir
                        .resolve("templates")
                        .resolve("Task-T")
                        .resolve("network-layer")
                        .resolve("incident_triage")
                        .resolve("v1")
                        .resolve("en")
                        .resolve("template.md"),
                "Local template for {{service}}.");
        write(
                promptRootDir.resolve("scenarios").resolve("en").resolve("scenarios.json"),
                """
                {
                  "scenarios": [
                    {
                      "scenario_code": "incident_triage",
                      "scenario_name": "Incident Triage",
                      "description": "Classify and route a production incident.",
                      "example": "Investigate elevated API latency."
                    }
                  ]
                }
                """);
        write(
                promptRootDir
                        .resolve("slots")
                        .resolve("Task-T")
                        .resolve("network-layer")
                        .resolve("incident_triage")
                        .resolve("v1")
                        .resolve("en")
                        .resolve("slot.json"),
                """
                {
                  "required": ["service"],
                  "properties": {
                    "service": {"type": "string", "description": "Affected service name"}
                  }
                }
                """);

        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));

        assertFalse(access.classpath());
        assertEquals(promptRootDir, access.localRootDir());
        assertEquals("Local template for {{service}}.", access.templateLoader().loadTemplate("incident_triage", "en"));
        List<ScenarioDefinition> scenarios = access.loadScenarios("en");
        PromptSlotSchema slotSchema = access.slotSchemaLoader().loadSlotSchema("incident_triage", "en");
        assertEquals("incident_triage", scenarios.get(0).scenarioCode());
        assertEquals("service", slotSchema.slotDefinitions().get(0).name());
        assertEquals(true, slotSchema.slotDefinitions().get(0).required());
    }

    @Test
    void localFileModeFallsBackToBuiltinTemplateWhenMissingLocally() {
        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));

        String template = access.templateLoader().loadTemplate("ran-energy-saving", "en-US");

        assertTrue(template.startsWith("## Operation Type"), "the built-in template should be loaded on fallback");
        assertTrue(template.contains("{{operation_type}}"), "the built-in template should carry its slot placeholders");
        List<String> warnings = warningMessages(fallbackAppender);
        assertEquals(1, warnings.size());
        assertContains(
                warnings.get(0),
                "prompt_resource_builtin_fallback",
                "path=prompt_resources/templates/*/network-layer/ran-energy-saving/v1/en-US/template.md",
                "source=classpath");
    }

    @Test
    void localFileModeFallsBackToBuiltinSlotSchemaWhenMissingLocally() {
        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));

        PromptSlotSchema schema = access.slotSchemaLoader().loadSlotSchema("ran-energy-saving", "en-US");

        assertEquals("ran-energy-saving", schema.scenarioCode());
        assertFalse(schema.slotDefinitions().isEmpty());
        List<String> warnings = warningMessages(fallbackAppender);
        assertEquals(1, warnings.size());
        assertContains(
                warnings.get(0),
                "prompt_resource_builtin_fallback",
                "path=prompt_resources/slots/*/network-layer/ran-energy-saving/v1/en-US/slot.json",
                "source=classpath");
    }

    @Test
    void localFileModeFallsBackToBuiltinScenarioCatalogWhenMissingLocally() {
        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));

        List<ScenarioDefinition> scenarios = access.loadScenarios("en-US");

        assertFalse(scenarios.isEmpty(), "a missing local scenarios.json must fall back to the built-in scenario catalog");
        List<String> warnings = warningMessages(fallbackAppender);
        assertEquals(1, warnings.size());
        assertContains(
                warnings.get(0),
                "prompt_resource_builtin_fallback",
                "path=prompt_resources/scenarios/en-US/scenarios.json",
                "source=classpath");
    }

    @Test
    void builtinFallbackWarnsOnlyOncePerResourcePathAcrossLoaderInstances() {
        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));

        access.templateLoader().loadTemplate("ran-energy-saving", "en-US");
        access.templateLoader().loadTemplate("ran-energy-saving", "en-US");

        assertEquals(1, warningMessages(fallbackAppender).size(), "the shared dedup set must warn only once per path");
    }

    @Test
    void localFileModeIgnoresUnsupportedLocalDirectoriesWithSingleWarning() throws IOException {
        write(
                promptRootDir
                        .resolve("prompts")
                        .resolve("scenario_recognition")
                        .resolve("en-US")
                        .resolve("system.md"),
                "Local prompt copy.");
        write(
                promptRootDir
                        .resolve("templates")
                        .resolve("Negotiation-T")
                        .resolve("information-negotiation")
                        .resolve("propose")
                        .resolve("v1")
                        .resolve("en-US")
                        .resolve("template.md"),
                "Local negotiation template copy.");
        write(promptRootDir.resolve("negotiation-vocabulary").resolve("en-US").resolve("vocabulary.json"), "{}");

        PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));

        List<String> warnings = warningMessages(accessAppender);
        assertEquals(1, warnings.size());
        assertContains(
                warnings.get(0),
                "prompt_resource_local_directories_ignored",
                "prompts",
                "templates/Negotiation-T",
                "negotiation-vocabulary",
                "reason=classpath_fixed");
    }

    @Test
    void localFileModePromptLoadUsesClasspathPathForMissingResource() {
        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));

        ResourceNotFoundException exception =
                assertThrows(ResourceNotFoundException.class, () -> access.loadPrompt("analysis", "en", "missing.md"));

        assertEquals("prompt_resources/prompts/analysis/en/missing.md", exception.resourcePath());
    }

    @Test
    void unsupportedSourceTypeFailsFast() {
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> PromptResourceAccess.create(
                        new PromptRuntimeConfig("en-US", "database", promptRootDir.toString())));

        assertEquals("Unsupported prompt source type: database", exception.getMessage());
    }

    private static List<String> warningMessages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static void assertContains(String message, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), "expected [" + message + "] to contain [" + fragment + "]");
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}