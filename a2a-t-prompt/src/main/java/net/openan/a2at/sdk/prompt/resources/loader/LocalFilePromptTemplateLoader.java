package net.openan.a2at.sdk.prompt.resources.loader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.openan.a2at.sdk.core.resources.PathSegments;

public final class LocalFilePromptTemplateLoader implements PromptTemplateTextLoader {

    private final Map<String, String> snapshot;
    private final ClasspathPromptTemplateLoader classpathLoader;
    private final Set<String> warnedFallbackPaths;
    private final List<String> templateTypes;

    public LocalFilePromptTemplateLoader(
            Map<String, String> snapshot,
            ClasspathPromptTemplateLoader classpathLoader,
            Set<String> warnedFallbackPaths) {
        this.snapshot = snapshot;
        this.classpathLoader = classpathLoader;
        this.warnedFallbackPaths = warnedFallbackPaths;
        this.templateTypes = LocalFileResourceSnapshot.typeDirectories(snapshot, "templates");
    }

    @Override
    public String loadTemplate(String scenarioCode, String language) {
        PathSegments.requireSimpleRelativePath(scenarioCode, "Prompt template scenario code");
        PathSegments.requireSimpleSegment(language, "Prompt template language");
        String pathKey = resolvePathKey(scenarioCode, language);
        if (pathKey != null && snapshot.containsKey(pathKey)) {
            return snapshot.get(pathKey);
        }
        String text = classpathLoader.loadTemplate(scenarioCode, language);
        BuiltinFallbackWarnings.warnOnce(warnedFallbackPaths, fallbackPath(scenarioCode, language));
        return text;
    }

    private String resolvePathKey(String scenarioCode, String language) {
        if (scenarioCode.contains("/")) {
            return "templates/" + scenarioCode + "/" + language + "/template.md";
        }
        for (String templateType : templateTypes) {
            String candidate = resolveBareCode(templateType, scenarioCode, language);
            if (snapshot.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String resolveBareCode(String templateType, String scenarioCode, String language) {
        String networkLayer =
                "templates/" + templateType + "/network-layer/" + scenarioCode + "/v1/" + language + "/template.md";
        if (snapshot.containsKey(networkLayer)) {
            return networkLayer;
        }
        return "templates/" + templateType + "/" + scenarioCode + "/v1/" + language + "/template.md";
    }

    private static String fallbackPath(String scenarioCode, String language) {
        if (scenarioCode.contains("/")) {
            return "prompt_resources/templates/" + scenarioCode + "/" + language + "/template.md";
        }
        return "prompt_resources/templates/*/network-layer/" + scenarioCode + "/v1/" + language
                + "/template.md (or the layout without the network-layer segment)";
    }
}