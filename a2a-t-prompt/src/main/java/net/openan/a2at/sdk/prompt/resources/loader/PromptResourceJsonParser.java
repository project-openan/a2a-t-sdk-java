package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared JSON parser for prompt resource documents.
 *
 * @since 2026-06
 */
final class PromptResourceJsonParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PromptResourceJsonParser() {}

    static <T> T parse(String payload, Class<T> type) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(payload, type);
    }
}
