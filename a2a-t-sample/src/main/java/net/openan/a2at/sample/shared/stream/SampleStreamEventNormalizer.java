package net.openan.a2at.sample.shared.stream;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.shared.error.ValueErrorException;

/**
 * Normalizes raw stream payload fragments into stable sample event shapes.
 *
 * @since 2026-05
 */
public final class SampleStreamEventNormalizer {

    private SampleStreamEventNormalizer() {
    }

    public static Map<String, Object> normalize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new ValueErrorException("Unsupported SSE payload: " + payload);
        }

        Object statusValue = payload.get("status");
        if (statusValue instanceof Map<?, ?> status) {
            return Map.of("kind", "status", "state", status.get("state"));
        }

        Object messageValue = payload.get("message");
        if (messageValue instanceof Map<?, ?> message) {
            Object partsValue = message.get("parts");
            if (partsValue instanceof List<?> parts) {
                for (Object part : parts) {
                    if (part instanceof Map<?, ?> partMap && partMap.containsKey("text")) {
                        return Map.of("kind", "message", "text", String.valueOf(partMap.get("text")));
                    }
                }
            }
            return Map.of("kind", "message", "text", "");
        }

        if (payload.containsKey("artifact")) {
            return Map.of("kind", "artifact", "artifact", payload.get("artifact"));
        }

        throw new ValueErrorException("Unsupported SSE payload: " + payload);
    }
}


