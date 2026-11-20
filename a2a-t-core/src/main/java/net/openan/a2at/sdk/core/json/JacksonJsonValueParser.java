package net.openan.a2at.sdk.core.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;

/**
 * Jackson-backed parser for structured JSON object payloads.
 *
 * @since 2026-06
 */
public final class JacksonJsonValueParser implements JsonValueParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Map<String, Object> parseObject(String payload) {
        try {
            Map<String, Object> parsed =
                    OBJECT_MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException error) {
            throw new A2ATError("Structured JSON payload must be a JSON object.", error);
        }
    }
}
