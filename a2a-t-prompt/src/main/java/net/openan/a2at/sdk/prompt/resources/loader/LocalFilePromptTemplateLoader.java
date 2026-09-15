package net.openan.a2at.sdk.prompt.resources.loader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.openan.a2at.sdk.core.resources.PathSegments;

/**
 * Loads shared prompt templates from one local prompt resource root.
 *
 * <p>Resources are resolved against an assembly-time snapshot of the local root: runtime reads never touch the
 * filesystem, so changes to the local files only take effect after the SDK is restarted. A template that is missing from
 * the local snapshot falls back to the packaged classpath copy, so customers only need to place the templates they
 * override under the local root; the first fallback for each resource path is reported with a one-time warning.
 *
 * @since 2026-06
 */
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
        String pathKey = LocalFileResourceSnapshot.resolveResourcePath(
                snapshot, "templates", templateTypes, scenarioCode, language, "template.md");
        if (pathKey != null && snapshot.containsKey(pathKey)) {
            return snapshot.get(pathKey);
        }
        BuiltinFallbackWarnings.warnOnce(
                warnedFallbackPaths, "prompt_resources/templates/" + scenarioCode + "/" + language + "/template.md");
        return classpathLoader.loadTemplate(scenarioCode, language);
    }
}