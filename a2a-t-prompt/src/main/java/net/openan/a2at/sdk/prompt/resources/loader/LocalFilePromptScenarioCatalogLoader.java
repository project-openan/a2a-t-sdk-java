package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;

/**
 * Loads shared scenario catalogs from one local prompt resource root.
 *
 * @since 2026-06
 */
public final class LocalFilePromptScenarioCatalogLoader {

    private final Path promptRootDir;

    public LocalFilePromptScenarioCatalogLoader(Path promptRootDir) {
        this.promptRootDir = promptRootDir;
    }

    public List<ScenarioDefinition> load(String language) {
        Path catalogPath = promptRootDir.resolve("scenarios").resolve(language).resolve("scenarios.json");
        if (!Files.exists(catalogPath)) {
            throw new ResourceNotFoundException("Prompt resource file does not exist.", catalogPath.toString());
        }
        try {
            return PromptResourceJsonParser.parse(Files.readString(catalogPath), ScenarioCatalog.class).scenarios();
        } catch (IOException exception) {
            throw new SdkException("Failed to read scenario catalog: " + catalogPath, exception);
        }
    }

    private record ScenarioCatalog(@JsonProperty("scenarios") List<ScenarioDefinition> scenarios) {
        ScenarioCatalog {
            scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        }
    }
}
