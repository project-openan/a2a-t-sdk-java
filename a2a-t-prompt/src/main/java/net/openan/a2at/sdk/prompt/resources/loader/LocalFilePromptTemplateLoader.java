package net.openan.a2at.sdk.prompt.resources.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;

/**
 * Loads shared prompt templates from one local prompt resource root.
 *
 * @since 2026-06
 */
public final class LocalFilePromptTemplateLoader implements PromptTemplateTextLoader {

    private final Path promptRootDir;

    public LocalFilePromptTemplateLoader(Path promptRootDir) {
        this.promptRootDir = promptRootDir;
    }

    @Override
    public String loadTemplate(String scenarioCode, String language) {
        Path templatePath = promptRootDir
                .resolve("templates")
                .resolve(scenarioCode)
                .resolve(language)
                .resolve("template.md");
        if (!Files.exists(templatePath)) {
            throw new ResourceNotFoundException("Prompt resource file does not exist.", templatePath.toString());
        }
        try {
            return Files.readString(templatePath);
        } catch (IOException exception) {
            throw new SdkException("Failed to read template resource: " + templatePath, exception);
        }
    }
}
