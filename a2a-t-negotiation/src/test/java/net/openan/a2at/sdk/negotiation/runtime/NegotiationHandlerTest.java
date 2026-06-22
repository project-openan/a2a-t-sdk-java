package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.handler.ClarificationNegotiation;
import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationHandlerTest {

    @Test
    void startReturnsFixedKeyMapAndSavesRecord() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationHandler handler =
                new NegotiationHandler(Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        Map<String, Object> payload = handler.start(
                NegotiationType.CLARIFICATION,
                "Please clarify the request.",
                Map.of("clarificationItems", new Object[] {"intent"}));

        assertEquals("Please clarify the request.", payload.get(NegotiationHandler.NEGOTIATION_TEXT_KEY));
        Map<String, Object> context = cast(payload.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));
        assertNotNull(context.get("negotiationId"));
        assertNotNull(store.get(stringValue(context.get("negotiationId"))));
    }

    @Test
    void continueMessageReturnsPayloadWithIncrementedRound() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationHandler handler =
                new NegotiationHandler(Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);
        Map<String, Object> startPayload =
                handler.start(NegotiationType.CLARIFICATION, "Please clarify the request.", Map.of());
        Map<String, Object> contextMap = cast(startPayload.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));
        NegotiationContext context = new NegotiationContext(
                NegotiationType.CLARIFICATION,
                stringValue(contextMap.get("negotiationId")),
                numberValue(contextMap.get("round")).intValue(),
                NegotiationStatus.IN_PROGRESS);

        Map<String, Object> payload =
                handler.continueMessage(context, NegotiationStatus.IN_PROGRESS, "Here is the clarification.");

        Map<String, Object> nextContext = cast(payload.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));
        assertEquals(2, numberValue(nextContext.get("round")).intValue());
        assertEquals("Here is the clarification.", payload.get(NegotiationHandler.NEGOTIATION_TEXT_KEY));
    }

    @Test
    void receiveReturnsNegotiationPayloadMap() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationHandler handler =
                new NegotiationHandler(Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);
        Map<String, Object> startPayload = handler.start(NegotiationType.CLARIFICATION, "Please clarify", Map.of());
        Map<String, Object> context = cast(startPayload.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));

        Map<String, Object> result = handler.receive("Clarify intent", context);

        assertTrue(booleanValue(result.get("needResponse")));
        assertEquals("Clarify intent", result.get("message"));
    }

    private static Map<String, Object> cast(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> stringValue(entry.getKey()), Map.Entry::getValue));
        }
        throw new AssertionError("Expected map value but was: " + value);
    }

    private static String stringValue(Object value) {
        if (value instanceof String text) {
            return text;
        }
        throw new AssertionError("Expected string value but was: " + value);
    }

    private static Number numberValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new AssertionError("Expected number value but was: " + value);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new AssertionError("Expected boolean value but was: " + value);
    }
}
