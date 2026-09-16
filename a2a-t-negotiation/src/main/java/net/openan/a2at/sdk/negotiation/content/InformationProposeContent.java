package net.openan.a2at.sdk.negotiation.content;

import java.util.List;

/**
 * Content of an information negotiation propose message.
 *
 * @param items information items the requester is missing
 * @param relationship free-form description of how the missing items relate to each other; null when there is none
 * @since 2026-08
 */
public record InformationProposeContent(List<NegotiationItem> items, String relationship)
        implements NegotiationProposeContent {}
