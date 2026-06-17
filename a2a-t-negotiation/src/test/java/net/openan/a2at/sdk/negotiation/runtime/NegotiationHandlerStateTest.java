package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.handler.ClarificationNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

import java.util.Map;

class NegotiationHandlerStateTest {

    @Test
    void continueMessageRejectsContextRoundMismatch() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.CLARIFICATION, "neg-continue-mismatch", 2, NegotiationStatus.IN_PROGRESS),
                "old"));
        NegotiationHandler handler = new NegotiationHandler(Map.of(NegotiationType.CLARIFICATION, new ClarificationNegotiation()), store);

        assertThrows(
                NegotiationStateException.class,
                () -> handler.continueMessage(
                        new NegotiationContext(
                                NegotiationType.CLARIFICATION,
                                "neg-continue-mismatch",
                                1,
                                NegotiationStatus.IN_PROGRESS),
                        NegotiationStatus.IN_PROGRESS,
                        "Here is the clarification."));
    }
}
