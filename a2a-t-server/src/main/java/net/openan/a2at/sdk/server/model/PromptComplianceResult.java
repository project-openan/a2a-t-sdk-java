package net.openan.a2at.sdk.server.model;

/**
 * Typed result returned by server prompt-compliance checks.
 *
 * @param success whether prompt compliance passed
 * @param failure standardized failure payload, if compliance failed
 * @since 2026-06
 */
public record PromptComplianceResult(boolean success, PromptComplianceFailure failure) {}
