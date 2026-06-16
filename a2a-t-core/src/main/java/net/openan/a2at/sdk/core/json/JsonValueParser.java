package net.openan.a2at.sdk.core.json;

import java.util.Map;

/**
 * Shared abstraction for parsing structured JSON object payloads into key-value maps.
 *
 * @since 2026-06
 */
public interface JsonValueParser {

    /**
     * Parses one JSON object payload.
     *
     * @param payload JSON object payload
     * @return parsed key-value map
     */
    Map<String, Object> parseObject(String payload);
}
