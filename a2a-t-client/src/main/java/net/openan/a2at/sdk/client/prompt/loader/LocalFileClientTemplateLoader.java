package net.openan.a2at.sdk.client.prompt.loader;

import java.nio.file.Path;
import net.openan.a2at.sdk.prompt.resources.loader.LocalFilePromptTemplateLoader;

/**
 * Loads client templates from one local prompt resource root.
 *
 * @since 2026-06
 */
public final class LocalFileClientTemplateLoader implements ClientTemplateLoader {
    private final LocalFilePromptTemplateLoader delegate;

    /**
     * Creates one local template loader.
     *
     * @param promptRootDir prompt resource root directory
     */
    public LocalFileClientTemplateLoader(Path promptRootDir) {
        this.delegate = new LocalFilePromptTemplateLoader(promptRootDir);
    }

    @Override
    public String loadTemplate(String scenarioCode, String language) {
        return delegate.loadTemplate(scenarioCode, language);
    }
}
