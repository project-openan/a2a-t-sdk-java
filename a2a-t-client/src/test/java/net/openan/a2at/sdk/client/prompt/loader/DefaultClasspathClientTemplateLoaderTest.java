package net.openan.a2at.sdk.client.prompt.loader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import org.junit.jupiter.api.Test;

class DefaultClasspathClientTemplateLoaderTest {

    @Test
    void loadTemplateReadsTemplateFromClasspathPromptResources() {
        DefaultClasspathClientTemplateLoader loader =
                new DefaultClasspathClientTemplateLoader(new ClasspathPromptResourceLoader());

        String templateText = loader.loadTemplate("energy_saving", "zh-CN");

        assertFalse(templateText.isBlank());
        assertTrue(templateText.contains("{{任务对象}}"));
    }

    @Test
    void loadTemplatePropagatesTypedMissingResourceError() {
        DefaultClasspathClientTemplateLoader loader =
                new DefaultClasspathClientTemplateLoader(new ClasspathPromptResourceLoader());

        assertThrows(ResourceNotFoundException.class, () -> loader.loadTemplate("missing_scenario", "zh-CN"));
    }
}
