package net.openan.a2at.sdk.server.validation;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import org.jspecify.annotations.Nullable;

/**
 * Free-text input gate wrapped around one delegated content validator.
 *
 * <p>The gate rejects an oversized prompt with the code {@code input.text_too_long} before the delegated pipeline
 * starts, so no LLM call is made for an input that could not fit the LLM context anyway. The limit is configured
 * through {@code A2AT_INPUT_TEXT_MAX_CHARS} and defaults to {@link InputLimitConfig#DEFAULT_MAX_TEXT_CHARS} characters.
 *
 * @since 2026-08
 */
public final class InputLimitedContentValidator implements ContentValidator {

    private final ContentValidator delegate;

    private final int maxTextChars;

    private final String language;

    /**
     * Creates one gating validator around the given delegate.
     *
     * @param delegate content validator carrying the actual validation pipeline
     * @param maxTextChars maximum length in characters accepted for the prompt text
     */
    public InputLimitedContentValidator(ContentValidator delegate, int maxTextChars) {
        this(delegate, maxTextChars, null);
    }

    /**
     * Creates one gating validator around the given delegate with an explicit message language.
     *
     * @param delegate content validator carrying the actual validation pipeline
     * @param maxTextChars maximum length in characters accepted for the prompt text
     * @param language language used to render the rejection message, for example {@code zh-CN}; null falls back to
     *     {@code en-US}
     */
    public InputLimitedContentValidator(ContentValidator delegate, int maxTextChars, @Nullable String language) {
        this.delegate = delegate;
        this.maxTextChars = maxTextChars;
        this.language = language;
    }

    @Override
    public FilledParamData validate(String prompt, Map<String, Object> schema, TemplateUri templateUri) {
        if (InputLimitConfig.isTooLong(prompt, maxTextChars)) {
            Map<String, String> facts = Map.of(
                    "actual_length", String.valueOf(prompt.length()),
                    "max_chars", String.valueOf(maxTextChars));
            throw new ContentValidationException(
                    ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(),
                    ErrorMessages.render(ErrorCatalog.INPUT_TEXT_TOO_LONG, language, facts));
        }
        return delegate.validate(prompt, schema, templateUri);
    }
}
