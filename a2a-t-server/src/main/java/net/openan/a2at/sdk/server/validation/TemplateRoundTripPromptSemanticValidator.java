package net.openan.a2at.sdk.server.validation;

import net.openan.a2at.sdk.prompt.taskrendering.api.TaskPromptRenderer;
import net.openan.a2at.sdk.prompt.taskrendering.exception.TaskPromptRenderException;
import net.openan.a2at.sdk.server.exception.PromptComplianceCheckException;
import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;

/**
 * Validates semantic consistency by round-tripping extracted slots through the original template.
 *
 * @since 2026-06
 */
public final class TemplateRoundTripPromptSemanticValidator implements ServerPromptSemanticValidator {

    private final TaskPromptRenderer renderer;

    /** Creates a semantic validator backed by the default task prompt renderer. */
    public TemplateRoundTripPromptSemanticValidator() {
        this(new TaskPromptRenderer());
    }

    /**
     * Creates a semantic validator backed by the supplied renderer.
     *
     * @param renderer task prompt renderer
     */
    public TemplateRoundTripPromptSemanticValidator(TaskPromptRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void validate(String processedPromptText, ProcessedPromptMetadata metadata) {
        final String rendered;
        try {
            rendered = renderer.render(metadata.templateText(), metadata.slots());
        } catch (TaskPromptRenderException error) {
            throw new PromptComplianceCheckException("slot_validation_error", error.getMessage(), "slot_validation");
        }
        if (!processedPromptText.equals(rendered)) {
            throw new PromptComplianceCheckException(
                    "slot_validation_error",
                    "Prompt is not semantically consistent with extracted slots.",
                    "slot_validation");
        }
    }
}
