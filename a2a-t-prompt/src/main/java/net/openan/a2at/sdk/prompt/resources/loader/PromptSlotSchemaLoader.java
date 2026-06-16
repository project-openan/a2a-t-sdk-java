package net.openan.a2at.sdk.prompt.resources.loader;

import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;

/**
 * Loads a shared slot schema for one scenario and language.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface PromptSlotSchemaLoader {

    /**
     * Loads one shared slot schema.
     *
     * @param scenarioCode scenario code
     * @param language resource language
     * @return slot schema
     */
    PromptSlotSchema loadSlotSchema(String scenarioCode, String language);
}
