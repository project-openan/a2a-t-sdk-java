package net.openan.a2at.sdk.client.prompt.loader;

import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;

/**
 * Loads slot schema definitions for client-side prompt generation.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface ClientSlotSchemaLoader {

    /**
     * Loads the slot schema for one scenario and language.
     *
     * @param scenarioCode scenario code to resolve
     * @param language locale identifier to resolve
     * @return slot schema for the requested scenario
     */
    PromptSlotSchema loadSlotSchema(String scenarioCode, String language);
}
