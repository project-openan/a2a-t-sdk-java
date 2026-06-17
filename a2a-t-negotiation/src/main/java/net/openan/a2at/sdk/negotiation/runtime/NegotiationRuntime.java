package net.openan.a2at.sdk.negotiation.runtime;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.store.NegotiationStore;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import net.openan.a2at.sdk.negotiation.handler.Negotiation;

/**
 * Minimal runtime coordinator for one negotiation type.
 *
 * @since 2026-06
 */
public final class NegotiationRuntime {

    public static final int MAX_IN_PROGRESS_NEGOTIATION_ROUND = 8;

    private final Map<NegotiationType, Negotiation> negotiationHandlerMap;

    private final NegotiationStore store;

    /**
     * Creates a negotiation runtime from registered type handlers and one persistence store.
     *
     * @param negotiationHandlerMap registered negotiation type handlers
     * @param store negotiation persistence store
     */
    public NegotiationRuntime(
            Map<NegotiationType, Negotiation> negotiationHandlerMap,
            NegotiationStore store) {
        this.negotiationHandlerMap = negotiationHandlerMap;
        this.store = store;
    }

    /**
     * Processes a received negotiation message against the local store snapshot.
     *
     * @param message received message content
     * @param context incoming negotiation context
     * @return receive result produced by the matching negotiation type handler
     */
    public NegotiationReceiveResult receive(String message, NegotiationContext context) {
        NegotiationRecord existing = store.get(context.negotiationId());
        if (existing != null && context.round() < existing.context().round()) {
            throw new NegotiationStateException("Incoming negotiation round is older than local progress.");
        }
        if (existing != null
                && existing.context().status() != NegotiationStatus.IN_PROGRESS
                && context.status() == NegotiationStatus.IN_PROGRESS) {
            throw new NegotiationStateException("Terminal negotiation cannot return to in-progress state.");
        }
        if (existing != null
                && existing.context().status() != NegotiationStatus.IN_PROGRESS
                && context.status() != existing.context().status()) {
            throw new NegotiationStateException("Terminal negotiation status cannot change once finalized.");
        }
        if (existing != null
                && existing.context().status() == NegotiationStatus.IN_PROGRESS
                && context.status() == NegotiationStatus.IN_PROGRESS
                && context.round() >= MAX_IN_PROGRESS_NEGOTIATION_ROUND) {
            return new NegotiationReceiveResult(
                    true,
                    Map.of(),
                    "Negotiation reached the maximum in-progress round limit. Please reject it.");
        }
        if (existing != null && context.round() > existing.context().round() + 1) {
            throw new NegotiationStateException("Incoming negotiation round skips local progress.");
        }
        // Handler dispatch happens only after all state checks pass, so one bad payload cannot mutate the local store.
        Negotiation negotiation = negotiationHandlerMap.get(context.negotiationType());
        if (negotiation == null) {
            throw new NegotiationStateException("Unsupported negotiation type: " + context.negotiationType());
        }
        NegotiationReceiveResult result = negotiation.processReceivedMessage(message, context);
        store.save(new NegotiationRecord(context, message));
        return result;
    }

    /**
     * Advances a locally stored negotiation to its next round.
     *
     * @param context current negotiation context
     * @param nextStatus next status to persist
     * @return next persisted negotiation context
     */
    public NegotiationContext continueMessage(NegotiationContext context, NegotiationStatus nextStatus) {
        NegotiationRecord existing = store.get(context.negotiationId());
        if (existing == null) {
            throw new NegotiationStateException("Cannot continue a negotiation that has not been stored.");
        }
        if (!existing.context().equals(context)) {
            throw new NegotiationStateException("Negotiation context does not match stored progress.");
        }
        if (existing.context().status() != NegotiationStatus.IN_PROGRESS) {
            throw new NegotiationStateException("Cannot continue a negotiation that is already terminal.");
        }
        NegotiationContext nextContext = new NegotiationContext(
                context.negotiationType(), context.negotiationId(), context.round() + 1, nextStatus);
        store.save(new NegotiationRecord(nextContext, existing.lastMessage()));
        return nextContext;
    }
}
