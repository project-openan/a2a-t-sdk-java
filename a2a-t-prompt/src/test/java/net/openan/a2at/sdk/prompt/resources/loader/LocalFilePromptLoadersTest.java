package net.openan.a2at.sdk.prompt.resources.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;
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

        List<ScenarioDefinition> scenarios = new LocalFilePromptScenarioCatalogLoader(promptRootDir).load("en");

        assertEquals(2, scenarios.size());
        assertEquals("incident_triage", scenarios.get(0).scenarioCode());
        assertEquals("Incident Triage", scenarios.get(0).scenarioName());
    }

    @Test
    void loadScenarioCatalogTreatsMissingScenariosArrayAsEmptyCatalog() throws IOException {
        write(promptRootDir.resolve("scenarios").resolve("en").resolve("scenarios.json"), "{}");

        assertEquals(List.of(), new LocalFilePromptScenarioCatalogLoader(promptRootDir).load("en"));
    }

    @Test
    void loadTemplateReadsExactLocalMarkdownText() throws IOException {
        write(
                promptRootDir
                        .resolve("templates")
                        .resolve("incident_triage")
                        .resolve("en")
                        .resolve("template.md"),
                """
                # Incident Triage

                Severity: {{severity}}
                Summary: {{summary}}
                """);

        String template = new LocalFilePromptTemplateLoader(promptRootDir).loadTemplate("incident_triage", "en");

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
                promptRootDir.resolve("slots").resolve("incident_triage").resolve("en").resolve("slot.json"),
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
                new LocalFilePromptSlotSchemaLoader(promptRootDir).loadSlotSchema("incident_triage", "en");

        assertEquals("incident_triage", schema.scenarioCode());
        assertEquals(3, schema.slotDefinitions().size());
        assertEquals("service", schema.slotDefinitions().get(0).name());
        assertEquals(true, schema.slotDefinitions().get(0).required());
        assertEquals("^[a-z0-9-]+$", schema.slotDefinitions().get(0).pattern());
        assertEquals(List.of("1", "2", "3", "4", "5"), schema.slotDefinitions().get(1).allowedValues());
        assertEquals("Severity from 1 to 5", schema.slotDefinitions().get(1).description());
        assertEquals("note", schema.slotDefinitions().get(2).name());
    }

    @Test
    void missingTemplateIncludesResolvedLocalPathInResourceNotFoundException() {
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> new LocalFilePromptTemplateLoader(promptRootDir).loadTemplate("incident_triage", "en"));

        assertEquals(
                promptRootDir
                        .resolve("templates")
                        .resolve("incident_triage")
                        .resolve("en")
                        .resolve("template.md")
                        .toString(),
                exception.resourcePath());
    }

    @Test
    void malformedLocalSlotSchemaIsWrappedAsSdkException() throws IOException {
        write(
                promptRootDir.resolve("slots").resolve("incident_triage").resolve("en").resolve("slot.json"),
                "{ \"required\": [\"severity\"], \"properties\": ");

        SdkException exception = assertThrows(
                SdkException.class,
                () -> new LocalFilePromptSlotSchemaLoader(promptRootDir).loadSlotSchema("incident_triage", "en"));

        assertEquals(
                "Failed to read slot schema resource: "
                        + promptRootDir.resolve("slots").resolve("incident_triage").resolve("en").resolve("slot.json"),
                exception.getMessage());
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
