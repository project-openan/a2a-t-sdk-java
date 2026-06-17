package net.openan.a2at.sdk.server.model;

import java.util.List;

/**
 * Semantic validation result contract for server-side prompt compliance.
 *
 * @param passed whether semantic validation passed
 * @param errors semantic validation error details
 * @since 2026-06
 */
public record SemanticValidationResult(boolean passed, List<SemanticValidationError> errors) {}
