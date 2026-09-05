package net.openan.a2at.sdk.negotiation.content;

/**
 * One named entry of a negotiation item list.
 *
 * @param name item name such as a field path or identifier; never blank in valid content
 * @param value free-form explanation of the item such as meaning, format, example or reason; may be null
 * @since 2026-08
 */
public record NegotiationItem(String name, String value) {}
