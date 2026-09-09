package net.openan.a2at.sdk.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATBusinessException;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Failure raised by the content validation pipeline.
 *
 * <p>Carries the machine-readable error code inherited from {@link A2ATBusinessException} and optionally structured
 * per-slot validation error details so callers can inspect which slot failed, under which error code, and why, without
 * parsing exception messages.
 *
 * @since 2026-08
 */
public class ContentValidationException extends A2ATBusinessException {

    private final List<SlotValidationError> errors;

    private final Map<String, Object> params;

    /**
     * Creates a content validation failure with an error code and one message.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     */
    public ContentValidationException(String code, String message) {
        this(code, message, List.of(), Map.of(), null);
    }

    /**
     * Creates a content validation failure with an error code, one message and slot details.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     */
    public ContentValidationException(String code, String message, List<SlotValidationError> errors) {
        this(code, message, errors, Map.of(), null);
    }

    /**
     * Creates a content validation failure with an error code, one message and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param cause root cause
     */
    public ContentValidationException(String code, String message, Throwable cause) {
        this(code, message, List.of(), Map.of(), cause);
    }

    public ContentValidationException(String code, String message, List<SlotValidationError> errors, Throwable cause) {
        this(code, message, errors, Map.of(), cause);
    }

    /**
     * Creates a content validation failure with an error code, one message, slot details, partial extraction params and
     * a root cause.
     *
     * <p>{@code params} carries the extraction result produced before rejection; the semantic validator deliberately
     * emits {@code null} values for slots it could not extract, so both the map and its values may be {@code null} and
     * must be accepted defensively. The stored copies are unmodifiable and preserve the original ordering.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     * @param params partial extraction params, may carry {@code null} values
     * @param cause root cause
     */
    public ContentValidationException(
            String code,
            String message,
            List<SlotValidationError> errors,
            Map<String, Object> params,
            Throwable cause) {
        super(code, message, null, cause);
        this.errors = errors == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(errors));
        this.params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    /**
     * Returns the structured per-slot validation error details.
     *
     * @return immutable list of slot validation errors, never null
     */
    public List<SlotValidationError> errors() {
        return errors;
    }

    /**
     * Returns the partial extraction params captured before rejection.
     *
     * @return unmodifiable map preserving insertion order, never null; values may be {@code null} for slots the
     *     semantic validator could not extract
     */
    public Map<String, Object> params() {
        return params;
    }
}
