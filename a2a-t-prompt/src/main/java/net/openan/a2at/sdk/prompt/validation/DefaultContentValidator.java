package net.openan.a2at.sdk.prompt.validation;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import net.openan.a2at.sdk.core.validation.ValidationPipeline;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Default content validator that orchestrates the full validation pipeline with a no-op rule checker and an LLM-backed
 * semantic validator.
 *
 * <p>The template referenced by the {@code templateUri} argument of every {@link #validate} call is loaded through a
 * sourceType-aware template loader before the validation pipeline is entered. A load failure is reported as a
 * {@code template.not_found} error, the same code that signals missing content_validation prompt resources. The loaded
 * template text flows into the pipeline for prompt injection.
 *
 * <p>The validation pipeline and the content_validation prompt resources are assembled eagerly in the constructor: the
 * constructor resolves and freezes the prompt resources once, and a missing resource fails fast at assembly time
 * instead of on the first {@link #validate} call. The content_validation prompt resources are internal LLM instructions
 * loaded from the classpath regardless of the configured prompt source type.
 *
 * @since 2026-08
 */
public final class DefaultContentValidator implements ContentValidator {

    private final String extensionName;

    private final String language;

    private final PromptTemplateTextLoader templateLoader;

    private final ValidationPipeline<TemplateUri> pipeline;

    /**
     * Creates a content validator for the given extension name and language.
     *
     * <p>The constructor assembles the validation pipeline and loads the content_validation prompt resources of the
     * configured language from the classpath.
     *
     * @param extensionName extension name used for template URI validation
     * @param language language code for prompt resource loading and template loading
     * @param maxAttempts maximum retry attempts for semantic validation
     * @param llmClient LLM client for semantic validation; may be {@code null}, in which case the first
     *     {@link #validate} call fails with {@code ContentValidationException} carrying {@code llm.not_configured};
     *     there is no late injection point
     * @param templateLoader sourceType-aware template text loader for loading the template referenced by the
     *     {@code templateUri} of every {@link #validate} call
     * @throws ContentValidationException if the content_validation prompt resources of the given language are missing
     *     on the classpath
     */
    public DefaultContentValidator(
            @NonNull String extensionName,
            @NonNull String language,
            int maxAttempts,
            @Nullable LLMClient llmClient,
            @NonNull PromptTemplateTextLoader templateLoader) {
        this.extensionName = extensionName;
        this.language = language;
        this.templateLoader = templateLoader;
        this.pipeline = assemblePipeline(language, maxAttempts, llmClient);
    }

    /**
     * Validates one content prompt and extracts its filled parameters.
     *
     * @param prompt content prompt text
     * @param schema caller-provided parameter JSON schema
     * @param templateUri URI of the template the content is validated against, such as
     *     {@code Task-T/network-layer/ran-energy-saving/v1}
     * @return filled parameter data carrying the merged parameters
     * @throws ContentValidationException with {@code negotiation.invalid_input} if the template URI is null, addresses
     *     another extension than the one this validator is configured for, or carries an unsupported version; or if the
     *     prompt is null or blank, or the schema is null
     * @throws ContentValidationException if the validation fails at any stage, including {@code template.not_found}
     *     when the template cannot be loaded
     */
    @Override
    public FilledParamData validate(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri) {
        if (templateUri == null) {
            throw invalidInput("Template URI must not be null.");
        }

        if (!extensionName.equals(templateUri.extensionName())) {
            throw invalidInput("Template URI extension '" + templateUri.extensionName()
                    + "' does not match expected extension '" + extensionName + "'.");
        }

        if (!TemplateUri.DEFAULT_TEMPLATE_VERSION.equals(templateUri.templateVersion())) {
            throw invalidInput("Unsupported template URI version: " + templateUri.templateVersion());
        }

        String templateContent;
        try {
            templateContent = templateLoader.loadTemplate(templateUri.uri(), language);
        } catch (ResourceNotFoundException exception) {
            Map<String, String> facts = Map.of("template_uri", templateUri.uri(), "language", language);
            throw new ContentValidationException(
                    ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(),
                    ErrorMessages.render(ErrorCatalog.TEMPLATE_NOT_FOUND, language, facts),
                    exception);
        }

        return pipeline.validate(prompt, schema, templateUri, templateContent);
    }

    private ContentValidationException invalidInput(String reason) {
        Map<String, String> facts = Map.of("reason", reason);
        return new ContentValidationException(
                ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(),
                ErrorMessages.render(ErrorCatalog.NEGOTIATION_INVALID_INPUT, language, facts));
    }

    private static ValidationPipeline<TemplateUri> assemblePipeline(
            String language, int maxAttempts, @Nullable LLMClient llmClient) {
        try {
            return new ValidationPipeline<>(
                    prompt -> Map.of(), new DefaultSemanticValidator(llmClient, language), maxAttempts, language);
        } catch (ResourceNotFoundException exception) {
            Map<String, String> facts = Map.of("template_uri", exception.resourcePath(), "language", language);
            throw new ContentValidationException(
                    ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(),
                    ErrorMessages.render(ErrorCatalog.TEMPLATE_NOT_FOUND, language, facts),
                    exception);
        }
    }
}
