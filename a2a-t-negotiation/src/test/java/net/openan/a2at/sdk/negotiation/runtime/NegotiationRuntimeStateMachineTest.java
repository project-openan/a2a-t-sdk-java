package net.openan.a2at.sdk.negotiation.runtime;

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

class NegotiationRuntimeStateMachineTest {

    @Test
    void continueMessageRejectsTerminalNegotiation() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.CLARIFICATION, "neg-terminal-continue", 2, NegotiationStatus.AGREED),
                "done"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        assertThrows(
                NegotiationStateException.class,
                () -> runtime.continueMessage(
                        new NegotiationContext(
                                NegotiationType.CLARIFICATION, "neg-terminal-continue", 2, NegotiationStatus.AGREED),
                        NegotiationStatus.IN_PROGRESS));
    }
}
