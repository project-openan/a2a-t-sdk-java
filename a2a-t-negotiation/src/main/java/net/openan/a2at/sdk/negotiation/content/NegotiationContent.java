package net.openan.a2at.sdk.negotiation.content;

/**
 * Marker for the typed content of any negotiation message, regardless of phase.
 *
 * <p>The concrete families are {@link NegotiationProposeContent} for propose-phase messages,
 * {@link NegotiationEndingContent} for terminal accept/reject messages and {@link NegotiationAbortContent} for the
 * type-independent abort message. Generators and content extractors accept this common supertype and dispatch on the
 * exact runtime type.
 *
 * @since 2026-08
 */
public sealed interface NegotiationContent permits NegotiationProposeContent, NegotiationEndingContent, NegotiationAbortContent {}
