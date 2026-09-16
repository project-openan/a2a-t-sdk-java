package net.openan.a2at.sdk.negotiation.content;

/**
 * Content of a feasibility negotiation terminal message.
 *
 * @param conclusion terminal conclusion of the feasibility negotiation
 * @param feasibilitySummary required summary of the feasibility evaluation result
 * @since 2026-08
 */
public record FeasibilityEndingContent(NegotiationConclusion conclusion, String feasibilitySummary)
        implements NegotiationEndingContent {}
