package net.openan.a2at.sdk.negotiation.generation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.validation.ParamExtractor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the negotiation content layer: deterministic message generation, single-step LLM content extraction and
 * the validation-plus-parameter-extraction pipeline.
 *
 * <p>The orchestrator owns the retry loop of every LLM step. A step failing with one of the retryable codes
 * {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} or {@code llm.response_invalid} is re-run
 * up to the configured attempt limit; the exhaustion failure rethrows the original error code. Internal exceptions of
 * the generation and validation pipelines never bubble out raw: render failures become generation failures carrying
 * {@code template.render_failed}, semantic validation infrastructure failures become parameter-extraction failures
 * carrying {@code llm.invocation_failed} or {@code llm.response_invalid}, and resource load misses become the code
 * {@code template.not_found} on both pipelines.
 *
 * <p>Instances are created through {@link NegotiationGenerationOrchestratorBuilder}; the builder wires the default
 * collaborators and allows overriding each of them.
 *
 * @since 2026-08
 */
public final class NegotiationGenerationOrchestrator {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(NegotiationGenerationOrchestrator.class);

    private static final String STEP_CONTENT_EXTRACT = "negotiation_content_extract";

    private final String language;

    private final int maxAttempts;

    private final int maxTextChars;

    private final NegotiationTemplateLoader templateLoader;

    private final NegotiationContentExtractor contentExtractor;

    private final ParamExtractor paramExtractor;

    private final NegotiationGeneratorRegistry generatorRegistry;

    private final Vocabulary vocabulary;

    private final Logger logger;

    NegotiationGenerationOrchestrator(
            String language,
            int maxAttempts,
            int maxTextChars,
            NegotiationTemplateLoader templateLoader,
            NegotiationContentExtractor contentExtractor,
            ParamExtractor paramExtractor,
            NegotiationGeneratorRegistry generatorRegistry,
            Vocabulary vocabulary,
            Logger logger) {
        this.language = language;
        this.maxAttempts = maxAttempts;
        this.maxTextChars = maxTextChars;
        this.templateLoader = templateLoader;
        this.contentExtractor = contentExtractor;
        this.paramExtractor = paramExtractor;
        this.generatorRegistry = generatorRegistry;
        this.vocabulary = vocabulary;
        this.logger = logger == null ? DEFAULT_LOGGER : logger;
    }

    /**
     * Generates a propose-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM: the typed content is validated, dispatched to the
     * generator of the negotiation type addressed by the template URI and rendered from that template.
     *
     * @param data typed propose input carrying the negotiation context and the typed content
     * @param templateUri template URI such as {@code Negotiation-T/information-negotiation/propose/v1}; its phase
     *     segment must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI;
     *     the emitted negotiation context is the input context stamped with the performative of the addressed template,
     *     so it may differ from the context the caller passed in
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase or type contradicts the method or the content type
     * @throws NegotiationGenerationException with the code {@code template.not_found} when no template exists for the
     *     URI in any resource root, or the code {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateProposeFromData(
            @NonNull NegotiationProposeData data, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(data, "Negotiation propose data must not be null.");
        return generateFromData(data.context(), data.content(), templateUri, NegotiationPerformative.PROPOSE);
    }

    /**
     * Generates an accept-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. The content conclusion must be {@code Accept}; a
     * mismatched conclusion is a content error.
     *
     * @param data typed terminal input carrying the negotiation context and the typed ending content
     * @param templateUri template URI such as {@code Negotiation-T/information-negotiation/accept-reject/v1}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI;
     *     the emitted negotiation context is the input context stamped with the performative of the addressed template,
     *     so it may differ from the context the caller passed in
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase or type contradicts the method or the content
     * @throws NegotiationGenerationException with the code {@code negotiation.conclusion_mismatch} when the content
     *     conclusion is not {@code Accept}, or {@code negotiation.content_invalid} when a required content field is
     *     blank or empty
     * @throws NegotiationGenerationException with the code {@code template.not_found} when no template exists for the
     *     URI in any resource root, or the code {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateAcceptFromData(
            @NonNull NegotiationEndingData data, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(data, "Negotiation ending data must not be null.");
        return generateFromData(data.context(), data.content(), templateUri, NegotiationPerformative.ACCEPT);
    }

    /**
     * Generates a reject-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. The content conclusion must be {@code Reject}; a
     * mismatched conclusion is a content error.
     *
     * @param data typed terminal input carrying the negotiation context and the typed ending content
     * @param templateUri template URI such as {@code Negotiation-T/feasibility-negotiation/accept-reject/v1}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI;
     *     the emitted negotiation context is the input context stamped with the performative of the addressed template,
     *     so it may differ from the context the caller passed in
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase or type contradicts the method or the content
     * @throws NegotiationGenerationException with the code {@code negotiation.conclusion_mismatch} when the content
     *     conclusion is not {@code Reject}, or {@code negotiation.content_invalid} when a required content field is
     *     blank or empty
     * @throws NegotiationGenerationException with the code {@code template.not_found} when no template exists for the
     *     URI in any resource root, or the code {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateRejectFromData(
            @NonNull NegotiationEndingData data, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(data, "Negotiation ending data must not be null.");
        return generateFromData(data.context(), data.content(), templateUri, NegotiationPerformative.REJECT);
    }

    /**
     * Generates an abort negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. Abort messages are type-independent: the addressed
     * template must be the common abort template and the content carries only the termination reason.
     *
     * @param data typed abort input carrying the negotiation context and the termination reason
     * @param templateUri template URI of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI;
     *     the emitted negotiation context is the input context stamped with the performative of the addressed template,
     *     so it may differ from the context the caller passed in
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI does not address the common abort template
     * @throws NegotiationGenerationException with the code {@code negotiation.content_invalid} when the termination
     *     reason is blank
     * @throws NegotiationGenerationException with the code {@code template.not_found} when no template exists for the
     *     URI in any resource root, or the code {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateAbortFromData(@NonNull NegotiationAbortData data, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(data, "Negotiation abort data must not be null.");
        return generateFromData(data.context(), data.content(), templateUri, NegotiationPerformative.ABORT);
    }

    /**
     * Generates a propose-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extraction step
     * is retried up to the configured attempt limit on the retryable failure codes
     * {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} and {@code llm.response_invalid}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri template URI such as {@code Negotiation-T/target-negotiation/propose/v1}; its phase segment
     *     must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI;
     *     the emitted negotiation context is the input context stamped with the performative of the addressed template,
     *     so it may differ from the context the caller passed in
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase contradicts the method
     * @throws NegotiationGenerationException with the code {@code template.not_found} when no template or prompt
     *     resource exists for the URI and language, {@code negotiation.content_extract_failed} or
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the extraction step fails after exhausting
     *     its retries, {@code negotiation.field_missing} when the extracted content misses a required field, or
     *     {@code negotiation.invalid_input} when the text is blank or the extracted content contradicts the phase,
     *     {@code template.render_failed} when rendering the template fails, or
     *     {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateProposeFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return generateFromText(text, context, templateUri, NegotiationPerformative.PROPOSE);
    }

    /**
     * Generates an accept-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extracted
     * conclusion must be {@code Accept}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri template URI such as {@code Negotiation-T/information-negotiation/accept-reject/v1}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI;
     *     the emitted negotiation context is the input context stamped with the performative of the addressed template,
     *     so it may differ from the context the caller passed in
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase contradicts the method
     * @throws NegotiationGenerationException with the code {@code template.not_found} when no template or prompt
     *     resource exists for the URI and language, {@code negotiation.content_extract_failed} or
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the extraction step fails after exhausting
     *     its retries, {@code negotiation.field_missing} when the extracted content misses a required field, or
     *     {@code negotiation.invalid_input} when the text is blank, {@code negotiation.conclusion_mismatch} when the
     *     extracted conclusion is not {@code Accept}, {@code template.render_failed} when rendering the template
     *     fails, or {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateAcceptFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return generateFromText(text, context, templateUri, NegotiationPerformative.ACCEPT);
    }

    /**
     * Generates a reject-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extracted
     * conclusion must be {@code Reject}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri template URI such as {@code Negotiation-T/feasibility-negotiation/accept-reject/v1}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI;
     *     the emitted negotiation context is the input context stamped with the performative of the addressed template,
     *     so it may differ from the context the caller passed in
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase contradicts the method
     * @throws NegotiationGenerationException with the code {@code template.not_found} when no template or prompt
     *     resource exists for the URI and language, {@code negotiation.content_extract_failed} or
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the extraction step fails after exhausting
     *     its retries, {@code negotiation.field_missing} when the extracted content misses a required field, or
     *     {@code negotiation.invalid_input} when the text is blank, {@code negotiation.conclusion_mismatch} when the
     *     extracted conclusion is not {@code Reject}, {@code template.render_failed} when rendering the template
     *     fails, or {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateRejectFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return generateFromText(text, context, templateUri, NegotiationPerformative.REJECT);
    }

    /**
     * Generates an abort negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the common abort template and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extraction step
     * is retried up to the configured attempt limit on the retryable failure codes
     * {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} and {@code llm.response_invalid}.
     *
     * @param text free-text input stating the termination reason
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri template URI of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI;
     *     the emitted negotiation context is the input context stamped with the performative of the addressed template,
     *     so it may differ from the context the caller passed in
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI does not address the common abort template
     * @throws NegotiationGenerationException with the code {@code template.not_found} when no template or prompt
     *     resource exists for the URI and language, {@code negotiation.content_extract_failed} or
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the extraction step fails after exhausting
     *     its retries, {@code negotiation.field_missing} when the extracted content misses the termination reason, or
     *     {@code negotiation.invalid_input} when the text is blank, {@code template.render_failed} when rendering the
     *     template fails, or {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateAbortFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return generateFromText(text, context, templateUri, NegotiationPerformative.ABORT);
    }

    /**
     * Validates a propose-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline checks the template URI before any LLM call, runs the deterministic rule gate, then performs one
     * semantic validation LLM call (retried on the retryable failure codes) and merges the extracted parameters with
     * the rule-level context parameters; context parameters win on conflict.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected negotiation type and phase; its phase segment must be
     *     {@code propose}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation.invalid_input} when the prompt is
     *     null or blank, or when the context is null and the message is therefore not a negotiation message,
     *     {@code negotiation.rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the message,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, or {@code template.not_found} when the semantic validation prompt resources are missing, or
     *     {@code input.text_too_long} when the prompt exceeds the configured maximum length
     */
    public FilledParamData validateProposePromptAndDataFilling(
            String prompt,
            NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        return validatePromptAndDataFilling(prompt, context, schema, templateUri, NegotiationPerformative.PROPOSE);
    }

    /**
     * Validates an accept-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateProposePromptAndDataFilling(String, NegotiationContext, Map,
     * TemplateUri)} with the expected phase fixed to accept: the template URI must declare the {@code accept-reject}
     * segment and the message must satisfy the accept-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected negotiation type and phase; its phase segment must be
     *     {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation.invalid_input} when the prompt is
     *     null or blank, or when the context is null and the message is therefore not a negotiation message,
     *     {@code negotiation.rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the message,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, or {@code template.not_found} when the semantic validation prompt resources are missing, or
     *     {@code input.text_too_long} when the prompt exceeds the configured maximum length
     */
    public FilledParamData validateAcceptPromptAndDataFilling(
            String prompt,
            NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        return validatePromptAndDataFilling(prompt, context, schema, templateUri, NegotiationPerformative.ACCEPT);
    }

    /**
     * Validates a reject-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateProposePromptAndDataFilling(String, NegotiationContext, Map,
     * TemplateUri)} with the expected phase fixed to reject: the template URI must declare the {@code accept-reject}
     * segment and the message must satisfy the reject-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected negotiation type and phase; its phase segment must be
     *     {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI's phase contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation.invalid_input} when the prompt is
     *     null or blank, or when the context is null and the message is therefore not a negotiation message,
     *     {@code negotiation.rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the message,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, or {@code template.not_found} when the semantic validation prompt resources are missing, or
     *     {@code input.text_too_long} when the prompt exceeds the configured maximum length
     */
    public FilledParamData validateRejectPromptAndDataFilling(
            String prompt,
            NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        return validatePromptAndDataFilling(prompt, context, schema, templateUri, NegotiationPerformative.REJECT);
    }

    /**
     * Validates an abort negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateProposePromptAndDataFilling(String, NegotiationContext, Map,
     * TemplateUri)} with the expected phase fixed to abort: the template URI must address the common abort template and
     * the message must satisfy the abort-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI does not address the common abort template
     * @throws NegotiationParamExtractionException with the code {@code negotiation.invalid_input} when the prompt is
     *     null or blank, or when the context is null and the message is therefore not a negotiation message,
     *     {@code negotiation.rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the message,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, or {@code template.not_found} when the semantic validation prompt resources are missing, or
     *     {@code input.text_too_long} when the prompt exceeds the configured maximum length
     */
    public FilledParamData validateAbortPromptAndDataFilling(
            String prompt,
            NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        return validatePromptAndDataFilling(prompt, context, schema, templateUri, NegotiationPerformative.ABORT);
    }

    private MetadataContent generateFromData(
            NegotiationContext context,
            NegotiationContent content,
            TemplateUri templateUri,
            NegotiationPerformative performative) {
        requireContext(context);
        try {
            NegotiationReference reference = requireReference(templateUri, performative);
            PromptTemplate template = loadTemplate(reference);
            String promptText = renderMessage(context, content, reference, template);
            return completeGeneration(reference, promptText, context);
        } catch (NegotiationGenerationException failure) {
            logger.atWarn()
                    .log("negotiation_generation_failed code={} template_uri={}", failure.getCode(), templateUri.uri());
            throw failure;
        }
    }

    private MetadataContent generateFromText(
            String text, NegotiationContext context, TemplateUri templateUri, NegotiationPerformative performative) {
        requireContext(context);
        requireTextWithinLimit(text);
        try {
            NegotiationReference reference = requireReference(templateUri, performative);
            PromptTemplate template = loadTemplate(reference);
            NegotiationContent content = extractContent(text, reference);
            String promptText = renderMessage(context, content, reference, template);
            return completeGeneration(reference, promptText, context);
        } catch (NegotiationGenerationException failure) {
            logger.atWarn()
                    .log("negotiation_generation_failed code={} template_uri={}", failure.getCode(), templateUri.uri());
            throw failure;
        }
    }

    private PromptTemplate loadTemplate(NegotiationReference reference) {
        try {
            return templateLoader.load(reference);
        } catch (ResourceNotFoundException exception) {
            throw templateNotFound(reference, exception);
        }
    }

    private NegotiationContent extractContent(String text, NegotiationReference reference) {
        try {
            return withRetry(STEP_CONTENT_EXTRACT, () -> contentExtractor.extract(text, reference));
        } catch (ResourceNotFoundException exception) {
            throw templateNotFound(reference, exception);
        }
    }

    private static NegotiationGenerationException templateNotFound(
            NegotiationReference reference, ResourceNotFoundException cause) {
        return new NegotiationGenerationException(
                ErrorCatalog.TEMPLATE_NOT_FOUND,
                reference.language(),
                Map.of("template_uri", reference.uri(), "language", reference.language()),
                cause);
    }

    private String renderMessage(
            NegotiationContext context,
            NegotiationContent content,
            NegotiationReference reference,
            PromptTemplate template) {
        NegotiationGenerator generator =
                generatorRegistry.resolve(reference.type(), reference.performative(), content, reference.language());
        try {
            return generator.generate(context, content, template, vocabulary);
        } catch (NegotiationRenderException exception) {
            throw new NegotiationGenerationException(
                    ErrorCatalog.TEMPLATE_RENDER_FAILED,
                    reference.language(),
                    Map.of(
                            "template_uri", reference.uri(),
                            "reason", String.valueOf(exception.getMessage())),
                    exception);
        }
    }

    private MetadataContent completeGeneration(
            NegotiationReference reference, String promptText, NegotiationContext context) {
        // The operation is the source of truth for the performative: the emitted context is the input context stamped
        // with the performative of the addressed template, overriding whatever the caller passed in.
        NegotiationContext stampedContext = context.withPerformative(reference.performative());
        logger.atInfo().log(
                "negotiation_generation_completed uri={} type={} performative={} round={} id={}",
                reference.uri(),
                reference.type(),
                reference.performative(),
                stampedContext.round(),
                stampedContext.id());
        return new MetadataContent(
                reference.uri(), promptText, ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI, stampedContext);
    }

    private FilledParamData validatePromptAndDataFilling(
            String prompt,
            NegotiationContext context,
            Map<String, Object> schema,
            TemplateUri templateUri,
            NegotiationPerformative performative) {
        Objects.requireNonNull(schema, "Parameter schema must not be null.");
        requirePromptWithinLimit(prompt);
        NegotiationReference reference = requireReference(templateUri, performative);
        try {
            return paramExtractor.extract(prompt, context, schema, reference);
        } catch (NegotiationParamExtractionException failure) {
            logger.atWarn()
                    .log(
                            "negotiation_param_extraction_failed code={} error_count={}",
                            failure.getCode(),
                            failure.getErrors().size());
            throw failure;
        }
    }

    /**
     * Derives the reference of a typed template URI against one expected performative.
     *
     * @param templateUri template URI declared by the caller
     * @param performative performative the calling method operates on
     * @return reference addressed by the URI carrying the expected performative
     * @throws IllegalArgumentException if the URI does not address a negotiation template of the expected performative
     */
    private NegotiationReference requireReference(TemplateUri templateUri, NegotiationPerformative performative) {
        return NegotiationReference.fromTemplateUri(templateUri, performative, language)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Template URI does not address a negotiation template of the expected performative "
                                + performative + " (" + NegotiationReference.uriSegmentOf(performative) + "): "
                                + templateUri.uri() + "."));
    }

    /**
     * Runs one LLM step with the retry policy of the negotiation content layer.
     *
     * <p>A failure carrying a retryable code is re-run up to the configured attempt limit; a failure carrying any other
     * code is rethrown immediately. When the attempts are exhausted, the original failure is rethrown with its original
     * error code.
     *
     * @param <T> result type of the step
     * @param step internal diagnostic step name used in the retry logs
     * @param action step implementation performing exactly one LLM call
     * @return step result
     */
    private <T> T withRetry(String step, Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException failure) {
                String code = retryableCode(failure);
                if (code == null) {
                    throw failure;
                }
                lastFailure = failure;
                if (attempt == maxAttempts) {
                    logger.atWarn()
                            .log(
                                    "negotiation_llm_retry_exhausted step={} max_attempts={} code={}",
                                    step,
                                    maxAttempts,
                                    code);
                    throw failure;
                }
                logger.atWarn()
                        .log(
                                "negotiation_llm_retry step={} attempt={} max_attempts={} code={}",
                                step,
                                attempt,
                                maxAttempts,
                                code);
            }
        }
        throw lastFailure;
    }

    private static String retryableCode(RuntimeException failure) {
        String code = null;
        if (failure instanceof NegotiationGenerationException generation) {
            code = generation.getCode();
        } else if (failure instanceof NegotiationParamExtractionException extraction) {
            code = extraction.getCode();
        }
        if (code == null) {
            return null;
        }
        boolean retryable =
                ErrorCatalog.NEGOTIATION_CONTENT_EXTRACT_FAILED.getCode().equals(code)
                        || ErrorCatalog.LLM_INVOCATION_FAILED.getCode().equals(code)
                        || ErrorCatalog.LLM_RESPONSE_INVALID.getCode().equals(code);
        return retryable ? code : null;
    }

    private static void requireContext(NegotiationContext context) {
        Objects.requireNonNull(context, "Negotiation context must not be null.");
    }

    private void requireTextWithinLimit(String text) {
        if (InputLimitConfig.isTooLong(text, maxTextChars)) {
            throw new NegotiationGenerationException(
                    ErrorCatalog.INPUT_TEXT_TOO_LONG, language, inputTooLongFacts(text));
        }
    }

    private void requirePromptWithinLimit(String prompt) {
        if (InputLimitConfig.isTooLong(prompt, maxTextChars)) {
            throw new NegotiationParamExtractionException(
                    ErrorCatalog.INPUT_TEXT_TOO_LONG, language, inputTooLongFacts(prompt), List.of());
        }
    }

    private Map<String, String> inputTooLongFacts(String text) {
        return Map.of(
                "actual_length", String.valueOf(text == null ? 0 : text.length()),
                "max_chars", String.valueOf(maxTextChars));
    }
}
