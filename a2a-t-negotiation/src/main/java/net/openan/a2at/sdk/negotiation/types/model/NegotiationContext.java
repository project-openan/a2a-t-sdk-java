package net.openan.a2at.sdk.negotiation.types.model;

/**
 * Shared negotiation context.
 *
 * @param negotiationType negotiation type
 * @param negotiationId negotiation identifier
 * @param round current round
 * @param status negotiation status
 * @since 2026-06
 */
public record NegotiationContext(
        NegotiationType negotiationType, String negotiationId, int round, NegotiationStatus status) {}
