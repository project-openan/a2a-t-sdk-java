package net.openan.a2at.sdk.negotiation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Outcome of the LLM-backed semantic validation of a negotiation message.
 *
 * <p>The semantic validation step combines the semantic verdict, the negotiation type implied by the message sections,
 * the structured semantic errors and the parameters extracted from the message per the caller-provided schema.
 *
 * @param verdict overall semantic verdict; {@code true} only when every semantic constraint holds
 * @param negotiationType negotiation type implied by the message sections, one of {@code information}, {@code target}
 *     or {@code feasibility}; may be null when the verdict is false
 * @param errors structured semantic errors using language-neutral {@code section.*} slot names; empty when the verdict
 *     is true
 * @param params parameters extracted from the message per the caller-provided schema
 * @since 2026-08
 */
public record SemanticValidationResult(
        boolean verdict, String negotiationType, List<SlotValidationError> errors, Map<String, Object> params) {

    /**
     * Normalizes the errors and the extracted parameters.
     *
     * @throws NullPointerException if the errors list or the params map is null
     */
    public SemanticValidationResult {
        errors = List.copyOf(errors);
        params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }
}
