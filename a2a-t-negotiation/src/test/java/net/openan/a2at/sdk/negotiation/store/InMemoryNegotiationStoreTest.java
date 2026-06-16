package net.openan.a2at.sdk.negotiation.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class InMemoryNegotiationStoreTest {

    @Test
    void storeSavesGetsAndDeletesRecords() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationRecord record = new NegotiationRecord(
                new NegotiationContext(NegotiationType.FULFILLMENT, "neg-store", 1, NegotiationStatus.IN_PROGRESS),
                "message");

        store.save(record);

        assertEquals(record, store.get("neg-store"));

        store.delete("neg-store");

        assertNull(store.get("neg-store"));
    }

    @Test
    void cleanupExpiredAlwaysReturnsTrueForInMemoryStore() {
        assertTrue(new InMemoryNegotiationStore().cleanupExpired());
    }
}
