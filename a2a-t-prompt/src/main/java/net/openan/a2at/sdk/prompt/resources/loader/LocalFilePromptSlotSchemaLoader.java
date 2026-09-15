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
        PromptSlotSchema schema = classpathLoader.loadSlotSchema(scenarioCode, language);
        BuiltinFallbackWarnings.warnOnce(warnedFallbackPaths, fallbackPath(scenarioCode, language));
        return schema;
    }

    /**
     * Builds the resource path reported by the fallback warning: exact for path-form scenario codes, and the same
     * wildcard locator the classpath loader uses for a bare scenario code.
     */
    private static String fallbackPath(String scenarioCode, String language) {
        if (scenarioCode.contains("/")) {
            return "prompt_resources/slots/" + scenarioCode + "/" + language + "/slot.json";
        }
        return "prompt_resources/slots/*/network-layer/" + scenarioCode + "/v1/" + language
                + "/slot.json (or the layout without the network-layer segment)";
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
