package net.openan.a2at.sdk.negotiation.validation;

import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.SemanticValidator;
import net.openan.a2at.sdk.core.validation.TemplateContentLoader;
import net.openan.a2at.sdk.core.validation.ValidationPipeline;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.jspecify.annotations.Nullable;

/**
 * Orchestrates the validation of a negotiation message and the extraction of its parameters.
 *
 * <p>The extractor delegates to the shared {@link ValidationPipeline} from the core module, which runs the rule-level
 * gate, the template loading gate, retryable semantic validation and deterministic parameter merging in one pass. The
 * rule-level gate is a per-call {@link NegotiationRuleCheckerAdapter} bridging the negotiation context carried
 * alongside the message to the core rule checker contract; the injected {@link TemplateContentLoader} resolves the
 * template body after the rule gate and before semantic validation, so the template is never preloaded by the caller.
 * The pipeline emits the final {@link net.openan.a2at.sdk.core.exception.ErrorCatalog} codes with messages rendered in
 * the language of the negotiation reference; there is no code mapping layer between the pipeline steps and the surfaced
 * exception.
 *
 * @since 2026-08
 */
public final class ParamExtractor {

    private final NegotiationComplianceChecker complianceChecker;

    private final SemanticValidator<NegotiationReference> semanticValidator;

    private final int maxAttempts;

    private final TemplateContentLoader<NegotiationReference> templateContentLoader;

    /**
     * Creates a parameter extractor.
     *
     * @param complianceChecker rule-level checker used as the entry gate
     * @param semanticValidator LLM-backed semantic validator producing the semantic verdict and extracted parameters
     * @param maxAttempts maximum number of retry attempts for the semantic validation step
     * @param templateContentLoader template loading gate resolving the template body after the rule gate
     * @throws NullPointerException if any collaborator is null
     */
    public ParamExtractor(
            NegotiationComplianceChecker complianceChecker,
            SemanticValidator<NegotiationReference> semanticValidator,
            int maxAttempts,
            TemplateContentLoader<NegotiationReference> templateContentLoader) {
        this.complianceChecker = Objects.requireNonNull(complianceChecker, "complianceChecker");
        this.semanticValidator = Objects.requireNonNull(semanticValidator, "semanticValidator");
        this.maxAttempts = maxAttempts;
        this.templateContentLoader = Objects.requireNonNull(templateContentLoader, "templateContentLoader");
    }

    /**
     * Validates one negotiation message and extracts its parameters through the full pipeline.
     *
     * @param prompt rendered negotiation message text
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param reference template reference the message is validated against
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NegotiationParamExtractionException with the code {@code negotiation.invalid_input},
     *     {@code negotiation.rule_violation}, {@code negotiation.semantic_rejected}, {@code llm.invocation_failed},
     *     {@code llm.response_invalid} or {@code template.not_found} when the validation pipeline fails
     */
    public FilledParamData extract(
            String prompt,
            @Nullable NegotiationContext context,
            Map<String, Object> schema,
            NegotiationReference reference) {
        ValidationPipeline<NegotiationReference> pipeline = new ValidationPipeline<>(
                new NegotiationRuleCheckerAdapter(complianceChecker, context, reference.language()),
                semanticValidator,
                maxAttempts,
                reference.language(),
                templateContentLoader);
        try {
            return pipeline.validate(prompt, schema, reference);
        } catch (ContentValidationException e) {
            throw negotiationFailure(e, reference.language());
        }
    }

    private static NegotiationParamExtractionException negotiationFailure(
            ContentValidationException failure, String language) {
        // The pipeline renders the final message itself; only the structured facts are reconstructed from the first
        // slot detail that carries them, so callers keep the rendered message and gain the facts map.
        ErrorCatalog entry = ErrorCatalog.byCode(failure.getCode()).orElse(null);
        if (entry == null) {
            throw new NegotiationParamExtractionException(
                    failure.getCode(), failure.getMessage(), failure.errors(), failure);
        }
        Map<String, String> facts = failure.errors().stream()
                .map(SlotValidationError::facts)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new NegotiationParamExtractionException(
                entry.getCode(), failure.getMessage(), facts, failure.errors(), failure);
    }
}
