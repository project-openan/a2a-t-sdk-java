package net.openan.a2at.sdk.prompt.resources.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic template query service consumed by the client and the server facade.
 *
 * <p>The service exposes the extension-agnostic template queries of the design document over a
 * {@link PromptTemplateCatalog}: {@link #getPrompts()} lists every loadable template of the configured language across
 * all A2A-T extensions and {@link #getPrompt(TemplateUri)} loads one template by its URI. Both queries never throw; a
 * missing template is answered with an empty result and an actionable warning log.
 *
 * @since 2026-08
 */
public final class TemplateQueryService {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(TemplateQueryService.class);

    private static final String LANGUAGE_HINT =
            "set A2AT_LANGUAGE to a language with bundled templates (zh-CN or en-US)";

    private final PromptTemplateCatalog templateCatalog;

    private final String language;

    private final Logger logger;

    /**
     * Creates one service over a catalog for the given language.
     *
     * @param templateCatalog directory-driven catalog over the template tree of every extension
     * @param language locale identifier the catalog was created for; only used in log messages
     */
    public TemplateQueryService(@NonNull PromptTemplateCatalog templateCatalog, @NonNull String language) {
        this.templateCatalog = Objects.requireNonNull(templateCatalog, "Prompt template catalog must not be null.");
        this.language = Objects.requireNonNull(language, "Language must not be null.");
        this.logger = DEFAULT_LOGGER;
    }

    /**
     * Creates one service for one language, one source type and an optional local template root.
     *
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @param sourceType resource source selector, {@code classpath} or {@code local_file}
     * @param localRootDir local prompt resource root containing the {@code templates/} tree; only consulted in
     *     {@code local_file} mode and may be null otherwise
     */
    public TemplateQueryService(@NonNull String language, @NonNull String sourceType, @Nullable String localRootDir) {
        this(new PromptTemplateCatalog(language, sourceType, localRootDir), language);
    }

    /**
     * Lists every template available for the configured language across all A2A-T extensions.
     *
     * <p>This query never throws: the extension directories are discovered from the resource tree itself, templates
     * that exist nowhere for the language are skipped, and an empty list is returned when no template can be loaded at
     * all. The result is sorted by template URI, which orders by extension first.
     *
     * @return loadable templates of the configured language across all extensions, sorted by URI; empty when none can
     *     be loaded
     */
    public @NonNull List<PromptTemplate> getPrompts() {
        return templateCatalog.loadAll();
    }

    /**
     * Loads one template by its URI, regardless of the extension.
     *
     * <p>This query never throws: a template that exists nowhere for the configured language returns an empty result
     * and logs an actionable warning.
     *
     * @param templateUri template URI such as {@code Negotiation-T/information-negotiation/propose/v1} or
     *     {@code Task-T/network-layer/ran-energy-saving/v1}
     * @return the addressed template, or an empty result when the template does not exist for the configured language
     * @throws NullPointerException if the template URI is null
     */
    public Optional<PromptTemplate> getPrompt(@NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        Optional<PromptTemplate> template = templateCatalog.load(templateUri);
        if (template.isEmpty()) {
            logger.atWarn()
                    .log(
                            "prompt_template_not_found uri={} language={} hint={}",
                            templateUri.uri(),
                            language,
                            LANGUAGE_HINT);
        }
        return template;
    }
}
