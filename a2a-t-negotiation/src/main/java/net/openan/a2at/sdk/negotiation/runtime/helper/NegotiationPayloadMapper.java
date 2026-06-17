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
        String rawType = (String) contextMap.get("negotiationType");
        String normalizedType = rawType.replace('-', '_').toUpperCase();
        String rawStatus = (String) contextMap.get("status");
        String normalizedStatus = rawStatus.replace('-', '_').toUpperCase();
        int round = ((Number) contextMap.get("round")).intValue();
        if (round <= 0) {
            throw new NegotiationStateException("Negotiation round must be a positive integer.");
        }
        return new NegotiationContext(
                NegotiationType.valueOf(normalizedType),
                (String) contextMap.get("negotiationId"),
                round,
                NegotiationStatus.valueOf(normalizedStatus));
    }
}
