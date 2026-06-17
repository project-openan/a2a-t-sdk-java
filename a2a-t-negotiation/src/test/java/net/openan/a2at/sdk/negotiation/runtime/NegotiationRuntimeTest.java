package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.handler.ClarificationNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationRuntimeTest {

    @Test
    void receiveAllowsFirstRoundWithoutExistingRecord() {
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()),
                new InMemoryNegotiationStore());

        NegotiationReceiveResult result = runtime.receive(
                "Clarify intent",
                new NegotiationContext(NegotiationType.CLARIFICATION, "neg-receive", 1, NegotiationStatus.IN_PROGRESS));

        assertTrue(result.needResponse());
        assertEquals("Clarify intent", result.message());
        assertTrue(result.facts().isEmpty());
    }

    @Test
    void continueMessageReturnsIncrementedRound() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);
        NegotiationContext context =
                new NegotiationContext(NegotiationType.CLARIFICATION, "neg-continue", 1, NegotiationStatus.IN_PROGRESS);
        store.save(new NegotiationRecord(context, "old"));

        NegotiationContext nextContext = runtime.continueMessage(context, NegotiationStatus.IN_PROGRESS);

        assertEquals(2, nextContext.round());
        assertEquals(NegotiationStatus.IN_PROGRESS, nextContext.status());
    }

    @Test
    void receiveRejectsIncomingRoundThatSkipsLocalProgress() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(NegotiationType.CLARIFICATION, "neg-skip", 2, NegotiationStatus.IN_PROGRESS),
                "old"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        assertThrows(
                NegotiationStateException.class,
                () -> runtime.receive(
                        "Clarify intent",
                        new NegotiationContext(
                                NegotiationType.CLARIFICATION, "neg-skip", 4, NegotiationStatus.IN_PROGRESS)));
    }
}
