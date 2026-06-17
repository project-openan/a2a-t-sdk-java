package net.openan.a2at.sdk.server.model;

/**
 * Standardized prompt-compliance failure payload exposed by the public server facade.
 *
 * @param code stable failure code
 * @param message human-readable failure message
 * @param stage compliance stage that produced the failure
 * @since 2026-06
 */
public record PromptComplianceFailure(String code, String message, String stage) {}
