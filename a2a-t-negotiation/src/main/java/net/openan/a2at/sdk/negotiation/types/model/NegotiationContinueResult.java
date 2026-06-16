package net.openan.a2at.sdk.negotiation.types.model;

/**
 * Result of rendering a continue payload.
 *
 * @param promptText prompt text to send to the peer
 * @param finalTaskPrompt final task prompt when the negotiation is agreed
 * @since 2026-06
 */
public record NegotiationContinueResult(String promptText, String finalTaskPrompt) {}
