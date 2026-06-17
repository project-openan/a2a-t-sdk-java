package net.openan.a2at.sdk.client.model;

/**
 * Standardized prompt-generation failure payload exposed by the public client facade.
 *
 * @param code stable failure code
 * @param message human-readable failure message
 * @param stage generation stage that produced the failure
 * @since 2026-06
 */
public record PromptGenerationFailure(String code, String message, String stage) {}
