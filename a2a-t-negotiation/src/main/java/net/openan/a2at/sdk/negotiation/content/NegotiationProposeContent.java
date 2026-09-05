package net.openan.a2at.sdk.negotiation.content;

/**
 * Marker for the typed content of a propose-phase negotiation message.
 *
 * @since 2026-08
 */
public sealed interface NegotiationProposeContent extends NegotiationContent
        permits InformationProposeContent, TargetProposeContent, FeasibilityProposeContent {}
