package net.openan.a2at.sdk.client.prompt.orchestration;

import java.util.Map;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.TemplateUri;

/**
 * Internal prompt-generation orchestration contract used by the client facade.
 *
 * @since 2026-06
 */
public interface ClientPromptGenerationOrchestrator {

    /**
     * Generates a processed task prompt from user input.
     *
     * @param userInput raw or structured user input; a String input longer than the configured maximum length fails
     *     fast with the code {@code input.text_too_long}
     * @return prompt-generation result
     */
    PromptGenerationResult generateTaskPrompt(Object userInput);

    /**
     * Generates a task prompt with metadata from natural-language input using the template identified by the template
     * URI, bypassing scenario recognition.
     *
     * @param text natural-language task input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the text or template URI is null
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code input.text_too_long}
     *     when the text exceeds the configured maximum length
     */
    MetadataContent generateTaskPromptFromText(String text, TemplateUri templateUri);

    /**
     * Generates a task prompt with metadata from structured input and a data schema using the template identified by
     * the template URI, bypassing scenario recognition.
     *
     * @param data structured task input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field; must not be null or empty
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the template URI, data or schema is null
     * @throws IllegalArgumentException if the schema is empty
     */
    MetadataContent generateTaskPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri);

    /**
     * Generates an authorization prompt with metadata from natural-language input using the template identified by the
     * template URI, bypassing scenario recognition.
     *
     * @param text natural-language authorization input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the text or template URI is null
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code input.text_too_long}
     *     when the text exceeds the configured maximum length
     */
    MetadataContent generateAuthPromptFromText(String text, TemplateUri templateUri);

    /**
     * Generates an authorization prompt with metadata from structured input and a data schema using the template
     * identified by the template URI, bypassing scenario recognition.
     *
     * @param data structured authorization input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field; must not be null or empty
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the template URI, data or schema is null
     * @throws IllegalArgumentException if the schema is empty
     */
    MetadataContent generateAuthPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri);

    /**
     * Generates a notification prompt with metadata from natural-language input using the template identified by the
     * template URI, bypassing scenario recognition.
     *
     * @param text natural-language notification input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the text or template URI is null
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code input.text_too_long}
     *     when the text exceeds the configured maximum length
     */
    MetadataContent generateNotificationPromptFromText(String text, TemplateUri templateUri);

    /**
     * Generates a notification prompt with metadata from structured input and a data schema using the template
     * identified by the template URI, bypassing scenario recognition.
     *
     * @param data structured notification input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field; must not be null or empty
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the template URI, data or schema is null
     * @throws IllegalArgumentException if the schema is empty
     */
    MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri);
}
