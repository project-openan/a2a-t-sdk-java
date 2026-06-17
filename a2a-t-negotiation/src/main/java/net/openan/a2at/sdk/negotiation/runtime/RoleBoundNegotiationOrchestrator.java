package net.openan.a2at.sdk.negotiation.runtime;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRole;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;

/**
 * Role-bound negotiation orchestrator shared by client and server facades.
 *
 * @since 2026-06
 */
public final class RoleBoundNegotiationOrchestrator {

    private final NegotiationHandler handler;

    private final NegotiationRole role;

    /**
     * Creates a role-bound negotiation orchestrator.
     *
     * @param handler shared negotiation handler facade
     * @param role local role that this orchestrator represents
     */
    public RoleBoundNegotiationOrchestrator(NegotiationHandler handler, NegotiationRole role) {
        this.handler = handler;
        this.role = role;
    }

    /**
     * Starts a negotiation for the orchestrator's bound role.
     *
     * @param type negotiation type to initiate
     * @param contentText human-readable negotiation message
     * @param facts structured facts attached to the payload
     * @return transport payload for the initial turn
     */
    public Map<String, Object> startNegotiation(NegotiationType type, String contentText, Map<String, Object> facts) {
        return handler.start(role, type, contentText, facts);
    }

    /**
     * Processes a received negotiation message.
     *
     * @param message received negotiation message
     * @param context transport context payload
     * @return normalized receive payload
     */
    public Map<String, Object> receiveNegotiation(String message, Map<String, Object> context) {
        return handler.receive(message, context);
    }

    /**
     * Continues a negotiation using one local context snapshot.
     *
     * @param context current negotiation context
     * @param status next status to emit
     * @param contentText message content for the next turn
     * @return transport payload for the next turn
     */
    public Map<String, Object> continueNegotiation(
            NegotiationContext context, NegotiationStatus status, String contentText) {
        return handler.continueNegotiation(context, status, contentText);
    }
}
