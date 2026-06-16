package net.openan.a2at.sdk.negotiation.store;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;

/**
 * Store abstraction for negotiation records.
 *
 * @since 2026-06
 */
public interface NegotiationStore {

    /**
     * Saves or replaces one negotiation record.
     *
     * @param record negotiation record to persist
     */
    void save(NegotiationRecord record);

    /**
     * Loads one negotiation record by identifier.
     *
     * @param negotiationId negotiation identifier
     * @return stored record or {@code null} when absent
     */
    NegotiationRecord get(String negotiationId);

    /**
     * Deletes one negotiation record by identifier.
     *
     * @param negotiationId negotiation identifier
     */
    void delete(String negotiationId);

    /**
     * Cleans expired records when the implementation supports expiration.
     *
     * @return {@code true} when any records were removed
     */
    boolean cleanupExpired();
}
