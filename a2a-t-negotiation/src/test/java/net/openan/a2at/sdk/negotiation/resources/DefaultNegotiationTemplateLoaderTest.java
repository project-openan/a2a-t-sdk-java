package net.openan.a2at.sdk.negotiation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.junit.jupiter.api.Test;

class DefaultNegotiationTemplateLoaderTest {

    private static final List<String> EXPECTED_LOAD_ALL_URIS = List.of(
            StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(),
            StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT.uri(),
            StandardTemplates.TARGET_NEGOTIATION_PROPOSE.uri(),
            StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT.uri(),
            StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE.uri(),
            StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT.uri(),
            StandardTemplates.NEGOTIATION_ABORT.uri());

    @Test
    void loadReturnsAllSevenBuiltinTemplatesPerLanguage() {
        DefaultNegotiationTemplateLoader zhCnLoader = new DefaultNegotiationTemplateLoader("zh-CN");
        DefaultNegotiationTemplateLoader enUsLoader = new DefaultNegotiationTemplateLoader("en-US");

        List<PromptTemplate> zhCnTemplates = loadAllOf(zhCnLoader, "zh-CN");
        List<PromptTemplate> enUsTemplates = loadAllOf(enUsLoader, "en-US");

        assertEquals(EXPECTED_LOAD_ALL_URIS, urisOf(zhCnTemplates));
        assertEquals(EXPECTED_LOAD_ALL_URIS, urisOf(enUsTemplates));
        assertEquals(7, zhCnTemplates.size());
        assertEquals(7, enUsTemplates.size());
        assertEquals(14, zhCnTemplates.size() + enUsTemplates.size());
    }

    @Test
    void loadReturnsTheCommonAbortTemplateForTheAbortPerformative() {
        DefaultNegotiationTemplateLoader enUsLoader = new DefaultNegotiationTemplateLoader("en-US");
        DefaultNegotiationTemplateLoader zhCnLoader = new DefaultNegotiationTemplateLoader("zh-CN");

        PromptTemplate englishTemplate =
                enUsLoader.load(new NegotiationReference(null, NegotiationPerformative.ABORT, "en-US"));
        PromptTemplate chineseTemplate =
                zhCnLoader.load(new NegotiationReference(null, NegotiationPerformative.ABORT, "zh-CN"));

        assertEquals(StandardTemplates.NEGOTIATION_ABORT, englishTemplate.templateUri());
        assertTrue(englishTemplate.content().startsWith("## Negotiation Result"));
        assertTrue(englishTemplate.content().contains("## Negotiation Termination Reason"));
        assertTrue(englishTemplate.content().contains("{{negotiation_termination_reason}}"));
        assertEquals(StandardTemplates.NEGOTIATION_ABORT, chineseTemplate.templateUri());
        assertTrue(chineseTemplate.content().contains("## 协商终止原因"));
        assertTrue(chineseTemplate.content().contains("{{协商终止原因}}"));
    }

    @Test
    void loadReturnsFullTemplateContentFromTheClasspath() {
        DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader("en-US");

        PromptTemplate template = loader.load(
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "en-US"));

        assertEquals(StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE, template.templateUri());
        assertTrue(template.content().startsWith("## Information Negotiation"));
    }

    @Test
    void builtinTemplatesCarryNoDescriptionCommentForEitherLanguage() {
        DefaultNegotiationTemplateLoader zhCnLoader = new DefaultNegotiationTemplateLoader("zh-CN");
        DefaultNegotiationTemplateLoader enUsLoader = new DefaultNegotiationTemplateLoader("en-US");

        PromptTemplate englishTemplate = enUsLoader.load(
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "en-US"));
        PromptTemplate chineseTemplate = zhCnLoader.load(
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "zh-CN"));

        assertEquals("", englishTemplate.description());
        assertEquals("", chineseTemplate.description());
    }

    @Test
    void missingLanguageThrowsOnLoad() {
        DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader("fr-FR");

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> loader.load(new NegotiationReference(
                        NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "fr-FR")));

        assertTrue(exception.getMessage().contains("A2AT_LANGUAGE"));
    }

    @Test
    void doesNotCacheMissingTemplatesAndKeepsThrowing() {
        DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader("fr-FR");
        NegotiationReference reference =
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "fr-FR");

        assertThrows(ResourceNotFoundException.class, () -> loader.load(reference));
        assertThrows(ResourceNotFoundException.class, () -> loader.load(reference));
    }

    @Test
    void servesTheCachedValueAfterTheTemplateFileIsAlteredOnDisk() throws Exception {
        Path tempRoot = Files.createTempDirectory("negotiation-template-cache-test");
        Path templateFile = tempRoot.resolve(
                "prompt_resources/templates/Negotiation-T/information-negotiation/propose/v1/en-US/template.md");
        Files.createDirectories(templateFile.getParent());
        Files.writeString(templateFile, "original content", StandardCharsets.UTF_8);

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader shadowingClassLoader =
                new URLClassLoader(new URL[] {tempRoot.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(shadowingClassLoader);

            DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader("en-US");
            NegotiationReference reference =
                    new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "en-US");

            String first = loader.load(reference).content();

            Files.writeString(templateFile, "changed on disk", StandardCharsets.UTF_8);

            assertEquals(first, loader.load(reference).content());
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            deleteRecursively(tempRoot);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        }
    }

    private static List<PromptTemplate> loadAllOf(DefaultNegotiationTemplateLoader loader, String language) {
        List<PromptTemplate> templates = new ArrayList<>();
        for (NegotiationType type : NegotiationType.values()) {
            for (NegotiationPerformative performative :
                    List.of(NegotiationPerformative.PROPOSE, NegotiationPerformative.ACCEPT)) {
                templates.add(loader.load(new NegotiationReference(type, performative, language)));
            }
        }
        templates.add(loader.load(new NegotiationReference(null, NegotiationPerformative.ABORT, language)));
        return templates;
    }

    private static List<String> urisOf(List<PromptTemplate> templates) {
        return templates.stream().map(template -> template.templateUri().uri()).toList();
    }
}
