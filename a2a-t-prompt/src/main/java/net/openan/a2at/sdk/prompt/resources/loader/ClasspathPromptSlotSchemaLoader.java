package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.openan.a2at.sdk.core.exception.SdkException;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotJsonSchema;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;


/**
 * Loads shared slot schemas from packaged classpath prompt resources.
 *
 * @since 2026-06
 */
public final class ClasspathPromptSlotSchemaLoader implements PromptSlotSchemaLoader {

    private final ClasspathPromptResourceLoader resourceLoader;

    public ClasspathPromptSlotSchemaLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public PromptSlotSchema loadSlotSchema(String scenarioCode, String language) {
        String payload = resourceLoader.loadText(new PromptResourceKey("slots", scenarioCode, language, "slot.json"));
        try {
            return PromptResourceJsonParser.parse(payload, PromptSlotJsonSchema.class).toPromptSlotSchema(scenarioCode);
        } catch (JsonProcessingException exception) {
            throw new SdkException("Failed to parse slot schema: " + scenarioCode, exception);
        }
    }
}
