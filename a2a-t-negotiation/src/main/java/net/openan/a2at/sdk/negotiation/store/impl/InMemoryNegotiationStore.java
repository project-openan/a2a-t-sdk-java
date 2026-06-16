package net.openan.a2at.sdk.negotiation.store.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.openan.a2at.sdk.negotiation.store.NegotiationStore;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
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
        if (record == null || record.context() == null || record.context().negotiationId() == null
                || record.context().negotiationId().isEmpty()) {
            throw new NegotiationStateException("negotiation id is null or empty.");
        }
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
}
