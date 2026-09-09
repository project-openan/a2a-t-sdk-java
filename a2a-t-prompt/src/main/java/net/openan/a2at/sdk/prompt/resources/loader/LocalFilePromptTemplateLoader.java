package net.openan.a2at.sdk.prompt.resources.loader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.resources.PathSegments;

/**
 * Loads shared prompt templates from one local prompt resource root.
 *
 * <p>Resources are resolved against an assembly-time snapshot of the local root: runtime reads never touch the
 * filesystem, so changes to the local files only take effect after the SDK is restarted.
 *
 * @since 2026-06
 */
public final class LocalFilePromptTemplateLoader implements PromptTemplateTextLoader {

    private final Map<String, String> snapshot;
    private final Path promptRootDir;
    private final List<String> templateTypes;

    public LocalFilePromptTemplateLoader(Map<String, String> snapshot, Path promptRootDir) {
        this.snapshot = snapshot;
        this.promptRootDir = promptRootDir;
        this.templateTypes = LocalFileResourceSnapshot.typeDirectories(snapshot, "templates");
    }

    @Override
    public String loadTemplate(String scenarioCode, String language) {
        PathSegments.requireSimpleRelativePath(scenarioCode, "Prompt template scenario code");
        PathSegments.requireSimpleSegment(language, "Prompt template language");
        String pathKey;
        if (scenarioCode.contains("/")) {
            pathKey = "templates/" + scenarioCode + "/" + language + "/template.md";
        } else {
            pathKey = null;
            for (String templateType : templateTypes) {
                String candidate = resolveBareCode(templateType, scenarioCode, language);
                if (snapshot.containsKey(candidate)) {
                    pathKey = candidate;
                    break;
                }
            }
        }
        if (pathKey == null || !snapshot.containsKey(pathKey)) {
            throw notFound(scenarioCode, language);
        }
        return snapshot.get(pathKey);
    }

    /**
     * Resolves a bare scenario code under one template type directory, preferring the {@code network-layer} domain
     * layout over the plain layout.
     */
    private String resolveBareCode(String templateType, String scenarioCode, String language) {
        String networkLayer =
                "templates/" + templateType + "/network-layer/" + scenarioCode + "/v1/" + language + "/template.md";
        if (snapshot.containsKey(networkLayer)) {
            return networkLayer;
        }
        return "templates/" + templateType + "/" + scenarioCode + "/v1/" + language + "/template.md";
    }

    private ResourceNotFoundException notFound(String scenarioCode, String language) {
        String pathHint = scenarioCode.contains("/")
                ? promptRootDir
                        .resolve("templates")
                        .resolve(scenarioCode)
                        .resolve(language)
                        .resolve("template.md")
                        .toString()
                : promptRootDir.resolve("templates") + "/*/network-layer/" + scenarioCode + "/v1/" + language
                        + "/template.md (or the layout without the network-layer segment)";
        return new ResourceNotFoundException("Prompt resource file does not exist.", pathHint);
    }
}
