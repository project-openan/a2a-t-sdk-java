package net.openan.a2at.sdk.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the bundled {@code prompt_resources/errors/{language}/errors.json} resources rendered through
 * {@link ErrorMessages}.
 *
 * <p>Tests cover the following scenarios:
 *
 * <ul>
 *   <li>The {@code template.not_found} message reports the missing template instead of claiming an unsupported language
 * </ul>
 *
 * @since 2026-09
 */
class ErrorMessagesResourceTest {

    private static final String UNKNOWN_URI = "Task-T/network-layer/does-not-exist/v1";

    private static final Map<String, String> FACTS = Map.of("template_uri", UNKNOWN_URI, "language", "fr-FR");

    /**
     * Verifies the bundled Chinese {@code template.not_found} message reports the missing template and does not echo
     * the configured language.
     *
     * <p>Scenario: Render the catalog entry with a template URI and an unsupported language. Expected result: the
     * message states the template does not exist and omits the language.
     */
    @Test
    void should_reportMissingTemplate_When_renderedInChinese() {
        String message = ErrorMessages.render(ErrorCatalog.TEMPLATE_NOT_FOUND, "zh-CN", FACTS);

        assertEquals("模板「" + UNKNOWN_URI + "」不存在", message);
        assertFalse(message.contains("fr-FR"), message);
    }

    /**
     * Verifies the bundled English {@code template.not_found} message reports the missing template and does not echo
     * the configured language.
     *
     * <p>Scenario: Render the catalog entry with a template URI and an unsupported language. Expected result: the
     * message states the template does not exist and omits the language.
     */
    @Test
    void should_reportMissingTemplate_When_renderedInEnglish() {
        String message = ErrorMessages.render(ErrorCatalog.TEMPLATE_NOT_FOUND, "en-US", FACTS);

        assertEquals("Template '" + UNKNOWN_URI + "' does not exist", message);
        assertFalse(message.contains("fr-FR"), message);
    }
}
