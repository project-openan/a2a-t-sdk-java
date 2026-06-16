package net.openan.a2at.sdk.negotiation.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class FulfillmentNegotiationTest {

    private final FulfillmentNegotiation negotiationType = new FulfillmentNegotiation();

    @Test
    void processReceivedMessagePassesThroughFactsAndMessage() {
        NegotiationReceiveResult result = negotiationType.processReceivedMessage(
                "deliver the final task output",
                new NegotiationContext(
                        NegotiationType.FULFILLMENT, "neg-fulfillment", 1, NegotiationStatus.IN_PROGRESS));

        assertTrue(result.needResponse());
        assertEquals("deliver the final task output", result.message());
        assertTrue(result.facts().isEmpty());
    }

    @Test
    void processReceivedMessageStopsResponseWhenNegotiationIsTerminal() {
        NegotiationReceiveResult result = negotiationType.processReceivedMessage(
                "fulfillment reached",
                new NegotiationContext(NegotiationType.FULFILLMENT, "neg-fulfillment", 2, NegotiationStatus.REJECTED));

        assertFalse(result.needResponse());
        assertEquals("fulfillment reached", result.message());
    }
}
