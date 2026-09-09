package net.openan.a2at.sdk.prompt.resources.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFilePromptLoadersTest {

    @TempDir
    Path promptRootDir;

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
                new LocalFilePromptScenarioCatalogLoader(snapshot(), promptRootDir).load("en");

        assertEquals(2, scenarios.size());
        assertEquals("incident_triage", scenarios.get(0).scenarioCode());
        assertEquals("Incident Triage", scenarios.get(0).scenarioName());
    }

    @Test
    void loadScenarioCatalogTreatsMissingScenariosArrayAsEmptyCatalog() throws IOException {
        write(promptRootDir.resolve("scenarios").resolve("en").resolve("scenarios.json"), "{}");

        assertEquals(List.of(), new LocalFilePromptScenarioCatalogLoader(snapshot(), promptRootDir).load("en"));
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
                new LocalFilePromptTemplateLoader(snapshot(), promptRootDir).loadTemplate("incident_triage", "en");

        assertEquals(
                """
                # Incident Triage

                Severity: {{severity}}
                Summary: {{summary}}
                """,
                template);
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

        PromptSlotSchema schema =
                new LocalFilePromptSlotSchemaLoader(snapshot(), promptRootDir).loadSlotSchema("incident_triage", "en");

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
    void missingTemplateIncludesResolvedLocalPathInResourceNotFoundException() {
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class, () -> new LocalFilePromptTemplateLoader(snapshot(), promptRootDir)
                        .loadTemplate("incident_triage", "en"));

        String expected = (promptRootDir.resolve("templates").toString()
                        + "/*/network-layer/incident_triage/v1/en/template.md"
                        + " (or the layout without the network-layer segment)")
                .replace('\\', '/');
        assertEquals(expected, exception.resourcePath().replace('\\', '/'));
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

        A2ATError exception =
                assertThrows(A2ATError.class, () -> new LocalFilePromptSlotSchemaLoader(snapshot(), promptRootDir)
                        .loadSlotSchema("incident_triage", "en"));

        assertEquals("infra.resource_read_failed", exception.getCode());
        assertTrue(exception.getMessage().startsWith("Failed to read resource '"));
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
        assertThrows(IllegalArgumentException.class, () -> new LocalFilePromptTemplateLoader(snapshot(), promptRootDir)
                .loadTemplate("../etc/passwd", "en"));
        assertThrows(IllegalArgumentException.class, () -> new LocalFilePromptTemplateLoader(snapshot(), promptRootDir)
                .loadTemplate("Task-T//network-layer/x", "en"));
        assertThrows(IllegalArgumentException.class, () -> new LocalFilePromptTemplateLoader(snapshot(), promptRootDir)
                .loadTemplate("incident_triage", "en/../admin"));
    }

    @Test
    void loadSlotSchemaRejectsNonSimpleLanguage() {
        assertThrows(
                IllegalArgumentException.class, () -> new LocalFilePromptSlotSchemaLoader(snapshot(), promptRootDir)
                        .loadSlotSchema("incident_triage", "../en"));
        assertThrows(
                IllegalArgumentException.class, () -> new LocalFilePromptSlotSchemaLoader(snapshot(), promptRootDir)
                        .loadSlotSchema("Task-T/network-layer/..", "en"));
    }

    @Test
    void loadScenarioCatalogRejectsNonSimpleLanguage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalFilePromptScenarioCatalogLoader(snapshot(), promptRootDir).load("en/../admin"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalFilePromptScenarioCatalogLoader(snapshot(), promptRootDir).load("   "));
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

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
