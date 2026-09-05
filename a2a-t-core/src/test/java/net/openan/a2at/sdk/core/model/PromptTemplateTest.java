package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PromptTemplate}.
 *
 * <p>Tests cover the four-component record shape and the {@code source()} accessor that identifies the effective
 * resource origin ({@code classpath} / {@code local}).
 *
 * @since 2026-08
 */
class PromptTemplateTest {

    /**
     * Verifies that {@link PromptTemplate} exposes all four components.
     *
     * <p>Scenario: Construct a template with a URI, description, content and a {@code classpath} source. Expected
     * result: templateUri(), description(), content() and source() each return the supplied value.
     */
    @Test
    void should_exposeAllFourComponents_When_templateConstructedWithClasspathSource() {
        TemplateUri templateUri = TemplateUri.of("Task-T", "network-layer", "ran-energy-saving");

        PromptTemplate template = new PromptTemplate(templateUri, "energy saving", "# template", "classpath");

        assertEquals(templateUri, template.templateUri());
        assertEquals("energy saving", template.description());
        assertEquals("# template", template.content());
        assertEquals("classpath", template.source());
    }

    /**
     * Verifies that {@link PromptTemplate#source()} carries the {@code local} source value.
     *
     * <p>Scenario: Construct a template with a {@code local} source. Expected result: source() returns {@code local}.
     */
    @Test
    void should_exposeLocalSource_When_templateConstructedWithLocalSource() {
        TemplateUri templateUri = TemplateUri.of("Task-T", "network-layer", "ran-energy-saving");

        PromptTemplate template = new PromptTemplate(templateUri, "", null, "local");

        assertEquals(templateUri, template.templateUri());
        assertEquals("", template.description());
        assertNull(template.content());
        assertEquals("local", template.source());
    }
}
