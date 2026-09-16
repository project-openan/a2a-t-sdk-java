package net.openan.a2at.sdk.negotiation.content;

import net.openan.a2at.sdk.core.model.NegotiationContext;

/**
 * Input bundle for generating a propose-phase negotiation message from typed data.
 *
 * @param context negotiation session context
 * @param content typed propose content matching the negotiation type addressed by the template URI
 * @since 2026-08
 */
public record NegotiationProposeData(NegotiationContext context, NegotiationProposeContent content) {}
