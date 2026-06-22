package net.openan.a2at.sdk.server.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.openan.a2at.sdk.server.model.PromptTemplateDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileServerPromptTemplateLoaderTest {

    @TempDir
    Path promptRootDir;

    @Test
    void loadReadsSlotDefinitionsUsingPromptPackagePublicLoaders() {
        LocalFileServerPromptTemplateLoader loader = new LocalFileServerPromptTemplateLoader(
                Path.of("..", "a2a-t-resources", "src", "main", "resources", "prompt_resources"));

        PromptTemplateDefinition definition = loader.load("energy_saving", "zh-CN");

        assertEquals("energy_saving", definition.scenarioCode());
        assertEquals(4, definition.slotDefinitions().size());
        assertEquals(false, definition.slotDefinitions().get(0).required());
    }

    @Test
    void loadAllScansLocalTemplateDirectoriesAndLoadsDefinitions() throws IOException {
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
        write(
                promptRootDir
                        .resolve("slots")
                        .resolve("incident_triage")
                        .resolve("en")
                        .resolve("slot.json"),
                """
                {
                  "required": ["severity"],
                  "properties": {
                    "severity": {
                      "type": "string",
                      "description": "Incident severity"
                    },
                    "summary": {
                      "type": "string"
                    }
                  }
                }
                """);

        var definitions = new LocalFileServerPromptTemplateLoader(promptRootDir).loadAll("en");

        assertEquals(1, definitions.size());
        PromptTemplateDefinition definition = definitions.get(0);
        assertEquals("incident_triage", definition.scenarioCode());
        assertEquals("en", definition.language());
        assertEquals(2, definition.slotDefinitions().size());
        assertEquals("severity", definition.slotDefinitions().get(0).name());
        assertEquals(true, definition.slotDefinitions().get(0).required());
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
