package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.handler.ClarificationNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationHandlerTest {

    @Test
    void startReturnsFixedKeyMapAndSavesRecord() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationHandler handler = new NegotiationHandler(Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        Map<String, Object> payload = handler.start(
                NegotiationType.CLARIFICATION,
                "Please clarify the request.",
                Map.of("clarificationItems", new Object[] {"intent"}));

        assertEquals("Please clarify the request.", payload.get(NegotiationHandler.NEGOTIATION_TEXT_KEY));
        Map<String, Object> context = cast(payload.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));
        assertNotNull(context.get("negotiationId"));
        assertNotNull(store.get((String) context.get("negotiationId")));
    }

    @Test
    void continueMessageReturnsPayloadWithIncrementedRound() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationHandler handler = new NegotiationHandler(Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);
        Map<String, Object> startPayload =
                handler.start(NegotiationType.CLARIFICATION, "Please clarify the request.", Map.of());
        Map<String, Object> contextMap = cast(startPayload.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));
        NegotiationContext context = new NegotiationContext(
                NegotiationType.CLARIFICATION,
                (String) contextMap.get("negotiationId"),
                ((Number) contextMap.get("round")).intValue(),
                NegotiationStatus.IN_PROGRESS);

        Map<String, Object> payload =
                handler.continueMessage(context, NegotiationStatus.IN_PROGRESS, "Here is the clarification.");

        Map<String, Object> nextContext = cast(payload.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));
        assertEquals(2, ((Number) nextContext.get("round")).intValue());
        assertEquals("Here is the clarification.", payload.get(NegotiationHandler.NEGOTIATION_TEXT_KEY));
    }

    @Test
    void receiveReturnsNegotiationPayloadMap() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationHandler handler = new NegotiationHandler(Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);
        Map<String, Object> startPayload = handler.start(NegotiationType.CLARIFICATION, "Please clarify", Map.of());
        Map<String, Object> context = cast(startPayload.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));

        Map<String, Object> result = handler.receive("Clarify intent", context);

        assertTrue((Boolean) result.get("needResponse"));
        assertEquals("Clarify intent", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
