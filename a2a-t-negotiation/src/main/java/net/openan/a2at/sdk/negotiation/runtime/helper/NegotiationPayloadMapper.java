package net.openan.a2at.sdk.negotiation.runtime.helper;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;

/**
 * Shared helpers for negotiation payload and context map conversion.
 *
 * @since 2026-06
 */
public final class NegotiationPayloadMapper {

    public static Map<String, Object> payload(
            NegotiationContext context, String contentText, Map<String, Object> facts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(NegotiationHandler.NEGOTIATION_TEXT_KEY, contentText);
        payload.put(NegotiationHandler.NEGOTIATION_CONTEXT_KEY, contextPayload(context));
        if (!facts.isEmpty()) {
            payload.put("facts", facts);
        }
        return payload;
    }

    public static Map<String, Object> contextPayload(NegotiationContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "negotiationType",
                context.negotiationType().name().toLowerCase().replace('_', '-'));
        payload.put("negotiationId", context.negotiationId());
        payload.put("round", context.round());
        payload.put("status", context.status().name().toLowerCase().replace('_', '-'));
        payload.put("extra", Map.of());
        return payload;
    }

    public static NegotiationContext contextFromMap(Map<String, Object> contextMap) {
        String rawType = stringValue(contextMap, "negotiationType");
        String normalizedType = rawType.replace('-', '_').toUpperCase();
        String rawStatus = stringValue(contextMap, "status");
        String normalizedStatus = rawStatus.replace('-', '_').toUpperCase();
        int round = numberValue(contextMap, "round").intValue();
        if (round <= 0) {
            throw new NegotiationStateException("Negotiation round must be a positive integer.");
        }
        return new NegotiationContext(
                NegotiationType.valueOf(normalizedType),
                stringValue(contextMap, "negotiationId"),
                round,
                NegotiationStatus.valueOf(normalizedStatus));
    }

    private static String stringValue(Map<String, Object> contextMap, String key) {
        Object value = contextMap.get(key);
        if (value instanceof String text) {
            return text;
        }
        throw new NegotiationStateException("Negotiation context field must be a string: " + key);
    }

    private static Number numberValue(Map<String, Object> contextMap, String key) {
        Object value = contextMap.get(key);
        if (value instanceof Number number) {
            return number;
        }
        throw new NegotiationStateException("Negotiation context field must be a number: " + key);
    }
}
