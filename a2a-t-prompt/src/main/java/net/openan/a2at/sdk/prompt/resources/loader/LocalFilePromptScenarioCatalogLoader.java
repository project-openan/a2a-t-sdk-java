package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;

/**
 * Loads shared scenario catalogs from one local prompt resource root.
 *
 * <p>Resources are resolved against an assembly-time snapshot of the local root: runtime reads never touch the
 * filesystem, so changes to the local files only take effect after the SDK is restarted. A local {@code scenarios.json}
 * is no longer required: when it is missing, the loader falls back to the packaged classpath scenario catalog and
 * reports the first fallback for the resource path with a one-time warning.
 *
 * @since 2026-06
 */
public final class LocalFilePromptScenarioCatalogLoader {

    private final Map<String, String> snapshot;
    private final Path promptRootDir;
    private final ClasspathPromptScenarioCatalogLoader classpathLoader;
    private final Set<String> warnedFallbackPaths;

    public LocalFilePromptScenarioCatalogLoader(
            Map<String, String> snapshot,
            ClasspathPromptScenarioCatalogLoader classpathLoader,
            Path promptRootDir,
            Set<String> warnedFallbackPaths) {
        this.snapshot = snapshot;
        this.classpathLoader = classpathLoader;
        this.promptRootDir = promptRootDir;
        this.warnedFallbackPaths = warnedFallbackPaths;
    }

    public List<ScenarioDefinition> load(String language) {
        PathSegments.requireSimpleSegment(language, "Prompt scenario catalog language");
        String pathKey = "scenarios/" + language + "/scenarios.json";
        if (snapshot.containsKey(pathKey)) {
            return parse(snapshot.get(pathKey), pathKey, language);
        }
        BuiltinFallbackWarnings.warnOnce(
                warnedFallbackPaths, "prompt_resources/scenarios/" + language + "/scenarios.json");
        return classpathLoader.load(language);
    }

    private List<ScenarioDefinition> parse(String payload, String pathKey, String language) {
        try {
            return PromptResourceJsonParser.parse(payload, ScenarioCatalog.class)
                    .scenarios();
        } catch (JsonProcessingException exception) {
            throw ResourceReadErrors.readFailed(
                    promptRootDir.resolve(pathKey).toString(), language, exception);
        }
    }

    private record ScenarioCatalog(@JsonProperty("scenarios") List<ScenarioDefinition> scenarios) {
        ScenarioCatalog {
            scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        }
    }
}