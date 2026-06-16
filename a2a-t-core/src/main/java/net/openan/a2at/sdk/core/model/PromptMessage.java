package net.openan.a2at.sdk.core.model;

/**
 * Shared chat-style prompt message model.
 *
 * @param role message role
 * @param content message content
 * @since 2026-06
 */
public record PromptMessage(String role, String content) {}
