package net.openan.a2at.sdk.server;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateQueryService;
import net.openan.a2at.sdk.server.assembly.DefaultA2ATServerBuilder;
import net.openan.a2at.sdk.server.compliance.ServerPromptComplianceOrchestrator;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;
import org.jspecify.annotations.NonNull;

/**
 * High-level server facade for prompt compliance and negotiation APIs. The caller provides the `.env` file path
 * explicitly, typically after copying the repository `env.example`.
 *
 * @since 2026-06
 */
public final class A2ATServer {

    private final ServerPromptComplianceOrchestrator promptComplianceOrchestrator;

    private final RoleBoundNegotiationOrchestrator negotiationOrchestrator;

    private final NegotiationContentService negotiationContentService;

    private final TemplateQueryService templateQueryService;

    private final ContentValidator taskContentValidator;

    private final ContentValidator notificationContentValidator;

    private final ContentValidator authContentValidator;

    /**
     * Creates a server facade from one user-supplied `.env` path.
     *
     * @param envPath user-supplied `.env` file path
     */
    public A2ATServer(Path envPath) {
        Path resolvedEnvPath = envPath.toAbsolutePath().normalize();
        A2ATConfig config =
                A2ATConfig.resolvePromptResourceLocalRootDir(A2ATConfig.load(resolvedEnvPath), resolvedEnvPath);
        DefaultA2ATServerBuilder builder =
                DefaultA2ATServerBuilder.builder().envPath(resolvedEnvPath).config(config);
        this.promptComplianceOrchestrator = builder.buildPromptComplianceOrchestrator();
        this.negotiationOrchestrator = builder.buildNegotiationOrchestrator();
        this.negotiationContentService =
                new NegotiationContentService(builder.buildNegotiationGenerationOrchestrator());
        this.templateQueryService = builder.buildTemplateQueryService();
        this.taskContentValidator = builder.buildTaskContentValidator();
        this.notificationContentValidator = builder.buildNotificationContentValidator();
        this.authContentValidator = builder.buildAuthContentValidator();
    }

    /**
     * Checks one processed task prompt for server-side compliance.
     *
     * <p>An input longer than {@code A2AT_INPUT_TEXT_MAX_CHARS} characters (default 16384) fails fast with the code
     * {@code input.text_too_long} before any LLM call.
     *
     * @param processedPromptText processed task prompt text
     * @return compliance result containing only success state or failure details
     */
    public PromptComplianceResult checkTaskPrompt(@NonNull String processedPromptText) {
        return promptComplianceOrchestrator.checkTaskPrompt(processedPromptText);
    }

    /**
     * Starts a new negotiation payload for the requested negotiation type.
     *
     * @param type negotiation type to initiate
     * @param contentText human-readable negotiation message
     * @param facts structured facts attached to the payload
     * @return transport payload representing the initial negotiation turn
     */
    public Map<String, Object> startNegotiation(
            @NonNull NegotiationType type, @NonNull String contentText, @NonNull Map<String, Object> facts) {
        return negotiationOrchestrator.startNegotiation(type, contentText, facts);
    }

    /**
     * Processes a received negotiation message using its transport context payload.
     *
     * @param message received negotiation message
     * @param context transport context payload associated with the message
     * @return normalized payload describing the receive result
     */
    public Map<String, Object> receiveNegotiation(@NonNull String message, @NonNull Map<String, Object> context) {
        return negotiationOrchestrator.receiveNegotiation(message, context);
    }

    /**
     * Continues an existing negotiation with a locally stored context snapshot.
     *
     * @param context current negotiation context
     * @param status next status to emit
     * @param contentText continuation message content
     * @return transport payload representing the next negotiation turn
     */
    public Map<String, Object> continueNegotiation(
            @NonNull NegotiationContext context, @NonNull NegotiationStatus status, @NonNull String contentText) {
        return negotiationOrchestrator.continueNegotiation(context, status, contentText);
    }

    /**
     * Generates a propose-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM: the typed content is validated, dispatched to the
     * generator of the negotiation type addressed by the template URI and rendered from that template.
     *
     * @param data typed propose input carrying the negotiation context and the typed content
     * @param templateUri raw template URI string such as {@code Negotiation-T/information-negotiation/propose/v1};
     *     its performative segment must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase or type contradicts the
     *     method or the content type
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template exists for the URI in any resource root, the code
     *     {@code negotiation.content_invalid} when a required content field is blank or empty, the code
     *     {@code negotiation.invalid_input} when the content combines a non-blank confirm request with conditional
     *     sections, or the code {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateNegotiationProposePromptFromData(
            @NonNull NegotiationProposeData data, @NonNull String templateUri) {
        return negotiationContentService.generateProposeFromData(data, parseTemplateUri(templateUri));
    }

    /**
     * Generates an accept-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. The content conclusion must be {@code Accept}; a
     * mismatched conclusion is a content error.
     *
     * @param data typed terminal input carrying the negotiation context and the typed ending content
     * @param templateUri raw template URI string such as {@code Negotiation-T/information-negotiation/accept-reject/v1};
     *     its performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase or type contradicts the
     *     method or the content
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template exists for the URI in any resource root, the code
     *     {@code negotiation.conclusion_mismatch} when the content conclusion is not {@code Accept}, the code
     *     {@code negotiation.content_invalid} when a required content field is blank or empty, or the code
     *     {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateNegotiationAcceptPromptFromData(
            @NonNull NegotiationEndingData data, @NonNull String templateUri) {
        return negotiationContentService.generateAcceptFromData(data, parseTemplateUri(templateUri));
    }

    /**
     * Generates a reject-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. The content conclusion must be {@code Reject}; a
     * mismatched conclusion is a content error.
     *
     * @param data typed terminal input carrying the negotiation context and the typed ending content
     * @param templateUri raw template URI string such as {@code Negotiation-T/feasibility-negotiation/accept-reject/v1};
     *     its performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase or type contradicts the
     *     method or the content
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template exists for the URI in any resource root, the code
     *     {@code negotiation.conclusion_mismatch} when the content conclusion is not {@code Reject}, the code
     *     {@code negotiation.content_invalid} when a required content field is blank or empty, or the code
     *     {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateNegotiationRejectPromptFromData(
            @NonNull NegotiationEndingData data, @NonNull String templateUri) {
        return negotiationContentService.generateRejectFromData(data, parseTemplateUri(templateUri));
    }

    /**
     * Generates an abort negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. Abort messages are type-independent: the addressed
     * template must be the common abort template and the content carries only the termination reason.
     *
     * @param data typed abort input carrying the negotiation context and the termination reason
     * @param templateUri raw template URI string of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank, malformed or does not address the common abort
     *     template
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template exists for the URI in any resource root, the code
     *     {@code negotiation.content_invalid} when the termination reason is blank, or the code
     *     {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateNegotiationAbortPromptFromData(
            @NonNull NegotiationAbortData data, @NonNull String templateUri) {
        return negotiationContentService.generateAbortFromData(data, parseTemplateUri(templateUri));
    }

    /**
     * Generates a propose-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extraction step
     * is retried up to the configured attempt limit on the retryable failure codes
     * {@code negotiation.content_extract_failed} and {@code llm.invocation_failed}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri raw template URI string such as {@code Negotiation-T/target-negotiation/propose/v1}; its
     *     performative segment must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} or {@code llm.response_invalid}
     *     when the extraction step fails after exhausting its retries, {@code negotiation.field_missing} when the
     *     extracted content misses a required field, {@code negotiation.invalid_input} when the text is blank or the
     *     extracted content contradicts the phase, or {@code input.text_too_long} when the text exceeds the configured
     *     maximum length (A2AT_INPUT_TEXT_MAX_CHARS, default 16384)
     */
    public MetadataContent generateNegotiationProposePromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.@NonNull NegotiationContext context,
            @NonNull String templateUri) {
        return negotiationContentService.generateProposeFromText(text, context, parseTemplateUri(templateUri));
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
     * @param templateUri raw template URI string such as {@code Negotiation-T/information-negotiation/accept-reject/v1};
     *     its performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} or {@code llm.response_invalid}
     *     when the extraction step fails after exhausting its retries, {@code negotiation.field_missing} when the
     *     extracted content misses a required field, {@code negotiation.invalid_input} when the text is blank,
     *     {@code negotiation.conclusion_mismatch} when the extracted conclusion is not {@code Accept}, or
     *     {@code input.text_too_long} when the text exceeds the configured maximum length (A2AT_INPUT_TEXT_MAX_CHARS,
     *     default 16384)
     */
    public MetadataContent generateNegotiationAcceptPromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.@NonNull NegotiationContext context,
            @NonNull String templateUri) {
        return negotiationContentService.generateAcceptFromText(text, context, parseTemplateUri(templateUri));
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
     * @param templateUri raw template URI string such as {@code Negotiation-T/feasibility-negotiation/accept-reject/v1};
     *     its performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} or {@code llm.response_invalid}
     *     when the extraction step fails after exhausting its retries, {@code negotiation.field_missing} when the
     *     extracted content misses a required field, {@code negotiation.invalid_input} when the text is blank,
     *     {@code negotiation.conclusion_mismatch} when the extracted conclusion is not {@code Reject}, or
     *     {@code input.text_too_long} when the text exceeds the configured maximum length (A2AT_INPUT_TEXT_MAX_CHARS,
     *     default 16384)
     */
    public MetadataContent generateNegotiationRejectPromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.@NonNull NegotiationContext context,
            @NonNull String templateUri) {
        return negotiationContentService.generateRejectFromText(text, context, parseTemplateUri(templateUri));
    }

    /**
     * Generates an abort negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the common abort template and then renders
     * deterministically like the from-data variant.
     *
     * @param text free-text input stating the termination reason
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri raw template URI string of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank, malformed or does not address the common abort
     *     template
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} or {@code llm.response_invalid}
     *     when the extraction step fails after exhausting its retries, {@code negotiation.field_missing} when the
     *     extracted content misses the termination reason, {@code negotiation.invalid_input} when the text is blank, or
     *     {@code input.text_too_long} when the text exceeds the configured maximum length (A2AT_INPUT_TEXT_MAX_CHARS,
     *     default 16384)
     */
    public MetadataContent generateNegotiationAbortPromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.@NonNull NegotiationContext context,
            @NonNull String templateUri) {
        return negotiationContentService.generateAbortFromText(text, context, parseTemplateUri(templateUri));
    }

    /**
     * Lists every template available for the configured language across all A2A-T extensions.
     *
     * <p>This query never throws: the extension directories are discovered from the bundled resource tree itself, so
     * templates of extensions added later are included automatically. Templates that exist nowhere for the language are
     * skipped and an empty list is returned when no template can be loaded at all.
     *
     * @return loadable templates of the configured language across all extensions, sorted by template URI; empty when
     *     none can be loaded
     */
    public List<PromptTemplate> getPrompts() {
        return templateQueryService.getPrompts();
    }

    /**
     * Loads one template by its URI, regardless of the extension.
     *
     * <p>This query never throws for a well-formed template URI: a template that exists nowhere for the configured
     * language returns an empty result and logs an actionable warning.
     *
     * @param templateUri raw template URI string such as {@code Negotiation-T/target-negotiation/propose/v1} or
     *     {@code Task-T/network-layer/ran-energy-saving/v1}
     * @return the addressed template, or an empty result when the template does not exist for the configured language
     * @throws NullPointerException if the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed
     */
    public Optional<PromptTemplate> getPrompt(@NonNull String templateUri) {
        return templateQueryService.getPrompt(parseTemplateUri(templateUri));
    }

    /**
     * Validates a propose-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline checks the template URI format before any LLM call, runs the deterministic rule gate, then
     * performs one semantic validation LLM call (retried on the retryable failure codes) and merges the extracted
     * parameters with the rule-level context parameters; context parameters win on conflict.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri raw template URI string declaring the expected negotiation type and phase; its performative segment
     *     must be {@code propose}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation.invalid_input} when the prompt is null or blank or is not a negotiation message,
     *     {@code negotiation.rule_violation} when the negotiation context violates a rule (nested slot error codes
     *     such as {@code negotiation.invalid_context_id} or {@code negotiation.round_exceeded}), one of the closed
     *     {@code negotiation.*} code set (for example
     *     {@code negotiation.conclusion_content_mismatch}) when a rule or semantic check rejects the message,
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the message,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, or {@code template.not_found} when the semantic validation prompt resources are missing, or
     *     {@code input.text_too_long} when the prompt exceeds the configured maximum length (A2AT_INPUT_TEXT_MAX_CHARS,
     *     default 16384)
     */
    public FilledParamData validateProposePromptAndDataFilling(
            @NonNull String prompt,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull String templateUri) {
        return negotiationContentService.validateProposePromptAndDataFilling(
                prompt, context, schema, parseTemplateUri(templateUri));
    }

    /**
     * Validates an accept-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateProposePromptAndDataFilling} with the expected phase fixed to
     * accept: the template URI must declare the {@code accept-reject} segment and the message must satisfy the
     * accept-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri raw template URI string declaring the expected negotiation type and phase; its performative segment
     *     must be {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation.invalid_input} when the prompt is null or blank or is not a negotiation message,
     *     {@code negotiation.rule_violation} when the negotiation context violates a rule (nested slot error codes
     *     such as {@code negotiation.invalid_context_id} or {@code negotiation.round_exceeded}), one of the closed
     *     {@code negotiation.*} code set (for example
     *     {@code negotiation.conclusion_content_mismatch}) when a rule or semantic check rejects the message,
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the message,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, or {@code template.not_found} when the semantic validation prompt resources are missing, or
     *     {@code input.text_too_long} when the prompt exceeds the configured maximum length (A2AT_INPUT_TEXT_MAX_CHARS,
     *     default 16384)
     */
    public FilledParamData validateAcceptPromptAndDataFilling(
            @NonNull String prompt,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull String templateUri) {
        return negotiationContentService.validateAcceptPromptAndDataFilling(
                prompt, context, schema, parseTemplateUri(templateUri));
    }

    /**
     * Validates a reject-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateProposePromptAndDataFilling} with the expected phase fixed to
     * reject: the template URI must declare the {@code accept-reject} segment and the message must satisfy the
     * reject-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri raw template URI string declaring the expected negotiation type and phase; its performative segment
     *     must be {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation.invalid_input} when the prompt is null or blank or is not a negotiation message,
     *     {@code negotiation.rule_violation} when the negotiation context violates a rule (nested slot error codes
     *     such as {@code negotiation.invalid_context_id} or {@code negotiation.round_exceeded}), one of the closed
     *     {@code negotiation.*} code set (for example
     *     {@code negotiation.conclusion_content_mismatch}) when a rule or semantic check rejects the message,
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the message,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, or {@code template.not_found} when the semantic validation prompt resources are missing, or
     *     {@code input.text_too_long} when the prompt exceeds the configured maximum length (A2AT_INPUT_TEXT_MAX_CHARS,
     *     default 16384)
     */
    public FilledParamData validateRejectPromptAndDataFilling(
            @NonNull String prompt,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull String templateUri) {
        return negotiationContentService.validateRejectPromptAndDataFilling(
                prompt, context, schema, parseTemplateUri(templateUri));
    }

    /**
     * Validates an abort negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateProposePromptAndDataFilling} with the expected phase fixed to
     * abort: the template URI must address the common abort template and the message must satisfy the abort-phase
     * semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri raw template URI string of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank, malformed or does not address the common abort
     *     template
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation.invalid_input} when the prompt is null or blank or is not a negotiation message,
     *     {@code negotiation.rule_violation} when the negotiation context violates a rule (nested slot error codes
     *     such as {@code negotiation.invalid_context_id} or {@code negotiation.round_exceeded}), one of the closed
     *     {@code negotiation.*} code set (for example
     *     {@code negotiation.conclusion_content_mismatch}) when a rule or semantic check rejects the message,
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the message,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, or {@code template.not_found} when the semantic validation prompt resources are missing, or
     *     {@code input.text_too_long} when the prompt exceeds the configured maximum length (A2AT_INPUT_TEXT_MAX_CHARS,
     *     default 16384)
     */
    public FilledParamData validateAbortPromptAndDataFilling(
            @NonNull String prompt,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull String templateUri) {
        return negotiationContentService.validateAbortPromptAndDataFilling(
                prompt, context, schema, parseTemplateUri(templateUri));
    }

    /**
     * Validates a task prompt and extracts its parameters.
     *
     * @param prompt rendered task prompt text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri raw template URI string declaring the expected task template; its prefix segment must be
     *     {@code Task-T}
     * @return filled parameter data carrying the merged parameters
     * @throws NullPointerException if the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed
     * @throws net.openan.a2at.sdk.core.validation.ContentValidationException with the code
     *     {@code negotiation.invalid_input} when the prompt is null or blank, the schema is null, or the template URI
     *     addresses another extension or carries an unsupported version, the code
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the content, the code
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, the code {@code template.not_found} when the template or the validation prompt resources are
     *     missing, or the code {@code input.text_too_long} when the prompt exceeds the configured maximum length
     *     (A2AT_INPUT_TEXT_MAX_CHARS, default 16384)
     */
    public FilledParamData validateTaskPromptAndDataFilling(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull String templateUri) {
        return taskContentValidator.validate(prompt, schema, parseTemplateUri(templateUri));
    }

    /**
     * Validates a notification prompt and extracts its parameters.
     *
     * @param prompt rendered notification prompt text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri raw template URI string declaring the expected notification template; its prefix segment must
     *     be {@code Notification-T}
     * @return filled parameter data carrying the merged parameters
     * @throws NullPointerException if the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed
     * @throws net.openan.a2at.sdk.core.validation.ContentValidationException with the code
     *     {@code negotiation.invalid_input} when the prompt is null or blank, the schema is null, or the template URI
     *     addresses another extension or carries an unsupported version, the code
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the content, the code
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, the code {@code template.not_found} when the template or the validation prompt resources are
     *     missing, or the code {@code input.text_too_long} when the prompt exceeds the configured maximum length
     *     (A2AT_INPUT_TEXT_MAX_CHARS, default 16384)
     */
    public FilledParamData validateNotificationPromptAndDataFilling(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull String templateUri) {
        return notificationContentValidator.validate(prompt, schema, parseTemplateUri(templateUri));
    }

    /**
     * Validates an authorization prompt and extracts its parameters.
     *
     * @param prompt rendered authorization prompt text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri raw template URI string declaring the expected authorization template; its prefix segment
     *     must be {@code Authorization-T}
     * @return filled parameter data carrying the merged parameters
     * @throws NullPointerException if the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed
     * @throws net.openan.a2at.sdk.core.validation.ContentValidationException with the code
     *     {@code negotiation.invalid_input} when the prompt is null or blank, the schema is null, or the template URI
     *     addresses another extension or carries an unsupported version, the code
     *     {@code negotiation.semantic_rejected} when the semantic validation rejects the content, the code
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the semantic step fails after exhausting
     *     its retries, the code {@code template.not_found} when the template or the validation prompt resources are
     *     missing, or the code {@code input.text_too_long} when the prompt exceeds the configured maximum length
     *     (A2AT_INPUT_TEXT_MAX_CHARS, default 16384)
     */
    public FilledParamData validateAuthPromptAndDataFilling(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull String templateUri) {
        return authContentValidator.validate(prompt, schema, parseTemplateUri(templateUri));
    }

    private static TemplateUri parseTemplateUri(String templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return TemplateUri.parse(templateUri)
                .orElseThrow(() -> new IllegalArgumentException("Unparseable template URI: " + templateUri));
    }
}
