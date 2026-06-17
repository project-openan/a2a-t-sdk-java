package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.handler.ClarificationNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationRuntimeLimitsTest {

    @Test
    void receiveReturnsRejectGuidanceWhenIncomingRoundHitsLimit() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.CLARIFICATION,
                        "neg-round-limit",
                        NegotiationRuntime.MAX_IN_PROGRESS_NEGOTIATION_ROUND - 1,
                        NegotiationStatus.IN_PROGRESS),
                "old"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        NegotiationReceiveResult result = runtime.receive(
                "Clarify intent",
                new NegotiationContext(
                        NegotiationType.CLARIFICATION,
                        "neg-round-limit",
                        NegotiationRuntime.MAX_IN_PROGRESS_NEGOTIATION_ROUND,
                        NegotiationStatus.IN_PROGRESS));

        assertTrue(result.needResponse());
        assertEquals("Negotiation reached the maximum in-progress round limit. Please reject it.", result.message());
    }
}
