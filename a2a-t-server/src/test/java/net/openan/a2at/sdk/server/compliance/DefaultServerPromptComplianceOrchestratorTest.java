package net.openan.a2at.sdk.server.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.metadata.ServerPromptMetadataExtractor;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;
import net.openan.a2at.sdk.server.validation.ServerPromptSemanticValidator;
import org.junit.jupiter.api.Test;

class DefaultServerPromptComplianceOrchestratorTest {

    @Test
    void checkTaskPromptReturnsSuccessWhenMetadataExtractionAndValidationPass() {
        ProcessedPromptMetadata metadata =
                new ProcessedPromptMetadata("ran-energy-saving", "en-US", "Site: {site}", Map.of("site", "Site A"));
        RecordingPromptMetadataExtractor extractor = new RecordingPromptMetadataExtractor(metadata);
        RecordingPromptSemanticValidator validator = new RecordingPromptSemanticValidator(null);
        DefaultServerPromptComplianceOrchestrator orchestrator =
                new DefaultServerPromptComplianceOrchestrator(extractor, validator);

        PromptComplianceResult result = orchestrator.checkTaskPrompt("Site: Site A");

        assertTrue(result.success());
        assertThrows(NoSuchMethodException.class, () -> PromptComplianceResult.class.getMethod("metadata"));
        assertEquals(null, result.failure());
        assertEquals("Site: Site A", extractor.lastProcessedPromptText);
        assertEquals("Site: Site A", validator.lastProcessedPromptText);
        assertEquals(metadata, validator.lastMetadata);
    }

    @Test
    void checkTaskPromptReturnsFailureWhenMetadataExtractionFails() {
        DefaultServerPromptComplianceOrchestrator orchestrator = new DefaultServerPromptComplianceOrchestrator(
                new RecordingPromptMetadataExtractor(new PromptComplianceCheckException(
                        "scenario.not_mapped", "Prompt does not match any known template.", "prompt_parse")),
                new RecordingPromptSemanticValidator(null));

        PromptComplianceResult result = orchestrator.checkTaskPrompt("Unknown");

        assertEquals(false, result.success());
        assertEquals("scenario.not_mapped", result.failure().code());
        assertEquals("prompt_parse", result.failure().stage());
    }

    @Test
    void checkTaskPromptReturnsFailureWhenSemanticValidationFails() {
        ProcessedPromptMetadata metadata =
                new ProcessedPromptMetadata("ran-energy-saving", "en-US", "Site: {site}", Map.of("site", "Site A"));
        DefaultServerPromptComplianceOrchestrator orchestrator = new DefaultServerPromptComplianceOrchestrator(
                new RecordingPromptMetadataExtractor(metadata),
                new RecordingPromptSemanticValidator(new PromptComplianceCheckException(
                        "slot.semantic_conflict",
                        "The value of 'site' conflicts with the slot definition.",
                        "slot_validation")));

        PromptComplianceResult result = orchestrator.checkTaskPrompt("Site: Site A");

        assertEquals(false, result.success());
        assertEquals("slot.semantic_conflict", result.failure().code());
        assertEquals("slot_validation", result.failure().stage());
    }

    @Test
    void checkTaskPromptReturnsInputTooLongFailureWithoutRunningThePipelineWhenOverLimit() {
        RecordingPromptMetadataExtractor extractor = new RecordingPromptMetadataExtractor(
                new ProcessedPromptMetadata("ran-energy-saving", "en-US", "Site: {site}", Map.of("site", "Site A")));
        RecordingPromptSemanticValidator validator = new RecordingPromptSemanticValidator(null);
        DefaultServerPromptComplianceOrchestrator orchestrator =
                new DefaultServerPromptComplianceOrchestrator(extractor, validator, 5);
        String oversizedInput = "a".repeat(6);

        PromptComplianceResult result = orchestrator.checkTaskPrompt(oversizedInput);

        assertEquals(false, result.success());
        assertEquals("input.text_too_long", result.failure().code());
        assertEquals("input_gate", result.failure().stage());
        assertEquals(
                "Input text length 6 exceeds the maximum of 5 (A2AT_INPUT_TEXT_MAX_CHARS)",
                result.failure().message());
        assertEquals(null, extractor.lastProcessedPromptText, "Extractor must not run for an oversized input");
        assertEquals(null, validator.lastProcessedPromptText, "Validator must not run for an oversized input");
    }

    @Test
    void checkTaskPromptRunsThePipelineWhenInputIsExactlyAtLimit() {
        RecordingPromptMetadataExtractor extractor = new RecordingPromptMetadataExtractor(
                new ProcessedPromptMetadata("ran-energy-saving", "en-US", "Site: {site}", Map.of("site", "Site A")));
        RecordingPromptSemanticValidator validator = new RecordingPromptSemanticValidator(null);
        DefaultServerPromptComplianceOrchestrator orchestrator =
                new DefaultServerPromptComplianceOrchestrator(extractor, validator, 5);
        String boundaryInput = "a".repeat(5);

        PromptComplianceResult result = orchestrator.checkTaskPrompt(boundaryInput);

        assertTrue(result.success());
        assertEquals(boundaryInput, extractor.lastProcessedPromptText);
    }

    private static final class RecordingPromptMetadataExtractor implements ServerPromptMetadataExtractor {
        private final ProcessedPromptMetadata metadata;
        private final PromptComplianceCheckException exception;
        private String lastProcessedPromptText;

        private RecordingPromptMetadataExtractor(ProcessedPromptMetadata metadata) {
            this.metadata = metadata;
            this.exception = null;
        }

        private RecordingPromptMetadataExtractor(PromptComplianceCheckException exception) {
            this.metadata = null;
            this.exception = exception;
        }

        @Override
        public ProcessedPromptMetadata extract(String processedPromptText) {
            this.lastProcessedPromptText = processedPromptText;
            if (exception != null) {
                throw exception;
            }
            return metadata;
        }
    }

    private static final class RecordingPromptSemanticValidator implements ServerPromptSemanticValidator {
        private final PromptComplianceCheckException exception;
        private String lastProcessedPromptText;
        private ProcessedPromptMetadata lastMetadata;

        private RecordingPromptSemanticValidator(PromptComplianceCheckException exception) {
            this.exception = exception;
        }

        @Override
        public void validate(String processedPromptText, ProcessedPromptMetadata metadata) {
            this.lastProcessedPromptText = processedPromptText;
            this.lastMetadata = metadata;
            if (exception != null) {
                throw exception;
            }
        }
    }
}
