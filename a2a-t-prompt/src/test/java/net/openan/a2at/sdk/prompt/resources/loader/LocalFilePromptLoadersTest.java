package net.openan.a2at.sdk.prompt.resources.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class LocalFilePromptLoadersTest {

    private final ClasspathPromptResourceLoader resourceLoader = new ClasspathPromptResourceLoader();

    @TempDir
    Path promptRootDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final Logger logger = (Logger) LoggerFactory.getLogger(BuiltinFallbackWarnings.class);

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void loadScenarioCatalogMapsJacksonAnnotatedRecords() throws IOException {
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
                    },
                    {
                      "scenario_code": "release_review",
                      "scenario_name": "Release Review",
                      "description": "Review a release candidate before rollout.",
                      "example": "Check the payment service release."
                    }
                  ]
                }
                """);

        List<ScenarioDefinition> scenarios =
                new LocalFilePromptScenarioCatalogLoader(snapshot(), scenarioLoader(), promptRootDir, warnedPaths())
                        .load("en");

        assertEquals(2, scenarios.size());
        assertEquals("incident_triage", scenarios.get(0).scenarioCode());
        assertEquals("Incident Triage", scenarios.get(0).scenarioName());
    }

    @Test
    void loadScenarioCatalogTreatsMissingScenariosArrayAsEmptyCatalog() throws IOException {
        write(promptRootDir.resolve("scenarios").resolve("en").resolve("scenarios.json"), "{}");

        assertEquals(
                List.of(),
                new LocalFilePromptScenarioCatalogLoader(snapshot(), scenarioLoader(), promptRootDir, warnedPaths())
                        .load("en"));
    }

    @Test
    void loadTemplateReadsExactLocalMarkdownText() throws IOException {
        write(
                promptRootDir
                        .resolve("templates")
                        .resolve("Task-T")
                        .resolve("network-layer")
                        .resolve("incident_triage")
                        .resolve("v1")
                        .resolve("en")
                        .resolve("template.md"),
                """
                # Incident Triage

                Severity: {{severity}}
                Summary: {{summary}}
                """);

        String template =
                new LocalFilePromptTemplateLoader(snapshot(), templateLoader(), warnedPaths()).loadTemplate("incident_triage", "en");

        assertEquals(
                """
                # Incident Triage

                Severity: {{severity}}
                Summary: {{summary}}
                """,
                template);
    }

    @Test
    void loadTemplateResolvesPathFormScenarioCodeFromLocalSnapshot() throws IOException {
        write(
                promptRootDir
                        .resolve("templates")
                        .resolve("Task-T/network-layer/ran-energy-saving/v1")
                        .resolve("en-US")
                        .resolve("template.md"),
                "Local path-form template body.");

        String template = new LocalFilePromptTemplateLoader(snapshot(), templateLoader(), warnedPaths())
                .loadTemplate("Task-T/network-layer/ran-energy-saving/v1", "en-US");

        assertEquals("Local path-form template body.", template);
        assertEquals(0, warningMessages().size(), "a local path-form hit must not fall back to the builtin template");
    }

    @Test
    void loadSlotSchemaResolvesPathFormScenarioCodeFromLocalSnapshot() throws IOException {
        write(
                promptRootDir
                        .resolve("slots")
                        .resolve("Task-T/network-layer/ran-energy-saving/v1")
                        .resolve("en-US")
                        .resolve("slot.json"),
                """
                {
                  "required": ["service"],
                  "properties": {
                    "service": {"type": "string", "description": "Affected service name"}
                  }
                }
                """);

        PromptSlotSchema schema = new LocalFilePromptSlotSchemaLoader(snapshot(), slotLoader(), promptRootDir, warnedPaths())
                .loadSlotSchema("Task-T/network-layer/ran-energy-saving/v1", "en-US");

        assertEquals("Task-T/network-layer/ran-energy-saving/v1", schema.scenarioCode());
        assertEquals(1, schema.slotDefinitions().size());
        assertEquals("service", schema.slotDefinitions().get(0).name());
        assertEquals(0, warningMessages().size(), "a local path-form hit must not fall back to the builtin schema");
    }

    @Test
    void loadSlotSchemaMapsJacksonAnnotatedRecords() throws IOException {
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
                  "required": ["severity", "service"],
                  "properties": {
                    "service": {
                      "type": "string",
                      "pattern": "^[a-z0-9-]+$",
                      "description": "Affected service name"
                    },
                    "severity": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 5,
                      "enum": ["1", "2", "3", "4", "5"],
                      "x-a2at-value-constraint": "Severity from 1 to 5"
                    },
                    "note": null
                  }
                }
                """);

        PromptSlotSchema schema = new LocalFilePromptSlotSchemaLoader(snapshot(), slotLoader(), promptRootDir, warnedPaths())
                .loadSlotSchema("incident_triage", "en");

        assertEquals("incident_triage", schema.scenarioCode());
        assertEquals(3, schema.slotDefinitions().size());
        assertEquals("service", schema.slotDefinitions().get(0).name());
        assertEquals(true, schema.slotDefinitions().get(0).required());
        assertEquals("^[a-z0-9-]+$", schema.slotDefinitions().get(0).pattern());
        assertEquals(
                List.of("1", "2", "3", "4", "5"),
                schema.slotDefinitions().get(1).allowedValues());
        assertEquals("Severity from 1 to 5", schema.slotDefinitions().get(1).valueConstraint());
        assertEquals("note", schema.slotDefinitions().get(2).name());
    }

    @Test
    void loadTemplateFallsBackToBuiltinWhenMissingLocally() {
        Set<String> warnedPaths = warnedPaths();

        String template =
                new LocalFilePromptTemplateLoader(snapshot(), templateLoader(), warnedPaths)
                        .loadTemplate("ran-energy-saving", "en-US");

        assertEquals(templateLoader().loadTemplate("ran-energy-saving", "en-US"), template);
        List<String> warnings = warningMessages();
        assertEquals(1, warnings.size());
        assertTrueWarning(warnings.get(0), "prompt_resource_builtin_fallback path=prompt_resources/templates/ran-energy-saving/en-US/template.md");
    }

    @Test
    void loadSlotSchemaFallsBackToBuiltinWhenMissingLocally() {
        Set<String> warnedPaths = warnedPaths();

        PromptSlotSchema schema = new LocalFilePromptSlotSchemaLoader(snapshot(), slotLoader(), promptRootDir, warnedPaths)
                .loadSlotSchema("ran-energy-saving", "en-US");

        assertEquals(slotLoader().loadSlotSchema("ran-energy-saving", "en-US").slotDefinitions(), schema.slotDefinitions());
        assertEquals(1, warningMessages().size());
    }

    @Test
    void loadScenarioCatalogFallsBackToBuiltinWhenMissingLocally() {
        Set<String> warnedPaths = warnedPaths();

        List<ScenarioDefinition> scenarios =
                new LocalFilePromptScenarioCatalogLoader(snapshot(), scenarioLoader(), promptRootDir, warnedPaths)
                        .load("en-US");

        assertEquals(scenarioLoader().load("en-US").size(), scenarios.size());
        assertEquals(1, warningMessages().size());
    }

    @Test
    void builtinFallbackWarnsOnlyOncePerResourcePath() {
        Set<String> warnedPaths = warnedPaths();
        LocalFilePromptTemplateLoader loader = new LocalFilePromptTemplateLoader(snapshot(), templateLoader(), warnedPaths);

        loader.loadTemplate("ran-energy-saving", "en-US");
        loader.loadTemplate("ran-energy-saving", "en-US");

        assertEquals(1, warningMessages().size(), "the same resource path must warn about its builtin fallback only once");
    }

    @Test
    void missingTemplateFallsBackToClasspathThenThrowsNotFound() {
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> new LocalFilePromptTemplateLoader(snapshot(), templateLoader(), warnedPaths())
                        .loadTemplate("incident_triage", "en-US"));

        assertEquals(
                "prompt_resources/templates/*/network-layer/incident_triage/v1/en-US/template.md"
                        + " (or the layout without the network-layer segment)",
                exception.resourcePath());
    }

    @Test
    void malformedLocalSlotSchemaIsWrappedAsA2ATError() throws IOException {
        write(
                promptRootDir
                        .resolve("slots")
                        .resolve("Task-T")
                        .resolve("network-layer")
                        .resolve("incident_triage")
                        .resolve("v1")
                        .resolve("en")
                        .resolve("slot.json"),
                "{ \"required\": [\"severity\"], \"properties\": ");

        A2ATError exception = assertThrows(
                A2ATError.class,
                () -> new LocalFilePromptSlotSchemaLoader(snapshot(), slotLoader(), promptRootDir, warnedPaths())
                        .loadSlotSchema("incident_triage", "en"));

        assertEquals("infra.resource_read_failed", exception.getCode());
        assertEquals(0, warningMessages().size(), "a malformed local slot schema must fail-fast without a builtin fallback WARN");
    }

    @Test
    void snapshotFreezesResourcesAgainstRuntimeFileChanges() throws IOException {
        write(
                promptRootDir
                        .resolve("templates")
                        .resolve("Task-T")
                        .resolve("network-layer")
                        .resolve("incident_triage")
                        .resolve("v1")
                        .resolve("en")
                        .resolve("template.md"),
                "Original template {{service}}.");
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

        PromptResourceAccess access = createAccess();

        Files.writeString(
                promptRootDir
                        .resolve("templates")
                        .resolve("Task-T")
                        .resolve("network-layer")
                        .resolve("incident_triage")
                        .resolve("v1")
                        .resolve("en")
                        .resolve("template.md"),
                "Mutated template.");
        Files.writeString(
                promptRootDir
                        .resolve("slots")
                        .resolve("Task-T")
                        .resolve("network-layer")
                        .resolve("incident_triage")
                        .resolve("v1")
                        .resolve("en")
                        .resolve("slot.json"),
                "{\"properties\":{}}");
        write(
                promptRootDir.resolve("scenarios").resolve("en").resolve("scenarios.json"),
                """
                {
                  "scenarios": [
                    {
                      "scenario_code": "mutated",
                      "scenario_name": "Mutated",
                      "description": "Mutated after snapshot.",
                      "example": "Mutated example."
                    }
                  ]
                }
                """);

        assertEquals("Original template {{service}}.", access.templateLoader().loadTemplate("incident_triage", "en"));
        assertEquals(
                "service",
                access.slotSchemaLoader()
                        .loadSlotSchema("incident_triage", "en")
                        .slotDefinitions()
                        .get(0)
                        .name());
        assertEquals("incident_triage", access.loadScenarios("en").get(0).scenarioCode());
    }

    @Test
    void loadTemplateRejectsTraversalOrBlankScenarioPathSegments() {
        assertThrows(
                IllegalArgumentException.class, () -> new LocalFilePromptTemplateLoader(snapshot(), templateLoader(), warnedPaths())
                        .loadTemplate("../etc/passwd", "en"));
        assertThrows(
                IllegalArgumentException.class, () -> new LocalFilePromptTemplateLoader(snapshot(), templateLoader(), warnedPaths())
                        .loadTemplate("Task-T//network-layer/x", "en"));
        assertThrows(
                IllegalArgumentException.class, () -> new LocalFilePromptTemplateLoader(snapshot(), templateLoader(), warnedPaths())
                        .loadTemplate("incident_triage", "en/../admin"));
    }

    @Test
    void loadSlotSchemaRejectsNonSimpleLanguage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalFilePromptSlotSchemaLoader(snapshot(), slotLoader(), promptRootDir, warnedPaths())
                        .loadSlotSchema("incident_triage", "../en"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalFilePromptSlotSchemaLoader(snapshot(), slotLoader(), promptRootDir, warnedPaths())
                        .loadSlotSchema("Task-T/network-layer/..", "en"));
    }

    @Test
    void loadScenarioCatalogRejectsNonSimpleLanguage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalFilePromptScenarioCatalogLoader(snapshot(), scenarioLoader(), promptRootDir, warnedPaths())
                        .load("en/../admin"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalFilePromptScenarioCatalogLoader(snapshot(), scenarioLoader(), promptRootDir, warnedPaths())
                        .load("   "));
    }

    @Test
    void localSnapshotKeepsPromptsOnClasspath() throws IOException {
        write(
                promptRootDir
                        .resolve("prompts")
                        .resolve("scenario_recognition")
                        .resolve("en-US")
                        .resolve("system.md"),
                "Local prompt copy.");

        PromptResourceAccess access = createAccess();

        assertFalse(access.classpath());
        assertNotEquals("Local prompt copy.", access.loadPrompt("scenario_recognition", "en-US", "system.md"));
    }

    private PromptResourceAccess createAccess() {
        return PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));
    }

    private Map<String, String> snapshot() {
        return LocalFileResourceSnapshot.capture(promptRootDir);
    }

    private ClasspathPromptTemplateLoader templateLoader() {
        return new ClasspathPromptTemplateLoader(resourceLoader);
    }

    private ClasspathPromptSlotSchemaLoader slotLoader() {
        return new ClasspathPromptSlotSchemaLoader(resourceLoader);
    }

    private ClasspathPromptScenarioCatalogLoader scenarioLoader() {
        return new ClasspathPromptScenarioCatalogLoader(resourceLoader);
    }

    private static Set<String> warnedPaths() {
        return ConcurrentHashMap.newKeySet();
    }

    private List<String> warningMessages() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static void assertTrueWarning(String message, String... fragments) {
        for (String fragment : fragments) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    message.contains(fragment), "expected [" + message + "] to contain [" + fragment + "]");
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}