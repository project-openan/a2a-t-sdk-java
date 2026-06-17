package net.openan.a2at.sdk.prompt.analysis.model;

/**
 * One slot validation error emitted by the structured extraction step.
 *
 * @param slotName slot name
 * @param code validation error code
 * @param message validation message
 * @since 2026-06
 */
public record StructuredSlotValidationError(String slotName, String code, String message) {}
