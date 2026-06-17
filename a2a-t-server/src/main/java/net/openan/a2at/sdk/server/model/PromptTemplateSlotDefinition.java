package net.openan.a2at.sdk.server.model;

/**
 * Slot definition used by template-based server prompt compliance checks.
 *
 * @param name slot name
 * @param required whether the slot is required
 * @since 2026-06
 */
public record PromptTemplateSlotDefinition(String name, boolean required) {}
