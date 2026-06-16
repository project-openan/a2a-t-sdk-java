package net.openan.a2at.sdk.negotiation.handler;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;

import java.util.Map;

/**
 * Minimal feasibility negotiation behavior.
 *
 * @since 2026-06
 */
public final class FeasibilityNegotiation implements NegotiationHandler {

    @Override
    public NegotiationReceiveResult processReceivedMessage(String message, NegotiationContext context) {
        boolean needResponse = context.status() == NegotiationStatus.IN_PROGRESS;
        return new NegotiationReceiveResult(needResponse, Map.of(), message);
    }
}
