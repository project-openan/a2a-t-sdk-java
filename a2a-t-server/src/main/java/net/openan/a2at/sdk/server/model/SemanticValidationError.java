package net.openan.a2at.sdk.server.model;

/**
 * Semantic validation error detail for server-side prompt compliance.
 *
 * @param slotName slot name associated with the error
 * @param code stable validation error code
 * @param message human-readable validation error message
 * @since 2026-06
 */
public record SemanticValidationError(String slotName, String code, String message) {}
