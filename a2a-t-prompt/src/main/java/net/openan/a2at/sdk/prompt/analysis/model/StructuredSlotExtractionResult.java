package net.openan.a2at.sdk.prompt.analysis.model;

import java.util.List;
import java.util.Map;

/**
 * Shared structured slot extraction result.
 *
 * @param slots normalized slot values
 * @param slotErrors extraction-time validation errors
 * @since 2026-06
 */
public record StructuredSlotExtractionResult(
        Map<String, String> slots, List<StructuredSlotValidationError> slotErrors) {}
