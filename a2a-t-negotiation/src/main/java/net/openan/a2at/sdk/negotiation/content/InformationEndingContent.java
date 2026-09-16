package net.openan.a2at.sdk.negotiation.content;

import java.util.List;

/**
 * Content of an information negotiation terminal message.
 *
 * @param conclusion terminal conclusion of the information negotiation
 * @param items information items delivered with the terminal message
 * @since 2026-08
 */
public record InformationEndingContent(NegotiationConclusion conclusion, List<NegotiationItem> items)
        implements NegotiationEndingContent {}
