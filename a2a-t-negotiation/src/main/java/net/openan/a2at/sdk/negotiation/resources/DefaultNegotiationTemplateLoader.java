package net.openan.a2at.sdk.negotiation.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateDescriptions;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default negotiation template loader, classpath-fixed.
 *
 * <p>Templates are resolved from the built-in classpath resources bundled with the SDK — negotiation templates are
 * classpath-fixed and never configurable. Local template copies are not consulted.
 *
 * <p>Classpath resources are frozen at assembly time: each template is resolved once and cached at the JVM level, so
 * every subsequent load returns the cached snapshot. Missing templates throw on every call and are never cached. The
 * loader is thread-safe.
 *
 * @since 2026-08
 */
public final class DefaultNegotiationTemplateLoader implements NegotiationTemplateLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNegotiationTemplateLoader.class);

    private static final String CLASSPATH_ROOT = "prompt_resources/";

    private static final String TEMPLATE_FILE_NAME = "template.md";

    private static final ConcurrentHashMap<CacheKey, String> CACHE = new ConcurrentHashMap<>();

    /**
     * Creates a classpath-fixed loader for one language.
     *
     * <p>The language is validated here for assembly-time fail-fast only; {@link #load} resolves the language from the
     * reference, not from this constructor argument.
     *
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @throws IllegalArgumentException if the language is not a simple path segment
     */
    public DefaultNegotiationTemplateLoader(String language) {
        if (!PathSegments.isSimpleSegment(language)) {
            throw new IllegalArgumentException(
                    "Negotiation template loader language must be a non-blank simple path segment but was " + language
                            + ".");
        }
    }

    /**
     * Loads one negotiation template from the built-in classpath resources.
     *
     * @param reference template addressing key, including the language to load
     * @return loaded template with its URI, description and full content
     * @throws ResourceNotFoundException if the template does not exist on the classpath
     */
    @Override
    public PromptTemplate load(NegotiationReference reference) {
        String classpathPath = CLASSPATH_ROOT + templateRelativePath(reference);
        CacheKey cacheKey = new CacheKey(classpathPath, Thread.currentThread().getContextClassLoader());
        String content = CACHE.computeIfAbsent(cacheKey, ignored -> readTemplate(classpathPath));
        PromptTemplate template = new PromptTemplate(
                reference.templateUri(),
                TemplateDescriptions.extract(content),
                content,
                PromptTemplate.SOURCE_CLASSPATH);
        LOGGER.atDebug().log("negotiation_template_loaded uri={} language={}", reference.uri(), reference.language());
        return template;
    }

    private static String templateRelativePath(NegotiationReference reference) {
        return String.join(
                "/",
                "templates",
                "Negotiation-T",
                reference.typeSegment(),
                NegotiationReference.uriSegmentOf(reference.performative()),
                TemplateUri.DEFAULT_TEMPLATE_VERSION,
                reference.language(),
                TEMPLATE_FILE_NAME);
    }

    private static String readTemplate(String classpathPath) {
        InputStream stream = ClasspathResourceStreams.open(classpathPath);
        if (stream == null) {
            throw new ResourceNotFoundException(
                    "Negotiation template does not exist for the configured language; set A2AT_LANGUAGE to a"
                            + " language with bundled templates (zh-CN or en-US).",
                    classpathPath);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new A2ATError("Failed to read negotiation template: " + classpathPath, exception);
        }
    }

    private record CacheKey(String classpathPath, @Nullable ClassLoader contextClassLoader) {}
}
