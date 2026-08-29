package net.openan.a2at.sdk.negotiation.generation;

import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Shared service for the negotiation content layer consumed by both the client and the server facade.
 *
 * <p>Each facade method delegates to exactly one service method, so the negotiation content-layer surface is defined
 * once instead of being copy-pasted per facade. The service itself is a thin typing over the
 * {@link NegotiationGenerationOrchestrator} pipeline and adds no behavior.
 *
 * @since 2026-08
 */
public final class NegotiationContentService {

    private final NegotiationGenerationOrchestrator orchestrator;

    /**
     * Creates one service over the given negotiation generation orchestrator.
     *
     * @param orchestrator negotiation generation orchestrator carrying the actual pipelines
     */
    public NegotiationContentService(@NonNull NegotiationGenerationOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "Negotiation orchestrator must not be null.");
    }

    /**
     * Assembles the default negotiation generation orchestrator from the unified SDK config.
     *
     * <p>The wiring is shared by the client and the server builder: the message language comes from the prompt runtime
     * config (negotiation templates and vocabularies are classpath-fixed), the retry attempt limit comes from the LLM
     * config, and the LLM client is passed by the caller and may be null when the provider is {@code local_rule}.
     *
     * @param config unified SDK config
     * @param llmClient LLM client for the LLM-backed steps; null keeps those steps unavailable
     * @return assembled negotiation generation orchestrator
     */
    public static NegotiationGenerationOrchestrator buildOrchestrator(
            @NonNull A2ATConfig config, @Nullable LLMClient llmClient) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(config.prompt().language())
                .llmClient(llmClient)
                .maxAttempts(config.llm().maxAttempts())
                .maxTextChars(config.inputLimits().maxTextChars())
                .build();
    }

    /**
     * Generates a propose negotiation message from typed data, deterministically without any LLM call.
     *
     * @param data typed propose input carrying the negotiation context and the typed content
     * @param templateUri template URI whose performative segment must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI's performative or type contradicts the method or the content
     *     type
     * @throws NegotiationGenerationException with the code {@code negotiation.content_invalid} when a required content
     *     field is blank or empty, or {@code negotiation.invalid_input} when the content combines a non-blank confirm
     *     request with conditional sections (target: the three clarification lists; feasibility: either conditional
     *     list or the alternative action)
     * @throws NegotiationGenerationException with the code {@code template.not_found} when loading the template fails,
     *     or the code {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateProposeFromData(
            @NonNull NegotiationProposeData data, @NonNull TemplateUri templateUri) {
        return orchestrator.generateProposeFromData(data, templateUri);
    }

    /**
     * Generates an accept negotiation message from typed data, deterministically without any LLM call.
     *
     * @param data typed terminal input whose content conclusion must be {@code Accept}
     * @param templateUri template URI whose performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI's performative or type contradicts the method or the content
     * @throws NegotiationGenerationException with the code {@code negotiation.conclusion_mismatch} when the content
     *     conclusion is not {@code Accept}, or {@code negotiation.content_invalid} when a required content field is
     *     blank or empty
     * @throws NegotiationGenerationException with the code {@code template.not_found} when loading the template fails,
     *     or the code {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateAcceptFromData(
            @NonNull NegotiationEndingData data, @NonNull TemplateUri templateUri) {
        return orchestrator.generateAcceptFromData(data, templateUri);
    }

    /**
     * Generates a reject negotiation message from typed data, deterministically without any LLM call.
     *
     * @param data typed terminal input whose content conclusion must be {@code Reject}
     * @param templateUri template URI whose performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI's performative or type contradicts the method or the content
     * @throws NegotiationGenerationException with the code {@code negotiation.conclusion_mismatch} when the content
     *     conclusion is not {@code Reject}, or {@code negotiation.content_invalid} when a required content field is
     *     blank or empty
     * @throws NegotiationGenerationException with the code {@code template.not_found} when loading the template fails,
     *     or the code {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateRejectFromData(
            @NonNull NegotiationEndingData data, @NonNull TemplateUri templateUri) {
        return orchestrator.generateRejectFromData(data, templateUri);
    }

    /**
     * Generates an abort negotiation message from typed data, deterministically without any LLM call.
     *
     * @param data typed abort input carrying the termination reason
     * @param templateUri template URI of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI does not address the common abort template
     * @throws NegotiationGenerationException with the code {@code negotiation.content_invalid} when the termination
     *     reason is blank
     * @throws NegotiationGenerationException with the code {@code template.not_found} when loading the template fails,
     *     or the code {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateAbortFromData(@NonNull NegotiationAbortData data, @NonNull TemplateUri templateUri) {
        return orchestrator.generateAbortFromData(data, templateUri);
    }

    /**
     * Generates a propose negotiation message from free text through one LLM content-extraction step.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri template URI whose performative segment must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI contradicts the method
     * @throws NegotiationGenerationException with the code {@code template.not_found},
     *     {@code negotiation.content_extract_failed} or {@code llm.invocation_failed} or {@code llm.response_invalid}
     *     when loading or extracting fails, {@code template.render_failed} when rendering the template fails,
     *     {@code negotiation.field_missing} when the extracted content misses a
     *     required field, or {@code negotiation.invalid_input} when the text is blank, the extracted content
     *     contradicts the performative or the extracted confirm request is combined with conditional sections or the
     *     wrong feasibility action, or {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateProposeFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return orchestrator.generateProposeFromText(text, context, templateUri);
    }

    /**
     * Generates an accept negotiation message from free text through one LLM content-extraction step.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri template URI whose performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI contradicts the method
     * @throws NegotiationGenerationException with the code {@code template.not_found},
     *     {@code negotiation.content_extract_failed} or {@code llm.invocation_failed} or {@code llm.response_invalid}
     *     when loading or extracting fails, {@code template.render_failed} when rendering the template fails,
     *     {@code negotiation.field_missing} when the extracted content misses a
     *     required field, or {@code negotiation.invalid_input} when the text is blank,
     *     {@code negotiation.conclusion_mismatch} when the extracted conclusion is not {@code Accept}, or
     *     {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateAcceptFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return orchestrator.generateAcceptFromText(text, context, templateUri);
    }

    /**
     * Generates a reject negotiation message from free text through one LLM content-extraction step.
     *
     * @param text free-text input describing the message content
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri template URI whose performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI contradicts the method
     * @throws NegotiationGenerationException with the code {@code template.not_found},
     *     {@code negotiation.content_extract_failed} or {@code llm.invocation_failed} or {@code llm.response_invalid}
     *     when loading or extracting fails, {@code template.render_failed} when rendering the template fails,
     *     {@code negotiation.field_missing} when the extracted content misses a
     *     required field, or {@code negotiation.invalid_input} when the text is blank,
     *     {@code negotiation.conclusion_mismatch} when the extracted conclusion is not {@code Reject}, or
     *     {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateRejectFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return orchestrator.generateRejectFromText(text, context, templateUri);
    }

    /**
     * Generates an abort negotiation message from free text through one LLM content-extraction step.
     *
     * @param text free-text input stating the termination reason
     * @param context negotiation context carried in the {@code negotiationContext} metadata entry of the generated
     *     message without any LLM involvement
     * @param templateUri template URI of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI does not address the common abort template
     * @throws NegotiationGenerationException with the code {@code template.not_found},
     *     {@code negotiation.content_extract_failed} or {@code llm.invocation_failed} or {@code llm.response_invalid}
     *     when loading or extracting fails, {@code template.render_failed} when rendering the template fails,
     *     {@code negotiation.field_missing} when the extracted content misses the
     *     termination reason, or {@code negotiation.invalid_input} when the text is blank, or
     *     {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateAbortFromText(
            String text, @NonNull NegotiationContext context, @NonNull TemplateUri templateUri) {
        return orchestrator.generateAbortFromText(text, context, templateUri);
    }

    /**
     * Validates a propose negotiation message and extracts its parameters.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI whose performative segment must be {@code propose}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation.invalid_input} when the prompt is
     *     null or blank, or {@code negotiation.rule_violation}, {@code negotiation.semantic_rejected},
     *     {@code llm.invocation_failed} or {@code llm.response_invalid}, {@code input.text_too_long} or
     *     {@code template.not_found} when the validation pipeline fails
     */
    public FilledParamData validateProposePromptAndDataFilling(
            String prompt,
            @Nullable NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        return orchestrator.validateProposePromptAndDataFilling(prompt, context, schema, templateUri);
    }

    /**
     * Validates an accept negotiation message and extracts its parameters.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI whose performative segment must be {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation.invalid_input} when the prompt is
     *     null or blank, or {@code negotiation.rule_violation}, {@code negotiation.semantic_rejected},
     *     {@code llm.invocation_failed} or {@code llm.response_invalid}, {@code input.text_too_long} or
     *     {@code template.not_found} when the validation pipeline fails
     */
    public FilledParamData validateAcceptPromptAndDataFilling(
            String prompt,
            @Nullable NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        return orchestrator.validateAcceptPromptAndDataFilling(prompt, context, schema, templateUri);
    }

    /**
     * Validates a reject negotiation message and extracts its parameters.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI whose performative segment must be {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI contradicts the method
     * @throws NegotiationParamExtractionException with the code {@code negotiation.invalid_input} when the prompt is
     *     null or blank, or {@code negotiation.rule_violation}, {@code negotiation.semantic_rejected},
     *     {@code llm.invocation_failed} or {@code llm.response_invalid}, {@code input.text_too_long} or
     *     {@code template.not_found} when the validation pipeline fails
     */
    public FilledParamData validateRejectPromptAndDataFilling(
            String prompt,
            @Nullable NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        return orchestrator.validateRejectPromptAndDataFilling(prompt, context, schema, templateUri);
    }

    /**
     * Validates an abort negotiation message and extracts its parameters.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI does not address the common abort template
     * @throws NegotiationParamExtractionException with the code {@code input.text_too_long} when the prompt exceeds the
     *     configured maximum length, or another validation pipeline failure code otherwise
     */
    public FilledParamData validateAbortPromptAndDataFilling(
            String prompt,
            @Nullable NegotiationContext context,
            @NonNull Map<String, Object> schema,
            @NonNull TemplateUri templateUri) {
        return orchestrator.validateAbortPromptAndDataFilling(prompt, context, schema, templateUri);
    }
}
