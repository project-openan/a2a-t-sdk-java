package net.openan.a2at.sdk.core.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized machine-readable error code constants shared across A2A-T SDK processing failures.
 *
 * @deprecated superseded by {@link ErrorCatalog}; each constant forwards to the layered {@code domain.semantic} code of
 *     the catalog entry that replaced it. Kept only while the corpus and sample modules reference the old constant
 *     names; new code must use {@link ErrorCatalog} directly.
 * @since 2026-08
 */
@Deprecated(forRemoval = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class A2ATErrorCodes {

    /** Default code for SDK processing failures without a more specific code. */
    @Deprecated(forRemoval = true)
    public static final String SDK_INTERNAL_ERROR = ErrorCatalog.INFRA_INTERNAL_ERROR.getCode();

    /** A free-text input exceeded the configured maximum length. */
    @Deprecated(forRemoval = true)
    public static final String INPUT_TEXT_TOO_LONG = ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode();

    /** Default code for parameter-extraction failures without a more specific code. */
    @Deprecated(forRemoval = true)
    public static final String PARAM_EXTRACTION_FAILED = ErrorCatalog.SLOT_NOT_PROVIDED.getCode();

    /** A referenced template could not be resolved. */
    @Deprecated(forRemoval = true)
    public static final String TEMPLATE_NOT_FOUND = ErrorCatalog.TEMPLATE_NOT_FOUND.getCode();

    /** Structured content could not be extracted from a free-text input. */
    @Deprecated(forRemoval = true)
    public static final String NEGOTIATION_CONTENT_EXTRACT_FAILED =
            ErrorCatalog.NEGOTIATION_CONTENT_EXTRACT_FAILED.getCode();

    /** Semantic validation rejected the input. */
    @Deprecated(forRemoval = true)
    public static final String NEGOTIATION_SEMANTIC_REJECTED = ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode();

    /** One or more structural rules of the expected template were violated. */
    @Deprecated(forRemoval = true)
    public static final String NEGOTIATION_RULE_VIOLATION = ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode();

    /** A required slot is missing from the input. */
    @Deprecated(forRemoval = true)
    public static final String NEGOTIATION_SLOT_MISSING = ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode();

    /** The input is not valid for the requested operation. */
    @Deprecated(forRemoval = true)
    public static final String NEGOTIATION_INVALID_INPUT = ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode();

    /** An LLM infrastructure failure prevented the step from completing. */
    @Deprecated(forRemoval = true)
    public static final String NEGOTIATION_LLM_INFRASTRUCTURE_ERROR = ErrorCatalog.LLM_INVOCATION_FAILED.getCode();

    /** The input is not valid for the requested validation operation. */
    @Deprecated(forRemoval = true)
    public static final String VALIDATION_INVALID_INPUT = ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode();

    /** One or more structural rules of the expected content were violated. */
    @Deprecated(forRemoval = true)
    public static final String VALIDATION_RULE_VIOLATION = ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode();

    /** Semantic validation rejected the content. */
    @Deprecated(forRemoval = true)
    public static final String VALIDATION_SEMANTIC_REJECTED = ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode();

    /** An LLM infrastructure failure prevented the validation step from completing. */
    @Deprecated(forRemoval = true)
    public static final String VALIDATION_LLM_INFRASTRUCTURE_ERROR = ErrorCatalog.LLM_INVOCATION_FAILED.getCode();

    /** A referenced prompt resource could not be resolved. */
    @Deprecated(forRemoval = true)
    public static final String VALIDATION_PROMPT_RESOURCE_NOT_FOUND = ErrorCatalog.TEMPLATE_NOT_FOUND.getCode();

    /** A prompt resource could not be loaded from its configured source. */
    @Deprecated(forRemoval = true)
    public static final String PROMPT_RESOURCE_LOAD_ERROR = ErrorCatalog.TEMPLATE_LOAD_FAILED.getCode();

    /** The slot schema describing the expected slots could not be resolved. */
    @Deprecated(forRemoval = true)
    public static final String SLOT_SCHEMA_NOT_FOUND = ErrorCatalog.SLOT_SCHEMA_NOT_FOUND.getCode();

    /** An LLM invocation failed at the infrastructure level. */
    @Deprecated(forRemoval = true)
    public static final String LLM_INVOCATION_FAILED = ErrorCatalog.LLM_INVOCATION_FAILED.getCode();

    /** Rendering a template with its slot values failed. */
    @Deprecated(forRemoval = true)
    public static final String RENDER_FAILED = ErrorCatalog.TEMPLATE_RENDER_FAILED.getCode();

    /** The slot values extracted or provided for a template failed validation. */
    @Deprecated(forRemoval = true)
    public static final String SLOT_VALIDATION_ERROR = ErrorCatalog.SLOT_RULE_VIOLATION.getCode();

    /** A processed prompt could not be parsed into its structured parts. */
    @Deprecated(forRemoval = true)
    public static final String PROCESSED_PROMPT_PARSE_ERROR = ErrorCatalog.SCENARIO_NOT_MATCHED.getCode();
}
