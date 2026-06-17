package net.openan.a2at.sdk.prompt.resources.loader;

import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Loads shared prompt templates from packaged classpath prompt resources.
 *
 * @since 2026-06
 */
public final class ClasspathPromptTemplateLoader implements PromptTemplateTextLoader {

    private final ClasspathPromptResourceLoader resourceLoader;

    public ClasspathPromptTemplateLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String loadTemplate(String scenarioCode, String language) {
        return resourceLoader.loadText(PromptResourceKey.template(scenarioCode, language, "template.md"));
    }
}
