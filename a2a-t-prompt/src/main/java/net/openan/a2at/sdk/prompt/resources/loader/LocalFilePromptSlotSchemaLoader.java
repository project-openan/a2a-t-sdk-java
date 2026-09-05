package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotJsonSchema;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;

/**
 * Loads shared slot schemas from one local prompt resource root.
 *
 * <p>Resources are resolved against an assembly-time snapshot of the local root: runtime reads never touch the
 * filesystem, so changes to the local files only take effect after the SDK is restarted.
 *
 * @since 2026-06
 */
public final class LocalFilePromptSlotSchemaLoader implements PromptSlotSchemaLoader {

    private final Map<String, String> snapshot;
    private final Path promptRootDir;
    private final List<String> slotTypes;

    public LocalFilePromptSlotSchemaLoader(Map<String, String> snapshot, Path promptRootDir) {
        this.snapshot = snapshot;
        this.promptRootDir = promptRootDir;
        this.slotTypes = LocalFileResourceSnapshot.typeDirectories(snapshot, "slots");
    }

    @Override
    public PromptSlotSchema loadSlotSchema(String scenarioCode, String language) {
        PathSegments.requireSimpleRelativePath(scenarioCode, "Prompt slot schema scenario code");
        PathSegments.requireSimpleSegment(language, "Prompt slot schema language");
        String pathKey;
        if (scenarioCode.contains("/")) {
            pathKey = "slots/" + scenarioCode + "/" + language + "/slot.json";
        } else {
            pathKey = null;
            for (String slotType : slotTypes) {
                String candidate = resolveBareCode(slotType, scenarioCode, language);
                if (snapshot.containsKey(candidate)) {
                    pathKey = candidate;
                    break;
                }
            }
        }
        if (pathKey == null || !snapshot.containsKey(pathKey)) {
            throw notFound(scenarioCode, language);
        }
        try {
            return PromptResourceJsonParser.parse(snapshot.get(pathKey), PromptSlotJsonSchema.class)
                    .toPromptSlotSchema(scenarioCode);
        } catch (JsonProcessingException exception) {
            throw ResourceReadErrors.readFailed(promptRootDir.resolve(pathKey).toString(), language, exception);
        }
    }

    /**
     * Resolves a bare scenario code under one slot type directory, preferring the {@code network-layer} domain layout
     * over the plain layout.
     */
    private String resolveBareCode(String slotType, String scenarioCode, String language) {
        String networkLayer = "slots/" + slotType + "/network-layer/" + scenarioCode + "/v1/" + language + "/slot.json";
        if (snapshot.containsKey(networkLayer)) {
            return networkLayer;
        }
        return "slots/" + slotType + "/" + scenarioCode + "/v1/" + language + "/slot.json";
    }

    private ResourceNotFoundException notFound(String scenarioCode, String language) {
        String pathHint = scenarioCode.contains("/")
                ? promptRootDir
                        .resolve("slots")
                        .resolve(scenarioCode)
                        .resolve(language)
                        .resolve("slot.json")
                        .toString()
                : promptRootDir.resolve("slots") + "/*/network-layer/" + scenarioCode + "/v1/" + language
                        + "/slot.json (or the layout without the network-layer segment)";
        return new ResourceNotFoundException("Prompt resource file does not exist.", pathHint);
    }
}
