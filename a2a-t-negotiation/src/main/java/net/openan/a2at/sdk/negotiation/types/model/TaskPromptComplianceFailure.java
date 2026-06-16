package net.openan.a2at.sdk.negotiation.types.model;

/**
 * Minimal task prompt compliance failure payload shared with server-side negotiation flows.
 *
 * @param code stable failure code
 * @param message human-readable failure message
 * @since 2026-06
 */
public record TaskPromptComplianceFailure(String code, String message) {}
