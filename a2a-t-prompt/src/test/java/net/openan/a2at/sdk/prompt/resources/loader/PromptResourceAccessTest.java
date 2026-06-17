package net.openan.a2at.sdk.prompt.resources.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptResourceAccessTest {

    @TempDir
    Path promptRootDir;

    @Test
    void createLocalFileAccessLoadsPromptsTemplatesScenariosAndSlotsFromSameRoot() throws IOException {
        write(promptRootDir.resolve("prompts").resolve("analysis").resolve("en").resolve("system.md"), "Local system prompt.");
        write(
                promptRootDir
                        .resolve("templates")
                        .resolve("incident_triage")
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
                promptRootDir.resolve("slots").resolve("incident_triage").resolve("en").resolve("slot.json"),
                """
                {
                  "required": ["service"],
                  "properties": {
                    "service": {"type": "string", "description": "Affected service name"}
                  }
                }
                """);

        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US","local_file", promptRootDir.toString()));

        assertFalse(access.classpath());
        assertEquals(promptRootDir, access.localRootDir());
        assertThrows(UnsupportedOperationException.class, access::classpathResourceLoader);
        assertEquals("Local system prompt.", access.loadPrompt("analysis", "en", "system.md"));
        assertEquals("Local template for {{service}}.", access.templateLoader().loadTemplate("incident_triage", "en"));
        List<ScenarioDefinition> scenarios = access.loadScenarios("en");
        PromptSlotSchema slotSchema = access.slotSchemaLoader().loadSlotSchema("incident_triage", "en");
        assertEquals("incident_triage", scenarios.get(0).scenarioCode());
        assertEquals("service", slotSchema.slotDefinitions().get(0).name());
        assertEquals(true, slotSchema.slotDefinitions().get(0).required());
    }

    @Test
    void localPromptResourceNotFoundIncludesResolvedPromptPath() {
        PromptResourceAccess access =
                PromptResourceAccess.create(new PromptRuntimeConfig("en-US", "local_file", promptRootDir.toString()));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> access.loadPrompt("analysis", "en", "missing.md"));

        assertEquals(
                promptRootDir.resolve("prompts").resolve("analysis").resolve("en").resolve("missing.md").toString(),
                exception.resourcePath());
    }

    @Test
    void unsupportedSourceTypeFailsFast() {
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> PromptResourceAccess.create(new PromptRuntimeConfig("en-US","database", promptRootDir.toString())));

        assertEquals("Unsupported prompt source type: database", exception.getMessage());
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
