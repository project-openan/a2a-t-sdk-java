package net.openan.a2at.sdk.prompt.resources.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotJsonSchema;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;

/**
 * Loads shared slot schemas from one local prompt resource root.
 *
 * @since 2026-06
 */
public final class LocalFilePromptSlotSchemaLoader implements PromptSlotSchemaLoader {

    private final Path promptRootDir;

    public LocalFilePromptSlotSchemaLoader(Path promptRootDir) {
        this.promptRootDir = promptRootDir;
    }

    @Override
    public PromptSlotSchema loadSlotSchema(String scenarioCode, String language) {
        Path schemaPath = promptRootDir
                .resolve("slots")
                .resolve(scenarioCode)
                .resolve(language)
                .resolve("slot.json");
        if (!Files.exists(schemaPath)) {
            throw new ResourceNotFoundException("Prompt resource file does not exist.", schemaPath.toString());
        }
        try {
            return PromptResourceJsonParser.parse(Files.readString(schemaPath), PromptSlotJsonSchema.class)
                    .toPromptSlotSchema(scenarioCode);
        } catch (IOException exception) {
            throw new SdkException("Failed to read slot schema resource: " + schemaPath, exception);
        }
    }
}
