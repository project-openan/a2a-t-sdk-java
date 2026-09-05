package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;

/**
 * Loads shared scenario catalogs from one local prompt resource root.
 *
 * <p>Resources are resolved against an assembly-time snapshot of the local root: runtime reads never touch the
 * filesystem, so changes to the local files only take effect after the SDK is restarted.
 *
 * @since 2026-06
 */
public final class LocalFilePromptScenarioCatalogLoader {

    private final Map<String, String> snapshot;
    private final Path promptRootDir;

    public LocalFilePromptScenarioCatalogLoader(Map<String, String> snapshot, Path promptRootDir) {
        this.snapshot = snapshot;
        this.promptRootDir = promptRootDir;
    }

    public List<ScenarioDefinition> load(String language) {
        PathSegments.requireSimpleSegment(language, "Prompt scenario catalog language");
        String pathKey = "scenarios/" + language + "/scenarios.json";
        if (!snapshot.containsKey(pathKey)) {
            throw new ResourceNotFoundException(
                    "Prompt resource file does not exist.",
                    promptRootDir
                            .resolve("scenarios")
                            .resolve(language)
                            .resolve("scenarios.json")
                            .toString());
        }
        try {
            return PromptResourceJsonParser.parse(snapshot.get(pathKey), ScenarioCatalog.class)
                    .scenarios();
        } catch (JsonProcessingException exception) {
            throw ResourceReadErrors.readFailed(
                    promptRootDir
                            .resolve("scenarios")
                            .resolve(language)
                            .resolve("scenarios.json")
                            .toString(),
                    language,
                    exception);
        }
    }

    private record ScenarioCatalog(@JsonProperty("scenarios") List<ScenarioDefinition> scenarios) {
        ScenarioCatalog {
            scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        }
    }
}
