package net.openan.a2at.sdk.corpus.engine.registry;

import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.validation.ContentValidator;

/**
 * Notification-T API registration: JSON step arguments are bound to the production
 * {@link ClientPromptGenerationOrchestrator} (from-text / from-data generation) and the Notification-T
 * {@link ContentValidator} (validation) assembled by {@link SdkRuntimeAssembler}.
 */
public final class NotificationApis {

    private NotificationApis() {}

    public static void registerNotificationApis(
            ApiRegistry registry,
            ClientPromptGenerationOrchestrator client,
            ContentValidator notificationValidator) {
        registry.register("generateNotificationPromptFromText", args ->
                ClientApis.metadataToMap(client.generateNotificationPromptFromText(
                        ClientApis.Args.text(args, "text"), ClientApis.Args.templateUri(args, "templateUri"))));
        registry.register("generateNotificationPromptFromDataWithSchema", args ->
                ClientApis.metadataToMap(client.generateNotificationPromptFromDataWithSchema(
                        ClientApis.Args.map(args, "data"),
                        ClientApis.Args.map(args, "schema"),
                        ClientApis.Args.templateUri(args, "templateUri"))));
        registry.register("validateNotificationPromptAndDataFilling", args ->
                ServerApis.filledToMap(notificationValidator.validate(
                        ClientApis.Args.text(args, "promptText"),
                        ClientApis.Args.map(args, "schema"),
                        ClientApis.Args.templateUri(args, "templateUri"))));
    }
}