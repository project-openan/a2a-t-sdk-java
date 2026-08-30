package net.openan.a2at.sample.service_recovery.client.prompt;

import java.util.Map;
import net.openan.a2at.sdk.core.model.MetadataContent;

/**
 * Prompt-generation bridge used by the client sample flow.
 *
 * <p>Exposes the two Notification-T generation entry points verified by this sample: the from-text variant and the
 * schema-guided from-data variant.
 *
 * @since 2026-08
 */
public interface SamplePromptClient {

    /**
     * Generates a notification prompt with metadata from natural-language input.
     *
     * @param text natural-language notification input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateNotificationPromptFromText(String text, String templateUri);

    /**
     * Generates a notification prompt with metadata from structured input and a data schema.
     *
     * @param data structured notification input as a string-to-object map
     * @param schema data schema map for schema-guided slot extraction
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String templateUri);
}
