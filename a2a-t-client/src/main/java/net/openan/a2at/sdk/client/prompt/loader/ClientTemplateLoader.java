package net.openan.a2at.sdk.client.prompt.loader;

/**
 * Loads prompt templates for client-side prompt generation.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface ClientTemplateLoader {

    /**
     * Loads one prompt template for the requested scenario and language.
     *
     * @param scenarioCode scenario code to resolve
     * @param language locale identifier to resolve
     * @return template text
     */
    String loadTemplate(String scenarioCode, String language);
}
