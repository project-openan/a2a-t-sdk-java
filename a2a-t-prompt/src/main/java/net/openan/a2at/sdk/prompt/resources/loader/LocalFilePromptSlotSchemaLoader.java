package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotJsonSchema;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;

/**
 * Loads shared slot schemas from one local prompt resource root.
 *
 * <p>Resources are resolved against an assembly-time snapshot of the local root: runtime reads never touch the
 * filesystem, so changes to the local files only take effect after the SDK is restarted. A schema that is missing from
 * the local snapshot falls back to the packaged classpath copy, so customers only need to place the schemas they
 * override under the local root; the first fallback for each resource path is reported with a one-time warning.
 *
 * @since 2026-06
 */
public final class LocalFilePromptSlotSchemaLoader implements PromptSlotSchemaLoader {

    private final Map<String, String> snapshot;
    private final Path promptRootDir;
    private final ClasspathPromptSlotSchemaLoader classpathLoader;
    private final Set<String> warnedFallbackPaths;
    private final List<String> slotTypes;

    public LocalFilePromptSlotSchemaLoader(
            Map<String, String> snapshot,
            ClasspathPromptSlotSchemaLoader classpathLoader,
            Path promptRootDir,
            Set<String> warnedFallbackPaths) {
        this.snapshot = snapshot;
        this.classpathLoader = classpathLoader;
        this.promptRootDir = promptRootDir;
        this.warnedFallbackPaths = warnedFallbackPaths;
        this.slotTypes = LocalFileResourceSnapshot.typeDirectories(snapshot, "slots");
    }

    @Override
    public PromptSlotSchema loadSlotSchema(String scenarioCode, String language) {
        PathSegments.requireSimpleRelativePath(scenarioCode, "Prompt slot schema scenario code");
        PathSegments.requireSimpleSegment(language, "Prompt slot schema language");
        String pathKey = LocalFileResourceSnapshot.resolveResourcePath(
                snapshot, "slots", slotTypes, scenarioCode, language, "slot.json");
        if (pathKey != null && snapshot.containsKey(pathKey)) {
            return parse(snapshot.get(pathKey), scenarioCode, promptRootDir.resolve(pathKey).toString(), language);
        }
        BuiltinFallbackWarnings.warnOnce(
                warnedFallbackPaths, "prompt_resources/slots/" + scenarioCode + "/" + language + "/slot.json");
        return classpathLoader.loadSlotSchema(scenarioCode, language);
    }

    private static PromptSlotSchema parse(String payload, String scenarioCode, String resourcePath, String language) {
        try {
            return PromptResourceJsonParser.parse(payload, PromptSlotJsonSchema.class)
                    .toPromptSlotSchema(scenarioCode);
        } catch (JsonProcessingException exception) {
            throw ResourceReadErrors.readFailed(resourcePath, language, exception);
        }
    }
}