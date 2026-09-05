package net.openan.a2at.sdk.core.exception;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders error messages from the bundled {@code prompt_resources/errors/{language}/errors.json} templates.
 *
 * <p>The language follows the {@code A2AT_LANGUAGE} configuration resolved by the caller (default {@code en-US}). A
 * template missing in the requested language falls back to {@code en-US}; a template missing in both languages renders
 * as the bare code string. Placeholders are {@code {name}} tokens; a placeholder without a matching fact value is kept
 * literal, matching the template style of the A2A-T prompt resources.
 *
 * @since 2026-08
 */
public final class ErrorMessages {

    /** Language used when no language is configured or a template is missing in the requested language. */
    public static final String DEFAULT_LANGUAGE = "en-US";

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorMessages.class);

    private static final String RESOURCE_PATTERN = "prompt_resources/errors/%s/errors.json";

    private static final TypeReference<Map<String, String>> TEMPLATE_MAP_TYPE =
            new TypeReference<Map<String, String>>() {};

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)\\}");

    private static final Map<String, Map<String, String>> TEMPLATES_BY_LANGUAGE = new ConcurrentHashMap<>();

    private ErrorMessages() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Renders the message of one catalog entry in one language.
     *
     * @param entry catalog entry whose message template is rendered
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     * @param facts fact values keyed by fact parameter name; null values render as the literal placeholder
     * @return rendered message, never null
     * @throws NullPointerException if {@code entry} is null
     */
    public static String render(
            @NonNull ErrorCatalog entry, @Nullable String language, @Nullable Map<String, String> facts) {
        return render(entry.getCode(), language, facts);
    }

    /**
     * Renders the message of one error code in one language.
     *
     * @param code layered error code, for example {@code content.param_missing}
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     * @param facts fact values keyed by fact parameter name; null values render as the literal placeholder
     * @return rendered message, never null; the bare code when no template exists in either language
     * @throws NullPointerException if {@code code} is null
     */
    public static String render(@NonNull String code, @Nullable String language, @Nullable Map<String, String> facts) {
        String template = resolveTemplate(code, language);
        if (template == null) {
            LOGGER.warn(
                    "No error message template found for code '{}' in language '{}' or '{}'.",
                    code,
                    language,
                    DEFAULT_LANGUAGE);
            return code;
        }
        return renderTemplate(template, facts);
    }

    /**
     * Returns the message template of one error code in one language, without rendering it.
     *
     * @param code layered error code
     * @param language message language; null or blank falls back to {@code en-US}
     * @return template text, or null when no template exists in the requested language or {@code en-US}
     * @throws NullPointerException if {@code code} is null
     */
    public static @Nullable String template(@NonNull String code, @Nullable String language) {
        return resolveTemplate(code, language);
    }

    private static @Nullable String resolveTemplate(String code, String language) {
        String normalized = normalizeLanguage(language);
        String template = templates(normalized).get(code);
        if (template == null && !DEFAULT_LANGUAGE.equals(normalized)) {
            template = templates(DEFAULT_LANGUAGE).get(code);
        }
        return template;
    }

    private static Map<String, String> templates(String language) {
        return TEMPLATES_BY_LANGUAGE.computeIfAbsent(language, ErrorMessages::loadTemplates);
    }

    private static Map<String, String> loadTemplates(String language) {
        String resourcePath = String.format(RESOURCE_PATTERN, language);
        try (InputStream stream = ClasspathResourceStreams.open(resourcePath)) {
            if (stream == null) {
                LOGGER.warn("Error message resource '{}' not found on the classpath.", resourcePath);
                return Map.of();
            }
            Map<String, String> templates = new ObjectMapper().readValue(stream, TEMPLATE_MAP_TYPE);
            return Map.copyOf(templates);
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Cannot load error message resource '{}': {}", resourcePath, error.getMessage());
            return Map.of();
        }
    }

    private static String renderTemplate(String template, Map<String, String> facts) {
        if (facts == null || facts.isEmpty()) {
            return template;
        }
        StringBuilder rendered = new StringBuilder();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String value = facts.get(matcher.group(1));
            String replacement = value != null ? value : matcher.group(0);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String trimmed = language.trim();
        return trimmed.isEmpty() ? DEFAULT_LANGUAGE : trimmed;
    }
}
