package net.openan.a2at.sample.service_recovery.server.flow;

import net.openan.a2at.sdk.core.model.FilledParamData;

/**
 * Sample-owned bridge for validating one rendered notification prompt with the server SDK.
 *
 * <p>The bridge wraps {@code A2ATServer.validateAndFillingNotificationData} with the fixed service-recovery parameter
 * schema and template URI; a validation failure surfaces as
 * {@link net.openan.a2at.sdk.core.validation.ContentValidationException}.
 *
 * @since 2026-08
 */
@FunctionalInterface
public interface NotificationPromptValidator {

    /**
     * Validates one rendered notification prompt and extracts its subscription parameters.
     *
     * @param promptText rendered notification prompt text received from the client
     * @return filled parameter data carrying the extracted subscription parameters
     */
    FilledParamData validateNotificationPrompt(String promptText);
}
