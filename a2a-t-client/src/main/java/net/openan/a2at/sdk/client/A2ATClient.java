package net.openan.a2at.sdk.client;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.assembly.DefaultA2ATClientBuilder;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.telemetry.TelemetryHook;
import net.openan.a2at.sdk.core.telemetry.TelemetryOperation;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level client facade for prompt generation and negotiation APIs. The caller provides the {@code .env} file path
 * explicitly, typically after copying the repository {@code env.example}.
 *
 * @since 2026-06
 */
public final class A2ATClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(A2ATClient.class);

    private final ClientPromptGenerationOrchestrator promptGenerationOrchestrator;

    private final RoleBoundNegotiationOrchestrator negotiationOrchestrator;

    private final NegotiationContentService negotiationContentService;

    private final TemplateQueryService templateQueryService;

    private final TelemetryHook hook;

    /**
     * Creates a client facade from one user-supplied {@code .env} path without telemetry observation.
     *
     * @param envPath user-supplied {@code .env} file path
     */
    public A2ATClient(Path envPath) {
        this(envPath, null);
    }

    /**
     * Creates a client facade from one user-supplied {@code .env} path with an optional telemetry hook observing every
     * facade operation.
     *
     * @param envPath user-supplied {@code .env} file path
     * @param hook telemetry hook receiving the SDK operation events; {@code null} disables observation
     */
    public A2ATClient(Path envPath, @Nullable TelemetryHook hook) {
        Path resolvedEnvPath = envPath.toAbsolutePath().normalize();
        A2ATConfig config =
                A2ATConfig.resolvePromptResourceLocalRootDir(A2ATConfig.load(resolvedEnvPath), resolvedEnvPath);
        DefaultA2ATClientBuilder builder =
                DefaultA2ATClientBuilder.builder().envPath(resolvedEnvPath).config(config);
        this.hook = hook == null ? TelemetryHook.NO_OP : hook;
        this.promptGenerationOrchestrator = builder.buildPromptGenerationOrchestrator();
        this.negotiationOrchestrator = builder.buildNegotiationOrchestrator();
        this.negotiationContentService =
                new NegotiationContentService(builder.buildNegotiationGenerationOrchestrator());
        this.templateQueryService = builder.buildTemplateQueryService();
    }

    private <T> T observed(String method, Supplier<T> action) {
        try (TelemetryOperation operation = hook.startSdkOperation("client", method)) {
            try {
                T result = action.get();
                operation.markSuccess();
                return result;
            } catch (RuntimeException error) {
                operation.markError(error);
                throw error;
            }
        }
    }

    static @Nullable String terminalOutcome(NegotiationStatus status) {
        if (status == NegotiationStatus.AGREED) {
            return "accept";
        }
        return status == NegotiationStatus.REJECTED ? "reject" : null;
    }

    private void reportNegotiationRounds(NegotiationContext context, NegotiationStatus status) {
        try {
            String outcome = terminalOutcome(status);
            if (outcome != null) {
                hook.reportNegotiationRounds(context.negotiationId(), outcome, context.round());
            }
        } catch (RuntimeException error) {
            LOGGER.warn("a2at: negotiation metric report failed", error);
        }
    }

    /**
     * Generates a processed task prompt from raw user input. Both text and structured input ({@code Map}) are unified
     * through LLM extraction; there is no zero-LLM rule shortcut.
     *
     * @param userInput user-provided task description (text or structured input map); a String input longer than the
     *     configured maximum length ({@code A2AT_INPUT_TEXT_MAX_CHARS}) fails fast without any LLM call
     * @return prompt generation result containing either rendered prompt text or failure details; a String input over
     *     the limit yields the failure code {@code input.text_too_long}
     */
    public PromptGenerationResult generateTaskPrompt(Object userInput) {
        return observed("generateTaskPrompt", () -> promptGenerationOrchestrator.generateTaskPrompt(userInput));
    }

    /**
     * Generates a task prompt with metadata from natural-language input using the template identified by the template
     * URI, bypassing scenario recognition.
     *
     * @param text natural-language task input
     * @param templateUri template URI string identifying the target template, such as
     *     {@code Task-T/network-layer/ran-energy-saving/v1}
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the text or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template.not_found},
     *     {@code template.load_failed}, {@code slot.schema_not_found}, {@code llm.invocation_failed},
     *     {@code template.render_failed} or {@code slot.not_provided} when generating the prompt fails, or the code
     *     {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateTaskPromptFromText(@NonNull String text, @NonNull String templateUri) {
        return observed("generateTaskPromptFromText", () -> {
            Objects.requireNonNull(text, "text");
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return promptGenerationOrchestrator.generateTaskPromptFromText(text, parsedTemplateUri);
        });
    }

    /**
     * Generates a task prompt with metadata from structured input and a data schema using the template identified by
     * the template URI, bypassing scenario recognition.
     *
     * @param data structured task input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field; must not be null or empty
     * @param templateUri template URI string identifying the target template, such as
     *     {@code Task-T/network-layer/ran-energy-saving/v1}
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the template URI, data or schema is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or the schema is empty
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template.not_found},
     *     {@code template.load_failed}, {@code slot.schema_not_found}, {@code llm.invocation_failed},
     *     {@code template.render_failed} or {@code slot.not_provided} when generating the prompt fails
     */
    public MetadataContent generateTaskPromptFromDataWithSchema(
            @NonNull Map<String, Object> data, @NonNull Map<String, Object> schema, @NonNull String templateUri) {
        return observed("generateTaskPromptFromDataWithSchema", () -> {
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(schema, "schema");
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return promptGenerationOrchestrator.generateTaskPromptFromDataWithSchema(data, schema, parsedTemplateUri);
        });
    }

    /**
     * Generates an authorization prompt with metadata from natural-language input using the template identified by the
     * template URI, bypassing scenario recognition.
     *
     * <p>Authorization-T slot schemas are bundled with a2a-t-resources, so the entry point works out of the box with
     * classpath resource source.
     *
     * @param text natural-language authorization input
     * @param templateUri template URI string identifying the target template, such as
     *     {@code Authorization-T/authorization-policy-management/v1}
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the text or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template.not_found},
     *     {@code template.load_failed}, {@code slot.schema_not_found}, {@code llm.invocation_failed},
     *     {@code template.render_failed} or {@code slot.not_provided} when generating the prompt fails, or the code
     *     {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateAuthPromptFromText(@NonNull String text, @NonNull String templateUri) {
        return observed("generateAuthPromptFromText", () -> {
            Objects.requireNonNull(text, "text");
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return promptGenerationOrchestrator.generateAuthPromptFromText(text, parsedTemplateUri);
        });
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
     * @param templateUri template URI string identifying the target template, such as
     *     {@code Authorization-T/authorization-policy-management/v1}
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the template URI, data or schema is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or the schema is empty
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template.not_found},
     *     {@code template.load_failed}, {@code slot.schema_not_found}, {@code llm.invocation_failed},
     *     {@code template.render_failed} or {@code slot.not_provided} when generating the prompt fails
     */
    public MetadataContent generateAuthPromptFromDataWithSchema(
            @NonNull Map<String, Object> data, @NonNull Map<String, Object> schema, @NonNull String templateUri) {
        return observed("generateAuthPromptFromDataWithSchema", () -> {
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(schema, "schema");
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return promptGenerationOrchestrator.generateAuthPromptFromDataWithSchema(data, schema, parsedTemplateUri);
        });
    }

    /**
     * Generates a notification prompt with metadata from natural-language input using the template identified by the
     * template URI, bypassing scenario recognition.
     *
     * @param text natural-language notification input
     * @param templateUri template URI string identifying the target template, such as
     *     {@code Notification-T/network-layer/subscribe-incident/v1}
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the text or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template.not_found},
     *     {@code template.load_failed}, {@code slot.schema_not_found}, {@code llm.invocation_failed},
     *     {@code template.render_failed} or {@code slot.not_provided} when generating the prompt fails, or the code
     *     {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateNotificationPromptFromText(@NonNull String text, @NonNull String templateUri) {
        return observed("generateNotificationPromptFromText", () -> {
            Objects.requireNonNull(text, "text");
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return promptGenerationOrchestrator.generateNotificationPromptFromText(text, parsedTemplateUri);
        });
    }

    /**
     * Generates a notification prompt with metadata from structured input and a data schema using the template
     * identified by the template URI, bypassing scenario recognition.
     *
     * @param data structured notification input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field; must not be null or empty
     * @param templateUri template URI string identifying the target template, such as
     *     {@code Notification-T/network-layer/subscribe-incident/v1}
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     * @throws NullPointerException if the template URI, data or schema is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or the schema is empty
     * @throws net.openan.a2at.sdk.core.exception.PromptGenerationException with the code {@code template.not_found},
     *     {@code template.load_failed}, {@code slot.schema_not_found}, {@code llm.invocation_failed},
     *     {@code template.render_failed} or {@code slot.not_provided} when generating the prompt fails
     */
    public MetadataContent generateNotificationPromptFromDataWithSchema(
            @NonNull Map<String, Object> data, @NonNull Map<String, Object> schema, @NonNull String templateUri) {
        return observed("generateNotificationPromptFromDataWithSchema", () -> {
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(schema, "schema");
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return promptGenerationOrchestrator.generateNotificationPromptFromDataWithSchema(
                    data, schema, parsedTemplateUri);
        });
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
        return observed("startNegotiation", () -> negotiationOrchestrator.startNegotiation(type, contentText, facts));
    }

    /**
     * Processes a received negotiation message using its transport context payload.
     *
     * @param message received negotiation message
     * @param context transport context payload associated with the message
     * @return normalized payload describing the receive result
     */
    public Map<String, Object> receiveNegotiation(String message, Map<String, Object> context) {
        return observed("receiveNegotiation", () -> negotiationOrchestrator.receiveNegotiation(message, context));
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
        return observed("continueNegotiation", () -> {
            Map<String, Object> result = negotiationOrchestrator.continueNegotiation(context, status, contentText);
            reportNegotiationRounds(context, status);
            return result;
        });
    }

    /**
     * Generates a propose-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM: the typed content is validated, dispatched to the
     * generator of the negotiation type addressed by the template URI and rendered from that template.
     *
     * @param data typed propose input carrying the negotiation context and the typed content
     * @param templateUri template URI string such as {@code Negotiation-T/information-negotiation/propose/v1}; its
     *     performative segment must be {@code propose}
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
        return observed("generateNegotiationProposePromptFromData", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.generateProposeFromData(data, parsedTemplateUri);
        });
    }

    /**
     * Generates an accept-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. The content conclusion must be {@code Accept}; a
     * mismatched conclusion is a content error.
     *
     * @param data typed terminal input carrying the negotiation context and the typed ending content
     * @param templateUri template URI string such as {@code Negotiation-T/information-negotiation/accept-reject/v1};
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
        return observed("generateNegotiationAcceptPromptFromData", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.generateAcceptFromData(data, parsedTemplateUri);
        });
    }

    /**
     * Generates a reject-phase negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. The content conclusion must be {@code Reject}; a
     * mismatched conclusion is a content error.
     *
     * @param data typed terminal input carrying the negotiation context and the typed ending content
     * @param templateUri template URI string such as {@code Negotiation-T/feasibility-negotiation/accept-reject/v1};
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
        return observed("generateNegotiationRejectPromptFromData", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.generateRejectFromData(data, parsedTemplateUri);
        });
    }

    /**
     * Generates an abort negotiation message from typed data.
     *
     * <p>This variant is deterministic and never calls an LLM. Abort messages are type-independent: the addressed
     * template must be the common abort template and the content carries only the termination reason.
     *
     * @param data typed abort input carrying the negotiation context and the termination reason
     * @param templateUri template URI string of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the data, its context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or does not address the common abort
     *     template
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template exists for the URI in any resource root, the code
     *     {@code negotiation.content_invalid} when the termination reason is blank, or the code
     *     {@code template.render_failed} when rendering the template fails
     */
    public MetadataContent generateNegotiationAbortPromptFromData(
            @NonNull NegotiationAbortData data, @NonNull String templateUri) {
        return observed("generateNegotiationAbortPromptFromData", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.generateAbortFromData(data, parsedTemplateUri);
        });
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
     * @param templateUri template URI string such as {@code Negotiation-T/target-negotiation/propose/v1}; its performative
     *     segment must be {@code propose}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} or {@code llm.response_invalid} when the extraction step fails
     *     after exhausting its retries, {@code negotiation.field_missing} when the extracted content misses a required
     *     field, or {@code negotiation.invalid_input} when the text is blank or the extracted content contradicts the
     *     phase, or the code {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateNegotiationProposePromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.@NonNull NegotiationContext context,
            @NonNull String templateUri) {
        return observed("generateNegotiationProposePromptFromText", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.generateProposeFromText(text, context, parsedTemplateUri);
        });
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
     * @param templateUri template URI string such as {@code Negotiation-T/information-negotiation/accept-reject/v1};
     *     its performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} or {@code llm.response_invalid} when the extraction step fails
     *     after exhausting its retries, {@code negotiation.field_missing} when the extracted content misses a required
     *     field, or {@code negotiation.invalid_input} when the text is blank, {@code negotiation.conclusion_mismatch}
     *     when the extracted conclusion is not {@code Accept}, or the code {@code input.text_too_long} when the text
     *     exceeds the configured maximum length
     */
    public MetadataContent generateNegotiationAcceptPromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.@NonNull NegotiationContext context,
            @NonNull String templateUri) {
        return observed("generateNegotiationAcceptPromptFromText", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.generateAcceptFromText(text, context, parsedTemplateUri);
        });
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
     * @param templateUri template URI string such as {@code Negotiation-T/feasibility-negotiation/accept-reject/v1};
     *     its performative segment must be {@code accept-reject}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or its phase contradicts the method
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} or {@code llm.response_invalid} when the extraction step fails
     *     after exhausting its retries, {@code negotiation.field_missing} when the extracted content misses a required
     *     field, or {@code negotiation.invalid_input} when the text is blank, {@code negotiation.conclusion_mismatch}
     *     when the extracted conclusion is not {@code Reject}, or the code {@code input.text_too_long} when the text
     *     exceeds the configured maximum length
     */
    public MetadataContent generateNegotiationRejectPromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.@NonNull NegotiationContext context,
            @NonNull String templateUri) {
        return observed("generateNegotiationRejectPromptFromText", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.generateRejectFromText(text, context, parsedTemplateUri);
        });
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
     * @param templateUri template URI string of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return generated message carrying the template URI, the rendered message text and the negotiation extension URI
     * @throws NullPointerException if the context or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or does not address the common abort
     *     template
     * @throws net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException with the code
     *     {@code template.not_found} when no template or prompt resource exists for the URI and language,
     *     {@code negotiation.content_extract_failed}, {@code llm.invocation_failed} or {@code llm.response_invalid} when the extraction step fails
     *     after exhausting its retries, {@code negotiation.field_missing} when the extracted content misses the
     *     termination reason, or {@code negotiation.invalid_input} when the text is blank, or the code
     *     {@code input.text_too_long} when the text exceeds the configured maximum length
     */
    public MetadataContent generateNegotiationAbortPromptFromText(
            @NonNull String text,
            net.openan.a2at.sdk.core.model.@NonNull NegotiationContext context,
            @NonNull String templateUri) {
        return observed("generateNegotiationAbortPromptFromText", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.generateAbortFromText(text, context, parsedTemplateUri);
        });
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
        return observed("getPrompts", () -> templateQueryService.getPrompts());
    }

    /**
     * Loads one template by its URI, regardless of the extension.
     *
     * <p>This query never throws for a well-formed template URI: a missing template yields an empty result together
     * with a warning log instead of a failure.
     *
     * @param templateUri template URI string such as {@code Negotiation-T/target-negotiation/propose/v1} or
     *     {@code Task-T/network-layer/ran-energy-saving/v1}
     * @return the addressed template, or an empty optional when no template exists for it in the configured language
     * @throws NullPointerException if the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed
     */
    public Optional<PromptTemplate> getPrompt(@NonNull String templateUri) {
        return observed("getPrompt", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return templateQueryService.getPrompt(parsedTemplateUri);
        });
    }

    /**
     * Validates a propose-phase negotiation message and extracts its parameters.
     *
     * <p>The pipeline checks the template URI before any LLM call, runs the deterministic rule gate on the negotiation
     * context carried in the {@code negotiationContext} metadata entry, then performs one LLM semantic validation call
     * that also extracts the parameters, and finally merges the parameters with the context parameters taking
     * precedence. The semantic step is retried up to the configured attempt limit on the retryable LLM infrastructure
     * failure code.
     *
     * @param prompt rendered negotiation message text to validate
     * @param context negotiation context carried alongside the message in the A2A-T metadata; {@code null} is reported
     *     as not being a negotiation message
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI string declaring the expected negotiation type and phase; its performative segment must
     *     be {@code propose}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or the template URI phase contradicts
     *     the method
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
        return observed("validateProposePromptAndDataFilling", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.validateProposePromptAndDataFilling(
                    prompt, context, schema, parsedTemplateUri);
        });
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
     * @param templateUri template URI string declaring the expected negotiation type and phase; its performative segment must
     *     be {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or the template URI phase contradicts
     *     the method
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
        return observed("validateAcceptPromptAndDataFilling", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.validateAcceptPromptAndDataFilling(
                    prompt, context, schema, parsedTemplateUri);
        });
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
     * @param templateUri template URI string declaring the expected negotiation type and phase; its performative segment must
     *     be {@code accept-reject}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or the template URI phase contradicts
     *     the method
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
        return observed("validateRejectPromptAndDataFilling", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.validateRejectPromptAndDataFilling(
                    prompt, context, schema, parsedTemplateUri);
        });
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
     * @param templateUri template URI string of the common abort template {@code Negotiation-T/common/abort/v1}
     * @return filled parameter data carrying the context parameters and the extracted parameters
     * @throws NullPointerException if the schema or the template URI is null
     * @throws IllegalArgumentException if the template URI is blank or malformed, or does not address the common abort
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
        return observed("validateAbortPromptAndDataFilling", () -> {
            TemplateUri parsedTemplateUri = parseTemplateUri(templateUri);
            return negotiationContentService.validateAbortPromptAndDataFilling(
                    prompt, context, schema, parsedTemplateUri);
        });
    }

    private static TemplateUri parseTemplateUri(String templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        return TemplateUri.parse(templateUri)
                .orElseThrow(() -> new IllegalArgumentException("Unparseable template URI: " + templateUri));
    }
}
