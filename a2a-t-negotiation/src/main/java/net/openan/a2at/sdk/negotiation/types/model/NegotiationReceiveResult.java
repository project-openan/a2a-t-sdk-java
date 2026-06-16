package net.openan.a2at.sdk.negotiation.types.model;

import java.util.Map;

/**
 * Result of processing one received negotiation message.
 *
 * @param needResponse whether the receiver should respond
 * @param facts structured facts extracted so far
 * @param message message content or guidance
 * @since 2026-06
 */
public record NegotiationReceiveResult(boolean needResponse, Map<String, Object> facts, String message) {}
