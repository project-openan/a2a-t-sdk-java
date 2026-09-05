package net.openan.a2at.sdk.negotiation.content;

/**
 * Marker for the typed content of a terminal (accept or reject) negotiation message.
 *
 * @since 2026-08
 */
public sealed interface NegotiationEndingContent extends NegotiationContent
        permits InformationEndingContent, TargetEndingContent, FeasibilityEndingContent {

    /**
     * Returns the terminal conclusion this content carries.
     *
     * @return negotiation conclusion
     */
    NegotiationConclusion conclusion();
}
