package net.openan.a2at.sdk.negotiation.validation;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.SemanticValidator;
import net.openan.a2at.sdk.core.validation.ValidationResult;
import net.openan.a2at.sdk.llm.LLMConfigError;
import net.openan.a2at.sdk.llm.LLMError;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;

/**
 * LLM-backed semantic validator for negotiation messages.
 *
 * <p>The validator performs a single structured LLM call that combines semantic validation with parameter extraction
 * and then enforces the declared type consistency in code: when the verdict is true, the negotiation type reported for
 * the message must be present and must match the type declared by the template reference. A response that misses one of
 * the four required keys or has the wrong shape is a validation infrastructure failure signalled through the internal
 * {@link NegotiationValidationException}.
 *
 * @since 2026-08
 */
public interface NegotiationSemanticValidator extends SemanticValidator<NegotiationReference> {

    /**
     * Validates one rendered negotiation message semantically and extracts its parameters.
     *
     * @param prompt rendered negotiation message text
     * @param callerSchema caller-provided parameter JSON schema embedded into the structured-call output contract
     * @param reference negotiation reference the message is validated against, carrying the declared type, phase and
     *     language
     * @param templateContent loaded template text used as a reference for structure/completeness checks
     * @return semantic validation outcome carrying the verdict, the implied negotiation type, the semantic errors and
     *     the extracted parameters
     * @throws NegotiationValidationException if the response misses a required key or has the wrong shape
     * @throws net.openan.a2at.sdk.llm.LLMError if the LLM invocation fails at the transport level
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if the semantic validation prompt resources
     *     of the reference language are missing
     */
    SemanticValidationResult validateNegotiation(
            String prompt, Map<String, Object> callerSchema, NegotiationReference reference, String templateContent);

    @Override
    default ValidationResult validate(
            String prompt, Map<String, Object> schema, NegotiationReference reference, String templateContent) {
        try {
            SemanticValidationResult result = validateNegotiation(prompt, schema, reference, templateContent);
            return new ValidationResult(result.verdict(), result.errors(), result.params());
        } catch (LLMConfigError exception) {
            // A missing LLM configuration never recovers within this call.
            throw new ContentValidationException(
                    ErrorCatalog.LLM_NOT_CONFIGURED.getCode(),
                    ErrorMessages.render(ErrorCatalog.LLM_NOT_CONFIGURED, reference.language(), null),
                    exception);
        } catch (LLMError exception) {
            Map<String, String> facts = Map.of(
                    "provider", exception.getClass().getSimpleName(),
                    "reason", String.valueOf(exception.getMessage()));
            String message = ErrorMessages.render(ErrorCatalog.LLM_INVOCATION_FAILED, reference.language(), facts);
            throw new ContentValidationException(
                    ErrorCatalog.LLM_INVOCATION_FAILED.getCode(),
                    message,
                    List.of(new SlotValidationError(
                            "_llm", ErrorCatalog.LLM_INVOCATION_FAILED.getCode(), message, facts)),
                    exception);
        } catch (NegotiationValidationException exception) {
            Map<String, String> facts = Map.of("step", "semantic_validation");
            String message = ErrorMessages.render(ErrorCatalog.LLM_RESPONSE_INVALID, reference.language(), facts);
            throw new ContentValidationException(
                    ErrorCatalog.LLM_RESPONSE_INVALID.getCode(),
                    message,
                    List.of(new SlotValidationError(
                            "_llm", ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), message, facts)),
                    exception);
        }
    }
}
