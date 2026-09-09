package net.openan.a2at.sdk.server.compliance;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.metadata.ServerPromptMetadataExtractor;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;
import net.openan.a2at.sdk.server.model.PromptComplianceFailure;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;
import net.openan.a2at.sdk.server.validation.ServerPromptSemanticValidator;
import org.jspecify.annotations.Nullable;

/**
 * Minimal runnable server-side prompt compliance orchestrator.
 *
 * @since 2026-06
 */
public final class DefaultServerPromptComplianceOrchestrator implements ServerPromptComplianceOrchestrator {

    /** Compliance stage reported when the input gate rejects an oversized prompt before any LLM call. */
    private static final String INPUT_GATE_STAGE = "input_gate";

    private final ServerPromptMetadataExtractor metadataExtractor;

    private final ServerPromptSemanticValidator semanticValidator;

    private final int maxTextChars;

    private final String language;

    /**
     * Creates a compliance orchestrator.
     *
     * @param metadataExtractor prompt metadata extractor
     * @param semanticValidator semantic validator
     */
    public DefaultServerPromptComplianceOrchestrator(
            ServerPromptMetadataExtractor metadataExtractor, ServerPromptSemanticValidator semanticValidator) {
        this(metadataExtractor, semanticValidator, InputLimitConfig.DEFAULT_MAX_TEXT_CHARS);
    }

    /**
     * Creates a compliance orchestrator with an explicit free-text input limit.
     *
     * @param metadataExtractor prompt metadata extractor
     * @param semanticValidator semantic validator
     * @param maxTextChars maximum length in characters accepted for the processed prompt text
     */
    public DefaultServerPromptComplianceOrchestrator(
            ServerPromptMetadataExtractor metadataExtractor,
            ServerPromptSemanticValidator semanticValidator,
            int maxTextChars) {
        this(metadataExtractor, semanticValidator, maxTextChars, null);
    }

    /**
     * Creates a compliance orchestrator with an explicit free-text input limit and message language.
     *
     * @param metadataExtractor prompt metadata extractor
     * @param semanticValidator semantic validator
     * @param maxTextChars maximum length in characters accepted for the processed prompt text
     * @param language language used to render failure messages, for example {@code zh-CN}; null falls back to
     *     {@code en-US}
     */
    public DefaultServerPromptComplianceOrchestrator(
            ServerPromptMetadataExtractor metadataExtractor,
            ServerPromptSemanticValidator semanticValidator,
            int maxTextChars,
            @Nullable String language) {
        this.metadataExtractor = metadataExtractor;
        this.semanticValidator = semanticValidator;
        this.maxTextChars = maxTextChars;
        this.language = language;
    }

    @Override
    public PromptComplianceResult checkTaskPrompt(String processedPromptText) {
        if (InputLimitConfig.isTooLong(processedPromptText, maxTextChars)) {
            Map<String, String> facts = Map.of(
                    "actual_length", String.valueOf(processedPromptText.length()),
                    "max_chars", String.valueOf(maxTextChars));
            return new PromptComplianceResult(
                    false,
                    new PromptComplianceFailure(
                            ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(),
                            ErrorMessages.render(ErrorCatalog.INPUT_TEXT_TOO_LONG, language, facts),
                            INPUT_GATE_STAGE));
        }
        try {
            ProcessedPromptMetadata metadata = metadataExtractor.extract(processedPromptText);
            semanticValidator.validate(processedPromptText, metadata);
            return new PromptComplianceResult(true, null);
        } catch (PromptComplianceCheckException error) {
            return new PromptComplianceResult(
                    false, new PromptComplianceFailure(error.getCode(), error.getMessage(), error.getStage()));
        }
    }
}
