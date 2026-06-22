package net.openan.a2at.sdk.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LlmPackageStructureTest {

    @Test
    void llmRootPackageExposesOnlySimplifiedPublicApi() throws IOException {
        Path root = llmRoot();

        assertEquals(
                List.of(
                        "LLMClient.java",
                        "LLMClientConfig.java",
                        "LLMClientFactory.java",
                        "LLMConfigError.java",
                        "LLMConfigLoader.java",
                        "LLMError.java",
                        "LLMResponse.java",
                        "LLMRuntimeError.java",
                        "package-info.java"),
                topLevelJavaFiles(root));
    }

    @Test
    void llmPackageKeepsOnlyProviderSubpackage() throws IOException {
        Path root = llmRoot();

        assertEquals(List.of("providers"), topLevelDirectories(root));
        assertEquals(List.of("OpenAIClient.java"), topLevelJavaFiles(root.resolve("providers")));
    }

    @Test
    void llmPackageRemovesLegacyAdapterBuilderParserTransportAndMapperLayers() {
        Path root = llmRoot();

        assertFalse(Files.exists(root.resolve("adapter")));
        assertFalse(Files.exists(root.resolve("config")));
        assertFalse(Files.exists(root.resolve("exception")));
        assertFalse(Files.exists(root.resolve("internal")));
        assertFalse(Files.exists(root.resolve("model")));
        assertFalse(Files.exists(root.resolve("spi")));
        assertFalse(Files.exists(root.resolve("api")));
    }

    @Test
    void llmTestsRemoveLegacyConfigInternalAndModelPackages() {
        Path root = llmTestRoot();

        assertFalse(Files.exists(root.resolve("config")));
        assertFalse(Files.exists(root.resolve("internal")));
        assertFalse(Files.exists(root.resolve("model")));
    }

    private static Path llmRoot() {
        return Path.of("src", "main", "java", "net", "openan", "a2at", "sdk", "llm");
    }

    private static Path llmTestRoot() {
        return Path.of("src", "test", "java", "net", "openan", "a2at", "sdk", "llm");
    }

    private static List<String> topLevelJavaFiles(Path path) throws IOException {
        try (var files = Files.list(path)) {
            return files
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static List<String> topLevelDirectories(Path path) throws IOException {
        try (var files = Files.list(path)) {
            return files
                    .filter(Files::isDirectory)
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
