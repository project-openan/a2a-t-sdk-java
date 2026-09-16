package net.openan.a2at.sdk.negotiation.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guards the package layout of the negotiation content layer.
 *
 * <p>The four content-layer packages carry exactly the pinned set of compilation units and no subpackages, so that the
 * internal layering (models and vocabulary, resources, generation, validation) cannot drift silently. The pre-existing
 * legacy packages of the old negotiation state machine are not pinned by this guard.
 */
class NegotiationPackageStructureTest {

    @Test
    void contentPackageExposesExactlyThePinnedTypes() throws IOException {
        assertEquals(
                List.of(
                        "FeasibilityEndingContent.java",
                        "FeasibilityProposeContent.java",
                        "InformationEndingContent.java",
                        "InformationProposeContent.java",
                        "NegotiationAbortContent.java",
                        "NegotiationAbortData.java",
                        "NegotiationAction.java",
                        "NegotiationConclusion.java",
                        "NegotiationContent.java",
                        "NegotiationEndingContent.java",
                        "NegotiationEndingData.java",
                        "NegotiationGenerationException.java",
                        "NegotiationItem.java",
                        "NegotiationParamExtractionException.java",
                        "NegotiationProcessingException.java",
                        "NegotiationProposeContent.java",
                        "NegotiationProposeData.java",
                        "NegotiationType.java",
                        "TargetEndingContent.java",
                        "TargetProposeContent.java",
                        "Vocabulary.java",
                        "package-info.java"),
                topLevelJavaFiles(negotiationRoot().resolve("content")));
        assertEquals(List.of(), topLevelDirectories(negotiationRoot().resolve("content")));
    }

    @Test
    void resourcesPackageExposesExactlyThePinnedTypes() throws IOException {
        assertEquals(
                List.of(
                        "DefaultNegotiationTemplateLoader.java",
                        "NegotiationReference.java",
                        "NegotiationTemplateLoader.java",
                        "package-info.java"),
                topLevelJavaFiles(negotiationRoot().resolve("resources")));
        assertEquals(List.of(), topLevelDirectories(negotiationRoot().resolve("resources")));
    }

    @Test
    void generationPackageExposesExactlyThePinnedTypes() throws IOException {
        assertEquals(
                List.of(
                        "AbortGenerator.java",
                        "AbstractNegotiationGenerator.java",
                        "DefaultNegotiationContentExtractor.java",
                        "FeasibilityEndingGenerator.java",
                        "FeasibilityProposeGenerator.java",
                        "InformationEndingGenerator.java",
                        "InformationProposeGenerator.java",
                        "NegotiationContentExtractor.java",
                        "NegotiationContentService.java",
                        "NegotiationGenerationOrchestrator.java",
                        "NegotiationGenerationOrchestratorBuilder.java",
                        "NegotiationGenerator.java",
                        "NegotiationGeneratorRegistry.java",
                        "NegotiationItemFormatter.java",
                        "NegotiationJsonSchemaBuilder.java",
                        "NegotiationMessageBuilder.java",
                        "NegotiationPromptRenderer.java",
                        "NegotiationPromptResourceLoader.java",
                        "NegotiationRenderException.java",
                        "TargetEndingGenerator.java",
                        "TargetProposeGenerator.java",
                        "package-info.java"),
                topLevelJavaFiles(negotiationRoot().resolve("generation")));
        assertEquals(List.of(), topLevelDirectories(negotiationRoot().resolve("generation")));
    }

    @Test
    void validationPackageExposesExactlyThePinnedTypes() throws IOException {
        assertEquals(
                List.of(
                        "DefaultNegotiationComplianceChecker.java",
                        "DefaultNegotiationSemanticValidator.java",
                        "NegotiationComplianceChecker.java",
                        "NegotiationRuleCheckResult.java",
                        "NegotiationRuleCheckerAdapter.java",
                        "NegotiationSemanticValidator.java",
                        "NegotiationValidationException.java",
                        "ParamExtractor.java",
                        "SemanticValidationResult.java",
                        "package-info.java"),
                topLevelJavaFiles(negotiationRoot().resolve("validation")));
        assertEquals(List.of(), topLevelDirectories(negotiationRoot().resolve("validation")));
    }

    @Test
    void contentLayerPackagesKeepNoUnexpectedSubpackages() {
        Path root = negotiationRoot();
        List.of("content", "generation", "resources", "validation")
                .forEach(packageName -> assertFalse(
                        hasSubdirectory(root.resolve(packageName)),
                        "the " + packageName + " package must not grow subpackages"));
    }

    private static Path negotiationRoot() {
        return Path.of("src", "main", "java", "net", "openan", "a2at", "sdk", "negotiation");
    }

    private static boolean hasSubdirectory(Path path) {
        try (var files = Files.list(path)) {
            return files.anyMatch(Files::isDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list " + path, exception);
        }
    }

    private static List<String> topLevelJavaFiles(Path path) throws IOException {
        try (var files = Files.list(path)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".java"))
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static List<String> topLevelDirectories(Path path) throws IOException {
        try (var files = Files.list(path)) {
            return files.filter(Files::isDirectory)
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
