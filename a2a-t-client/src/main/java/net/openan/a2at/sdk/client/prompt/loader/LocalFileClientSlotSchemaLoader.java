package net.openan.a2at.sdk.client.prompt.loader;

import java.nio.file.Path;
import net.openan.a2at.sdk.prompt.resources.loader.LocalFilePromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;

/**
 * Loads slot schema definitions from one local prompt resource root.
 *
 * @since 2026-06
 */
public final class LocalFileClientSlotSchemaLoader implements ClientSlotSchemaLoader {
    private final LocalFilePromptSlotSchemaLoader delegate;

    /**
     * Creates one local slot schema loader.
     *
     * @param promptRootDir prompt resource root directory
     */
    public LocalFileClientSlotSchemaLoader(Path promptRootDir) {
        this.delegate = new LocalFilePromptSlotSchemaLoader(promptRootDir);
    }

    @Override
    public PromptSlotSchema loadSlotSchema(String scenarioCode, String language) {
        return delegate.loadSlotSchema(scenarioCode, language);
    }
}
