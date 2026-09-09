package net.openan.a2at.sdk.prompt.taskrendering.exception;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATBusinessException;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;

/**
 * Raised when task prompt template rendering fails.
 *
 * <p>Carries the {@code template.render_failed} code; the {@code reason} fact describes the concrete rendering problem.
 * The template URI itself is not known to the renderer and is supplied by the caller that catches this exception.
 *
 * @since 2026-06
 */
public final class TaskPromptRenderException extends A2ATBusinessException {

    /**
     * Creates a task prompt rendering exception.
     *
     * @param message failure message describing the rendering problem
     */
    public TaskPromptRenderException(String message) {
        super(ErrorCatalog.TEMPLATE_RENDER_FAILED.getCode(), message, Map.of("reason", message));
    }
}
