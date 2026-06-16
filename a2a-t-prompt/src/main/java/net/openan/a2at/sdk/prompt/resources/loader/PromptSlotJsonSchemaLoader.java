package net.openan.a2at.sdk.prompt.resources.loader;

import java.util.Map;

/**
 * Loads raw slot JSON schema payloads for downstream validation prompts.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface PromptSlotJsonSchemaLoader {

    /**
     * Loads one raw slot JSON schema document.
     *
     * @param scenarioCode scenario code
     * @param language resource language
     * @return parsed JSON schema object
     */
    Map<String, Object> loadSlotJsonSchema(String scenarioCode, String language);
}
