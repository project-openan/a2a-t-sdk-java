package net.openan.a2at.sdk.client.prompt.loader;

import net.openan.a2at.sdk.prompt.resources.loader.ClasspathPromptTemplateLoader;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;

/**
 * Default template loader backed by packaged classpath resources.
 *
 * @since 2026-06
 */
public final class DefaultClasspathClientTemplateLoader implements ClientTemplateLoader {

    private final ClasspathPromptTemplateLoader delegate;

    /**
     * Creates a template loader backed by one classpath resource loader.
     *
     * @param resourceLoader shared resource loader
     */
    public DefaultClasspathClientTemplateLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.delegate = new ClasspathPromptTemplateLoader(resourceLoader);
    }

    @Override
    public String loadTemplate(String scenarioCode, String language) {
        return delegate.loadTemplate(scenarioCode, language);
    }
}
