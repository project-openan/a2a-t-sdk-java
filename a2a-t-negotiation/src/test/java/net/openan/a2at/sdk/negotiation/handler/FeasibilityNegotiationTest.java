package net.openan.a2at.sdk.negotiation.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class FeasibilityNegotiationTest {

    private final FeasibilityNegotiation negotiationType = new FeasibilityNegotiation();

    @Test
    void processReceivedMessagePassesThroughFactsAndMessage() {
        NegotiationReceiveResult result = negotiationType.processReceivedMessage(
                "check whether this task is feasible",
                new NegotiationContext(
                        NegotiationType.FEASIBILITY, "neg-feasibility", 1, NegotiationStatus.IN_PROGRESS));

        assertTrue(result.needResponse());
        assertEquals("check whether this task is feasible", result.message());
        assertTrue(result.facts().isEmpty());
    }

    @Test
    void processReceivedMessageStopsResponseWhenNegotiationIsTerminal() {
        NegotiationReceiveResult result = negotiationType.processReceivedMessage(
                "feasibility reached",
                new NegotiationContext(NegotiationType.FEASIBILITY, "neg-feasibility", 2, NegotiationStatus.AGREED));

        assertFalse(result.needResponse());
        assertEquals("feasibility reached", result.message());
    }
}
