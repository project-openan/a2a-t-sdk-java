package net.openan.a2at.sdk.prompt.resources.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.A2ATConfigKeys;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared access point for prompt resources backed either by local files or classpath resources.
 *
 * <p>The factory routes resources by origin: the business content (tasks' templates, slot schemas and scenario catalog)
 * follows the configured source type; the LLM instruction prompts, negotiation templates and negotiation vocabulary are
 * always loaded from the classpath.
 *
 * @since 2026-06
 */
public interface PromptResourceAccess {

    String CLASSPATH_SOURCE_TYPE = PromptRuntimeConfig.SOURCE_TYPE_CLASSPATH;

    String LOCAL_FILE_SOURCE_TYPE = PromptRuntimeConfig.SOURCE_TYPE_LOCAL_FILE;

    static PromptResourceAccess create(PromptRuntimeConfig config) {
        if (CLASSPATH_SOURCE_TYPE.equals(config.sourceType())) {
            if (hasConfiguredLocalRoot(config.localRootDir())) {
                warnIgnoredClasspathLocalRoot(config.localRootDir());
            }
            return new ClasspathAccess(new ClasspathPromptResourceLoader());
        }
        if (LOCAL_FILE_SOURCE_TYPE.equals(config.sourceType())) {
            Path localRootDir = requireLocalRoot(config.localRootDir());
            warnIgnoredLocalDirectories(localRootDir);
            return new LocalFileAccess(localRootDir, new ClasspathPromptResourceLoader());
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
        private final ClasspathPromptResourceLoader resourceLoader;
        private final Map<String, String> snapshot;

        private LocalFileAccess(Path promptRootDir, ClasspathPromptResourceLoader resourceLoader) {
            this.promptRootDir = promptRootDir;
            this.resourceLoader = resourceLoader;
            this.snapshot = LocalFileResourceSnapshot.capture(promptRootDir);
        }

        @Override
        public boolean classpath() {
            return false;
        }

        @Override
        public ClasspathPromptResourceLoader classpathResourceLoader() {
            return resourceLoader;
        }

        @Override
        public Path localRootDir() {
            return promptRootDir;
        }

        @Override
        public List<ScenarioDefinition> loadScenarios(String language) {
            return new LocalFilePromptScenarioCatalogLoader(snapshot, promptRootDir).load(language);
        }

        @Override
        public PromptTemplateTextLoader templateLoader() {
            return new LocalFilePromptTemplateLoader(snapshot, promptRootDir);
        }

        @Override
        public PromptSlotSchemaLoader slotSchemaLoader() {
            return new LocalFilePromptSlotSchemaLoader(snapshot, promptRootDir);
        }

        @Override
        public String loadPrompt(String promptCategory, String language, String fileName) {
            return resourceLoader.loadText(PromptResourceKey.prompt(promptCategory, language, fileName));
        }
    }

    private static boolean hasConfiguredLocalRoot(String localRootDir) {
        return localRootDir != null && !localRootDir.isBlank();
    }

    private static Path requireLocalRoot(String localRootDir) {
        String key = A2ATConfigKeys.PromptRuntime.LOCAL_ROOT_DIR;
        if (localRootDir == null || localRootDir.isBlank()) {
            throw new IllegalArgumentException(
                    "Prompt resource local root directory is required for sourceType '" + LOCAL_FILE_SOURCE_TYPE
                            + "' but is not set; configure " + key + " to the local prompt resource root.");
        }
        Path root = Path.of(localRootDir);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Prompt resource local root directory configured via " + key
                    + " does not exist or is not a directory: " + root);
        }
        return root;
    }

    private static void warnIgnoredClasspathLocalRoot(String localRootDir) {
        logger().atWarn()
                .log(
                        "prompt_resource_local_root_ignored root={} source=classpath reason=source_type_is_classpath",
                        localRootDir);
    }

    private static void warnIgnoredLocalDirectories(Path localRootDir) {
        List<String> ignoredDirectories = new ArrayList<>();
        if (Files.isDirectory(localRootDir.resolve("prompts"))) {
            ignoredDirectories.add("prompts");
        }
        if (Files.isDirectory(localRootDir.resolve("templates").resolve("Negotiation-T"))) {
            ignoredDirectories.add("templates/Negotiation-T");
        }
        if (Files.isDirectory(localRootDir.resolve("negotiation-vocabulary"))) {
            ignoredDirectories.add("negotiation-vocabulary");
        }
        if (!ignoredDirectories.isEmpty()) {
            logger().atWarn()
                    .log(
                            "prompt_resource_local_directories_ignored root={} directories=[{}] reason=classpath_fixed",
                            localRootDir,
                            String.join(", ", ignoredDirectories));
        }
    }

    private static Logger logger() {
        return LoggerFactory.getLogger(PromptResourceAccess.class);
    }
}
