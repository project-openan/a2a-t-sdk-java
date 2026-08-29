package net.openan.a2at.sample.negotiation.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sample.subscribe_incident.shared.error.ValueErrorException;

/**
 * Bridge between the A2A-T SDK payloads and the A2A {@code Message.metadata} carried over the real a2a-java HTTP
 * transport.
 *
 * <p>An A2A {@code Message.metadata} map carries one entry per active extension: the extension URI maps to the rendered
 * prompt text. The negotiation context map (type/id/round/status) is JSON-serialised into a dedicated
 * {@link DemoConstants#NEGOTIATION_CONTEXT_KEY} entry so the state machine can advance on the receiving side.
 *
 * @since 2026-08
 */
public final class NegotiationMessage {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NegotiationMessage() {}

    /**
     * Builds the A2A metadata map for one outbound negotiation message.
     *
     * @param extensionUri Negotiation-T or Task-T extension URI (the metadata key)
     * @param promptText rendered prompt text
     * @param templateUri template URI of the generated prompt
     * @param contextMap negotiation context map (may be empty for Task-T-only messages)
     * @return A2A metadata map
     */
    public static Map<String, Object> buildMetadata(
            String extensionUri, String promptText, String templateUri, Map<String, Object> contextMap) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(extensionUri, promptText);
        metadata.put(DemoConstants.TEMPLATE_URI_KEY, templateUri);
        if (contextMap != null && !contextMap.isEmpty()) {
            metadata.put(DemoConstants.NEGOTIATION_CONTEXT_KEY, toJson(contextMap));
        }
        return metadata;
    }

    /** Extracts the prompt text for one extension from an inbound A2A metadata map. */
    public static String extractPrompt(Map<String, Object> metadata, String extensionUri) {
        if (metadata == null) {
            return "";
        }
        Object value = metadata.get(extensionUri);
        return value == null ? "" : String.valueOf(value);
    }

    /** Extracts and deserialises the negotiation context map, or returns an empty map when absent. */
    public static Map<String, Object> extractContext(Map<String, Object> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        Object value = metadata.get(DemoConstants.NEGOTIATION_CONTEXT_KEY);
        if (value == null || (value instanceof String s && s.isBlank())) {
            return Map.of();
        }
        String json = value instanceof String s ? s : toJson(value);
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new ValueErrorException("Failed to parse negotiation context: " + exception.getMessage(), exception);
        }
    }

    /** Serialises a context map to JSON (exposed for the client when merging metadata). */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ValueErrorException(
                    "Failed to serialise negotiation context: " + exception.getMessage(), exception);
        }
    }
}
