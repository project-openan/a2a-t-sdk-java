package net.openan.a2at.sdk.negotiation.handler;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;

/**
 * Shared contract for negotiation handlers.
 *
 * @since 2026-06
 */
public interface Negotiation {

    /**
     * Processes one received negotiation message for the supplied context.
     *
     * @param message received message content
     * @param context current negotiation context
     * @return receive result describing whether a reply is needed
     */
    NegotiationReceiveResult processReceivedMessage(String message, NegotiationContext context);
}
