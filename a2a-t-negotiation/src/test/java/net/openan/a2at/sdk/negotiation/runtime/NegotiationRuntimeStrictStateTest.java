package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.handler.ClarificationNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationRuntimeStrictStateTest {

    @Test
    void receiveRejectsIncomingRoundOlderThanLocalProgress() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.CLARIFICATION, "neg-round-regress", 3, NegotiationStatus.IN_PROGRESS),
                "latest"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        NegotiationStateException error = assertThrows(
                NegotiationStateException.class,
                () -> runtime.receive(
                        "stale",
                        new NegotiationContext(
                                NegotiationType.CLARIFICATION, "neg-round-regress", 2, NegotiationStatus.IN_PROGRESS)));

        assertEquals("Incoming negotiation round is older than local progress.", error.getMessage());
    }

    @Test
    void receiveRejectsTerminalContextReturningToInProgress() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.CLARIFICATION, "neg-terminal-regress", 2, NegotiationStatus.AGREED),
                "done"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        assertThrows(
                NegotiationStateException.class,
                () -> runtime.receive(
                        "Clarify intent",
                        new NegotiationContext(
                                NegotiationType.CLARIFICATION,
                                "neg-terminal-regress",
                                3,
                                NegotiationStatus.IN_PROGRESS)));
    }

    @Test
    void receiveRejectsChangingOneTerminalStatusToAnother() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.CLARIFICATION, "neg-terminal-switch", 2, NegotiationStatus.AGREED),
                "done"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        NegotiationStateException error = assertThrows(
                NegotiationStateException.class,
                () -> runtime.receive(
                        "switch terminal",
                        new NegotiationContext(
                                NegotiationType.CLARIFICATION, "neg-terminal-switch", 3, NegotiationStatus.REJECTED)));

        assertEquals("Terminal negotiation status cannot change once finalized.", error.getMessage());
    }

    @Test
    void continueMessageRejectsRepeatedTerminalProgression() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.CLARIFICATION, "neg-terminal-repeat", 3, NegotiationStatus.REJECTED),
                "done"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        assertThrows(
                NegotiationStateException.class,
                () -> runtime.continueMessage(
                        new NegotiationContext(
                                NegotiationType.CLARIFICATION, "neg-terminal-repeat", 3, NegotiationStatus.REJECTED),
                        NegotiationStatus.REJECTED));
    }

    @Test
    void continueMessageRejectsContextRoundThatDoesNotMatchStoredProgress() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.CLARIFICATION, "neg-continue-mismatch", 3, NegotiationStatus.IN_PROGRESS),
                "latest"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        NegotiationStateException error = assertThrows(
                NegotiationStateException.class,
                () -> runtime.continueMessage(
                        new NegotiationContext(
                                NegotiationType.CLARIFICATION,
                                "neg-continue-mismatch",
                                2,
                                NegotiationStatus.IN_PROGRESS),
                        NegotiationStatus.IN_PROGRESS));

        assertEquals("Negotiation context does not match stored progress.", error.getMessage());
    }
}
