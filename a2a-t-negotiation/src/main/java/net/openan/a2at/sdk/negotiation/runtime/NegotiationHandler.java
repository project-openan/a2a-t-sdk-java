package net.openan.a2at.sdk.negotiation.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.openan.a2at.sdk.negotiation.handler.Negotiation;
import net.openan.a2at.sdk.negotiation.runtime.helper.NegotiationPayloadMapper;
import net.openan.a2at.sdk.negotiation.store.NegotiationStore;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.types.model.*;

/**
 * Minimal negotiation handler that exposes fixed-key payload maps.
 *
 * @since 2026-06
 */
public final class NegotiationHandler {

    public static final String NEGOTIATION_CONTEXT_KEY =
            "https://github.com/a2aproject/telecommunication/extensions/DATA-NEGOTIATION-T/v1";

    public static final String NEGOTIATION_TEXT_KEY =
            "https://github.com/a2aproject/telecommunication/extensions/NEGOTIATION-T";

    private final NegotiationRuntime runtime;

    private final NegotiationStore store;

    /**
     * Creates a handler backed by explicit negotiation type registrations.
     *
     * @param negotiationTypes registered negotiation type handlers
     * @param store negotiation persistence store
     */
    public NegotiationHandler(Map<NegotiationType, Negotiation> negotiationTypes, NegotiationStore store) {
        this.runtime = new NegotiationRuntime(negotiationTypes, store);
        this.store = store;
    }

    /**
     * Creates a builder for assembling multi-type negotiation handlers.
     *
     * @return empty negotiation handler builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts a client-side negotiation payload.
     *
     * @param type negotiation type to initiate
     * @param contentText human-readable negotiation message
     * @param facts structured facts to include
     * @return transport payload for the initial turn
     */
    public Map<String, Object> start(NegotiationType type, String contentText, Map<String, Object> facts) {
        return start(NegotiationRole.CLIENT, type, contentText, facts);
    }

    public Map<String, Object> start(
            NegotiationRole role, NegotiationType type, String contentText, Map<String, Object> facts) {
        String negotiationId = UUID.randomUUID().toString();
        NegotiationContext context = new NegotiationContext(type, negotiationId, 1, NegotiationStatus.IN_PROGRESS);
        store.save(new NegotiationRecord(context, contentText));
        return NegotiationPayloadMapper.payload(context, contentText, facts);
    }

    public Map<String, Object> receive(String message, Map<String, Object> contextMap) {
        NegotiationContext context = NegotiationPayloadMapper.contextFromMap(contextMap);
        NegotiationReceiveResult result = runtime.receive(message, context);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("needResponse", result.needResponse());
        payload.put("facts", result.facts());
        payload.put("message", result.message());
        payload.put("context", NegotiationPayloadMapper.contextPayload(context));
        return payload;
    }

    /**
     * Continues one locally stored negotiation and emits the next payload.
     *
     * @param context current negotiation context
     * @param status next status to emit
     * @param contentText message content for the next turn
     * @return transport payload for the next turn
     */
    public Map<String, Object> continueMessage(
            NegotiationContext context, NegotiationStatus status, String contentText) {
        NegotiationRecord existing = store.get(context.negotiationId());
        if (existing != null && existing.context().round() != context.round()) {
            throw new NegotiationStateException("Negotiation context round does not match stored progress.");
        }
        NegotiationContext nextContext = runtime.continueMessage(context, status);
        return NegotiationPayloadMapper.payload(nextContext, contentText, Map.of());
    }

    public Map<String, Object> continueNegotiation(
            NegotiationContext context, NegotiationStatus status, String contentText) {
        return continueMessage(context, status, contentText);
    }

    /**
     * Builder for {@link NegotiationHandler}.
     *
     * @since 2026-05
     */
    public static final class Builder {
        private final Map<NegotiationType, Negotiation> negotiationTypes = new LinkedHashMap<>();

        private NegotiationStore store;

        /**
         * Configures the negotiation persistence store.
         *
         * @param store negotiation store
         * @return current builder
         */
        public Builder store(NegotiationStore store) {
            this.store = store;
            return this;
        }

        /**
         * Registers one handler for the supplied negotiation type.
         *
         * @param type negotiation type
         * @param handler type handler implementation
         * @return current builder
         */
        public Builder register(NegotiationType type, Negotiation handler) {
            negotiationTypes.put(type, handler);
            return this;
        }

        /**
         * Builds the negotiation handler.
         *
         * @return handler backed by the configured store and type registrations
         */
        public NegotiationHandler build() {
            if (store == null) {
                throw new IllegalStateException("Negotiation store must be configured.");
            }
            return new NegotiationHandler(Map.copyOf(negotiationTypes), store);
        }
    }
}
