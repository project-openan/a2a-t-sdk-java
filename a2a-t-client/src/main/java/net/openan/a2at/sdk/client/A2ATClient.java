package net.openan.a2at.sdk.client;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.assembly.DefaultA2ATClientBuilder;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateQueryService;
import org.jspecify.annotations.NonNull;

/**
 * High-level client facade for prompt generation and negotiation APIs. The caller provides the `.env` file path
 * explicitly, typically after copying the repository `env.example`.
 *
 * @since 2026-06
 */
public final class A2ATClient {

    private final ClientPromptGenerationOrchestrator promptGenerationOrchestrator;

    private final RoleBoundNegotiationOrchestrator negotiationOrchestrator;

    private final NegotiationContentService negotiationContentService;

    private final TemplateQueryService templateQueryService;

    /**
     * Creates a client facade from one user-supplied `.env` path.
     *
     * @param envPath user-supplied `.env` file path
     */
    public A2ATClient(Path envPath) {
        Path resolvedEnvPath = envPath.toAbsolutePath().normalize();
        A2ATConfig config = NegotiationContentService.resolvePromptResourceLocalRootDir(
                A2ATConfig.load(resolvedEnvPath), resolvedEnvPath);
        DefaultA2ATClientBuilder builder =
                DefaultA2ATClientBuilder.builder().envPath(resolvedEnvPath).config(config);
        this.promptGenerationOrchestrator = builder.buildPromptGenerationOrchestrator();
        this.negotiationOrchestrator = builder.buildNegotiationOrchestrator();
        this.negotiationContentService =
                new NegotiationContentService(builder.buildNegotiationGenerationOrchestrator());
        this.templateQueryService = builder.buildTemplateQueryService();
    }

    /**
     * Generates a processed task prompt from raw user input.
     *
     * @param userInput user-provided task description or structured input map
     * @return prompt generation result containing either rendered prompt text or failure details
     */
    public PromptGenerationResult generateTaskPrompt(Object userInput) {
        return promptGenerationOrchestrator.generateTaskPrompt(userInput);
    }

    /**
     * Generates a task prompt with metadata from natural-language input using the template identified by the template
     * URI, bypassing scenario recognition.
     *
     * @param text natural-language task input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the text or template URI is null
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template_not_found},
     *     {@code prompt_resource_load_error}, {@code slot_schema_not_found}, {@code llm_invocation_failed},
     *     {@code render_failed} or {@code slot_validation_error} when generating the prompt fails
     */
    public MetadataContent generateTaskPromptFromText(@NonNull String text, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(templateUri, "templateUri");
        return promptGenerationOrchestrator.generateTaskPromptFromText(text, templateUri);
    }

    /**
     * Generates a task prompt with metadata from structured input and a data schema using the template identified by
     * the template URI, bypassing scenario recognition.
     *
     * @param data structured task input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field; must not be null or empty
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the template URI, data or schema is null
     * @throws IllegalArgumentException if the schema is empty
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template_not_found},
     *     {@code prompt_resource_load_error}, {@code slot_schema_not_found}, {@code llm_invocation_failed},
     *     {@code render_failed} or {@code slot_validation_error} when generating the prompt fails
     */
    public MetadataContent generateTaskPromptFromDataWithSchema(
            @NonNull Map<String, Object> data, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(templateUri, "templateUri");
        return promptGenerationOrchestrator.generateTaskPromptFromDataWithSchema(data, schema, templateUri);
    }

    /**
     * Generates an authorization prompt with metadata from natural-language input using the template identified by the
     * template URI, bypassing scenario recognition.
     *
     * <p>Authorization-T slot schemas are bundled with a2a-t-resources, so the entry point works out of the box with
     * classpath resource source.
     *
     * @param text natural-language authorization input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the text or template URI is null
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template_not_found},
     *     {@code prompt_resource_load_error}, {@code slot_schema_not_found}, {@code llm_invocation_failed},
     *     {@code render_failed} or {@code slot_validation_error} when generating the prompt fails
     */
    public MetadataContent generateAuthPromptFromText(@NonNull String text, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(templateUri, "templateUri");
        return promptGenerationOrchestrator.generateAuthPromptFromText(text, templateUri);
    }

    /**
     * Generates an authorization prompt with metadata from structured input and a data schema using the template
     * identified by the template URI, bypassing scenario recognition.
     *
     * <p>Authorization-T slot schemas are bundled with a2a-t-resources, so the entry point works out of the box with
     * classpath resource source.
     *
     * @param data structured authorization input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field; must not be null or empty
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the template URI, data or schema is null
     * @throws IllegalArgumentException if the schema is empty
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template_not_found},
     *     {@code prompt_resource_load_error}, {@code slot_schema_not_found}, {@code llm_invocation_failed},
     *     {@code render_failed} or {@code slot_validation_error} when generating the prompt fails
     */
    public MetadataContent generateAuthPromptFromDataWithSchema(
            @NonNull Map<String, Object> data, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(templateUri, "templateUri");
        return promptGenerationOrchestrator.generateAuthPromptFromDataWithSchema(data, schema, templateUri);
    }

    /**
     * Generates a notification prompt with metadata from natural-language input using the template identified by the
     * template URI, bypassing scenario recognition.
     *
     * @param text natural-language notification input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the text or template URI is null
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template_not_found},
     *     {@code prompt_resource_load_error}, {@code slot_schema_not_found}, {@code llm_invocation_failed},
     *     {@code render_failed} or {@code slot_validation_error} when generating the prompt fails
     */
    public MetadataContent generateNotificationPromptFromText(@NonNull String text, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(templateUri, "templateUri");
        return promptGenerationOrchestrator.generateNotificationPromptFromText(text, templateUri);
    }

    /**
     * Generates a notification prompt with metadata from structured input and a data schema using the template
     * identified by the template URI, bypassing scenario recognition.
     *
     * @param data structured notification input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field; must not be null or empty
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the template URI, data or schema is null
     * @throws IllegalArgumentException if the schema is empty
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template_not_found},
     *     {@code prompt_resource_load_error}, {@code slot_schema_not_found}, {@code llm_invocation_failed},
     *     {@code render_failed} or {@code slot_validation_error} when generating the prompt fails
     */
    public MetadataContent generateNotificationPromptFromDataWithSchema(
            @NonNull Map<String, Object> data, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(templateUri, "templateUri");
        return promptGenerationOrchestrator.generateNotificationPromptFromDataWithSchema(data, schema, templateUri);
    }

    /**
     * Starts a new negotiation payload for the requested negotiation type.
     *
     * @param type negotiation type to initiate
     * @param contentText human-readable negotiation message
     * @param facts structured facts attached to the payload
     * @return transport payload representing the initial negotiation turn
     */
    public Map<String, Object> startNegotiation(NegotiationType type, String contentText, Map<String, Object> facts) {
        return negotiationOrchestrator.startNegotiation(type, contentText, facts);
    }

    /**
     * Processes a received negotiation message using its transport context payload.
     *
     * @param message received negotiation message
     * @param context transport context payload associated with the message
     * @return normalized payload describing the receive result
     */
    public Map<String, Object> receiveNegotiation(String message, Map<String, Object> context) {
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
            NegotiationContext context, NegotiationStatus status, String contentText) {
        return negotiationOrchestrator.continueNegotiation(context, status, contentText);
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
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data or its context is null
     * @throws IllegalArgumentException if the template URI phase or type contradicts the method or the content type
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template exists for the URI in any resource root, or the code
     *     {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateNegotiationProposePromptFromData(
            @NonNull NegotiationProposeData data, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.generateProposeFromData(data, templateUri);
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
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data or its context is null
     * @throws IllegalArgumentException if the template URI phase or type contradicts the method or the content, or the
     *     content conclusion is not {@code Accept}
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template exists for the URI in any resource root, or the code
     *     {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateNegotiationAcceptPromptFromData(
            @NonNull NegotiationEndingData data, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.generateAcceptFromData(data, templateUri);
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
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data or its context is null
     * @throws IllegalArgumentException if the template URI phase or type contradicts the method or the content, or the
     *     content conclusion is not {@code Reject}
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template exists for the URI in any resource root, or the code
     *     {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateNegotiationRejectPromptFromData(
            @NonNull NegotiationEndingData data, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.generateRejectFromData(data, templateUri);
    }

    /**
     * Generates an abort negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. Abort messages are type-independent: the addressed
     * template must be the common abort template and the content carries only the termination reason.
     *
     * @param data typed abort input carrying the negotiation context and the termination reason
     * @param templateUri template URI of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data or its context is null
     * @throws IllegalArgumentException if the template URI does not address the common abort template or the
     *     termination reason is blank
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template exists for the URI in any resource root, or the code
     *     {@code negotiation_slot_missing} when rendering the template fails
     */
    public MetadataContent generateNegotiationAbortPromptFromData(
            @NonNull NegotiationAbortData data, @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.generateAbortFromData(data, templateUri);
    }

    /**
     * Generates a propose-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extraction step
     * is retried up to the configured attempt limit on the retryable failure codes
     * {@code negotiation_content_extract_failed} and {@code negotiation_llm_infrastructure_error}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI such as {@code Negotiation-T/target-negotiation/propose/v1}; its phase segment
     *     must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context is null
     * @throws IllegalArgumentException if the template URI phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when the
     *     extraction step fails after exhausting its retries, {@code negotiation_slot_missing} when the extracted
     *     content misses a required field, or {@code negotiation_invalid_input} when the text is blank or the extracted
     *     content contradicts the phase
     */
    public MetadataContent generateNegotiationProposePromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.generateProposeFromText(text, context, templateUri);
    }

    /**
     * Generates an accept-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extracted
     * conclusion must be {@code Accept}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI such as {@code Negotiation-T/information-negotiation/accept-reject/v1}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context is null
     * @throws IllegalArgumentException if the template URI phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when the
     *     extraction step fails after exhausting its retries, {@code negotiation_slot_missing} when the extracted
     *     content misses a required field, or {@code negotiation_invalid_input} when the text is blank or the extracted
     *     conclusion is not {@code Accept}
     */
    public MetadataContent generateNegotiationAcceptPromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.generateAcceptFromText(text, context, templateUri);
    }

    /**
     * Generates a reject-phase negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the template URI and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extracted
     * conclusion must be {@code Reject}.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI such as {@code Negotiation-T/feasibility-negotiation/accept-reject/v1}; its phase
     *     segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context is null
     * @throws IllegalArgumentException if the template URI phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when the
     *     extraction step fails after exhausting its retries, {@code negotiation_slot_missing} when the extracted
     *     content misses a required field, or {@code negotiation_invalid_input} when the text is blank or the extracted
     *     conclusion is not {@code Reject}
     */
    public MetadataContent generateNegotiationRejectPromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.generateRejectFromText(text, context, templateUri);
    }

    /**
     * Generates an abort negotiation message from free text.
     *
     * <p>This variant runs one LLM content-extraction step constrained by the common abort template and then renders
     * deterministically like the from-data variant. The template is loaded before the LLM call and the extraction step
     * is retried up to the configured attempt limit on the retryable failure codes
     * {@code negotiation_content_extract_failed} and {@code negotiation_llm_infrastructure_error}.
     *
     * @param text free-text input stating the termination reason
     * @param context negotiation context injected into the rendered message without any LLM involvement
     * @param templateUri template URI of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context is null
     * @throws IllegalArgumentException if the template URI does not address the common abort template
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template_not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation_content_extract_failed} or {@code negotiation_llm_infrastructure_error} when the
     *     extraction step fails after exhausting its retries, {@code negotiation_slot_missing} when the extracted
     *     content misses the termination reason, or {@code negotiation_invalid_input} when the text is blank
     */
    public MetadataContent generateNegotiationAbortPromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.generateAbortFromText(text, context, templateUri);
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
     * <p>This query never throws: a missing template yields an empty result together with a warning log instead of a
     * failure.
     *
     * @param templateUri template URI such as {@code Negotiation-T/target-negotiation/propose/v1} or
     *     {@code Task-T/network-layer/energy-saving/v1}
     * @return the addressed template, or an empty optional when no template exists for it in the configured language
     */
    public Optional<PromptTemplate> getPrompt(@NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return templateQueryService.getPrompt(templateUri);
    }

    /**
     * Lists every negotiation template available for the configured language.
     *
     * <p>This query never throws: templates that exist nowhere for the language are skipped and an empty list is
     * returned when no template can be loaded at all.
     *
     * @return loadable negotiation templates of the configured language, in a fixed type and phase order; empty when
     *     none can be loaded
     */
    public List<PromptTemplate> getNegotiationPrompts() {
        return negotiationContentService.getNegotiationPrompts();
    }

    /**
     * Loads one negotiation template by its URI.
     *
     * <p>This query never throws: a missing template yields an empty result together with a warning log instead of a
     * failure.
     *
     * @param templateUri template URI such as {@code Negotiation-T/target-negotiation/propose/v1}
     * @return the addressed template, or an empty optional when no template exists for it in the configured language
     */
    public Optional<PromptTemplate> getNegotiationPrompt(@NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.getNegotiationPrompt(templateUri);
    }

    /**
     * Validates a propose-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline first runs the deterministic rule gate (recognition of the negotiation context section and its
     * strong constraints) before any LLM call, then performs one LLM semantic validation call that also extracts the
     * parameters, and finally merges the parameters with the context parameters taking precedence. The semantic step is
     * retried up to the configured attempt limit on the retryable LLM infrastructure failure code.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is
     *     reported as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected negotiation type and phase; its phase segment must be
     *     {@code propose}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the prompt or schema is null
     * @throws IllegalArgumentException if the prompt is blank, or the template URI phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation_invalid_input} when the prompt is not a negotiation message,
     *     {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateProposePromptAndDataFilling(
            @NonNull String prompt,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.validateProposePromptAndDataFilling(prompt, context, schema, templateUri);
    }

    /**
     * Validates an accept-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateProposePromptAndDataFilling(String, Map, TemplateUri)} with the expected
     * phase fixed to accept: the template URI must declare the {@code accept-reject} segment and the message must
     * satisfy the accept-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is
     *     reported as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected negotiation type and phase; its phase segment must be
     *     {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the prompt or schema is null
     * @throws IllegalArgumentException if the prompt is blank, or the template URI phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation_invalid_input} when the prompt is not a negotiation message,
     *     {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateAcceptPromptAndDataFilling(
            @NonNull String prompt,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.validateAcceptPromptAndDataFilling(prompt, context, schema, templateUri);
    }

    /**
     * Validates a reject-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateProposePromptAndDataFilling(String, Map, TemplateUri)} with the expected
     * phase fixed to reject: the template URI must declare the {@code accept-reject} segment and the message must
     * satisfy the reject-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is
     *     reported as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected negotiation type and phase; its phase segment must be
     *     {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the prompt or schema is null
     * @throws IllegalArgumentException if the prompt is blank, or the template URI phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation_invalid_input} when the prompt is not a negotiation message,
     *     {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateRejectPromptAndDataFilling(
            @NonNull String prompt,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.validateRejectPromptAndDataFilling(prompt, context, schema, templateUri);
    }

    /**
     * Validates an abort negotiation message and extracts its parameters.
     *
     * <p>The pipeline is the one of {@link #validateProposePromptAndDataFilling(String, Map, TemplateUri)} with the expected
     * phase fixed to abort: the template URI must address the common abort template and the message must satisfy the
     * abort-phase semantic constraints.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is
     *     reported as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the prompt or schema is null
     * @throws IllegalArgumentException if the prompt is blank, or the template URI does not address the common abort
     *     template
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException with the code
     *     {@code negotiation_invalid_input} when the prompt is not a negotiation message,
     *     {@code negotiation_rule_violation} when the negotiation context violates a rule,
     *     {@code negotiation_semantic_rejected} when the semantic validation rejects the message,
     *     {@code negotiation_llm_infrastructure_error} when the semantic step fails after exhausting its retries, or
     *     {@code template_not_found} when the semantic validation prompt resources are missing
     */
    public FilledParamData validateAbortPromptAndDataFilling(
            @NonNull String prompt,
            net.openan.a2at.sdk.core.model.NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return negotiationContentService.validateAbortPromptAndDataFilling(prompt, context, schema, templateUri);
    }
}
