package net.openan.a2at.sdk.prompt.resources.loader;

/**
 * Loads raw template text for one scenario and language.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface PromptTemplateTextLoader {

    /**
     * Loads one template text payload.
     *
     * @param scenarioCode scenario code
     * @param language resource language
     * @return template markdown text
     */
    String loadTemplate(String scenarioCode, String language);
}
