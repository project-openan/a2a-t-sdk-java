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
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class PromptResourceAccessTest {

    @TempDir
    Path promptRootDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final Logger logger = (Logger) LoggerFactory.getLogger(PromptResourceAccess.class);

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void classpathModeIgnoresConfiguredLocalRootWithSingleWarning() {
        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "classpath", promptRootDir.toString()));

        assertTrue(access.classpath());
        List<String> warnings = warningMessages();
        assertEquals(1, warnings.size());
        assertContains(
                warnings.get(0), "prompt_resource_local_root_ignored", "root=" + promptRootDir, "source=classpath");
    }

    @Test
    void localFileModeFailsFastWhenLocalRootIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", null)));

        assertContains(exception.getMessage(), "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR");
    }

    @Test
    void localFileModeFailsFastWhenLocalRootIsBlank() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", "   ")));

        assertContains(exception.getMessage(), "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR");
    }

    @Test
    void localFileModeFailsFastWhenLocalRootDoesNotExist() {
        Path missing = promptRootDir.resolve("missing");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", missing.toString())));

        assertContains(exception.getMessage(), "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR");
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

        List<String> warnings = warningMessages();
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

    private List<String> warningMessages() {
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
