package net.openan.a2at.sdk.prompt.resources.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Shared access point for prompt resources backed either by local files or classpath resources.
 *
 * @since 2026-06
 */
public interface PromptResourceAccess {

    String CLASSPATH_SOURCE_TYPE = "classpath";

    String LOCAL_FILE_SOURCE_TYPE = "local_file";

    static PromptResourceAccess create(PromptRuntimeConfig config) {
        if (CLASSPATH_SOURCE_TYPE.equals(config.sourceType())) {
            return new ClasspathAccess(new ClasspathPromptResourceLoader());
        }
        if (LOCAL_FILE_SOURCE_TYPE.equals(config.sourceType())) {
            return new LocalFileAccess(Path.of(config.localRootDir()));
        }
        throw new UnsupportedOperationException("Unsupported prompt source type: " + config.sourceType());
    }

    boolean classpath();

    ClasspathPromptResourceLoader classpathResourceLoader();

    Path localRootDir();

    List<ScenarioDefinition> loadScenarios(String language);

    PromptTemplateTextLoader templateLoader();

    PromptSlotSchemaLoader slotSchemaLoader();

    String loadPrompt(String promptCategory, String language, String fileName);

    final class ClasspathAccess implements PromptResourceAccess {
        private final ClasspathPromptResourceLoader resourceLoader;

        private ClasspathAccess(ClasspathPromptResourceLoader resourceLoader) {
            this.resourceLoader = resourceLoader;
        }

        @Override
        public boolean classpath() {
            return true;
        }

        @Override
        public ClasspathPromptResourceLoader classpathResourceLoader() {
            return resourceLoader;
        }

        @Override
        public Path localRootDir() {
            throw new UnsupportedOperationException("Classpath prompt resources do not have a local root directory.");
        }

        @Override
        public List<ScenarioDefinition> loadScenarios(String language) {
            return new ClasspathPromptScenarioCatalogLoader(resourceLoader).load(language);
        }

        @Override
        public PromptTemplateTextLoader templateLoader() {
            return new ClasspathPromptTemplateLoader(resourceLoader);
        }

        @Override
        public PromptSlotSchemaLoader slotSchemaLoader() {
            return new ClasspathPromptSlotSchemaLoader(resourceLoader);
        }

        @Override
        public String loadPrompt(String promptCategory, String language, String fileName) {
            return resourceLoader.loadText(PromptResourceKey.prompt(promptCategory, language, fileName));
        }
    }

    final class LocalFileAccess implements PromptResourceAccess {
        private final Path promptRootDir;

        private LocalFileAccess(Path promptRootDir) {
            this.promptRootDir = promptRootDir;
        }

        @Override
        public boolean classpath() {
            return false;
        }

        @Override
        public ClasspathPromptResourceLoader classpathResourceLoader() {
            throw new UnsupportedOperationException("Local prompt resources do not have a classpath loader.");
        }

        @Override
        public Path localRootDir() {
            return promptRootDir;
        }

        @Override
        public List<ScenarioDefinition> loadScenarios(String language) {
            return new LocalFilePromptScenarioCatalogLoader(promptRootDir).load(language);
        }

        @Override
        public PromptTemplateTextLoader templateLoader() {
            return new LocalFilePromptTemplateLoader(promptRootDir);
        }

        @Override
        public PromptSlotSchemaLoader slotSchemaLoader() {
            return new LocalFilePromptSlotSchemaLoader(promptRootDir);
        }

        @Override
        public String loadPrompt(String promptCategory, String language, String fileName) {
            Path promptPath = promptRootDir.resolve("prompts")
                    .resolve(promptCategory)
                    .resolve(language)
                    .resolve(fileName);
            if (!Files.exists(promptPath)) {
                throw new ResourceNotFoundException("Prompt resource file does not exist.", promptPath.toString());
            }
            try {
                return Files.readString(promptPath);
            } catch (IOException exception) {
                throw new SdkException("Failed to read prompt resource: " + promptPath, exception);
            }
        }
    }
}
