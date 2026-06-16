package net.openan.a2at.sdk.negotiation.store.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.openan.a2at.sdk.negotiation.store.NegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;

/**
 * In-memory negotiation store for early SDK iterations.
 *
 * @since 2026-06
 */
public final class InMemoryNegotiationStore implements NegotiationStore {

    private final Map<String, NegotiationRecord> records = new ConcurrentHashMap<>();

    @Override
    public void save(NegotiationRecord record) {
        records.put(record.context().negotiationId(), record);
    }

    @Override
    public NegotiationRecord get(String negotiationId) {
        return records.get(negotiationId);
    }

    @Override
    public void delete(String negotiationId) {
        records.remove(negotiationId);
    }

    @Override
    public boolean cleanupExpired() {
        return true;
    }
}
