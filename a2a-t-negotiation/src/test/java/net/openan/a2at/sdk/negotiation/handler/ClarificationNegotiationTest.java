package net.openan.a2at.sdk.negotiation.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class ClarificationNegotiationTest {

    private final ClarificationNegotiation negotiationType = new ClarificationNegotiation();

    @Test
    void processReceivedMessagePassesThroughFactsAndMessage() {
        NegotiationReceiveResult result = negotiationType.processReceivedMessage(
                "clarify this",
                new NegotiationContext(NegotiationType.CLARIFICATION, "neg-1", 1, NegotiationStatus.IN_PROGRESS));

        assertTrue(result.needResponse());
        assertEquals("clarify this", result.message());
        assertTrue(result.facts().isEmpty());
    }

    @Test
    void processReceivedMessageStopsResponseWhenNegotiationIsTerminal() {
        NegotiationReceiveResult result = negotiationType.processReceivedMessage(
                "clarify this",
                new NegotiationContext(NegotiationType.CLARIFICATION, "neg-2", 2, NegotiationStatus.AGREED));

        assertFalse(result.needResponse());
        assertEquals("clarify this", result.message());
    }
}
