package net.openan.a2at.sdk.negotiation.types.model;

/**
 * Minimal stored record for negotiation progress.
 *
 * @param context latest negotiation context
 * @param lastMessage latest negotiation message
 * @since 2026-06
 */
public record NegotiationRecord(NegotiationContext context, String lastMessage) {}
