package net.openan.a2at.sdk.client.prompt.loader;

import net.openan.a2at.sdk.prompt.resources.loader.ClasspathPromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;

/**
 * Default slot schema loader backed by packaged classpath resources.
 *
 * @since 2026-06
 */
public final class DefaultClasspathClientSlotSchemaLoader implements ClientSlotSchemaLoader {

    private final ClasspathPromptSlotSchemaLoader delegate;

    /**
     * Creates a slot schema loader backed by one classpath resource loader.
     *
     * @param resourceLoader shared resource loader
     */
    public DefaultClasspathClientSlotSchemaLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.delegate = new ClasspathPromptSlotSchemaLoader(resourceLoader);
    }

    @Override
    public PromptSlotSchema loadSlotSchema(String scenarioCode, String language) {
        return delegate.loadSlotSchema(scenarioCode, language);
    }
}
