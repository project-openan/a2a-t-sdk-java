package net.openan.a2at.sdk.negotiation.content;

/**
 * Content of a target negotiation terminal message.
 *
 * <p>Exactly one of {@code confirmedIntent} and {@code failureReason} is meaningful, selected by the conclusion: an
 * accept message must carry the confirmed intent, a reject message must carry the failure reason.
 *
 * @param conclusion terminal conclusion of the target negotiation
 * @param confirmedIntent confirmed intent for an accept conclusion; null otherwise
 * @param failureReason reason for a reject conclusion; null otherwise
 * @since 2026-08
 */
public record TargetEndingContent(NegotiationConclusion conclusion, String confirmedIntent, String failureReason)
        implements NegotiationEndingContent {}
