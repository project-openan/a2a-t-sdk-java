package net.openan.a2at.sdk.negotiation.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGoldenCases;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGoldenCases.GoldenCase;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Locks the language switching of the negotiation content layer.
 *
 * <p>The same typed input is rendered through the built-in resources of each bundled language: the zh-CN pipeline and
 * the en-US pipeline must each reproduce that language's golden fixture byte for byte, and the two results must differ
 * because the section titles, labels and punctuation come from the per-language vocabulary. The template query methods
 * must answer with the templates of the configured language only.
 */
class NegotiationLanguageSwitchTest {

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    static Stream<GoldenCase> goldenCases() {
        return Stream.of(GoldenCase.values());
    }

    @ParameterizedTest(name = "the same input renders each language golden fixture [{0}]")
    @MethodSource("goldenCases")
    void sameInputRendersTheRespectiveGoldenFixturePerLanguage(GoldenCase goldenCase) {
        NegotiationGenerationOrchestrator zhCnOrchestrator = orchestrator(NegotiationGoldenCases.ZH_CN);
        NegotiationGenerationOrchestrator enUsOrchestrator = orchestrator(NegotiationGoldenCases.EN_US);

        MetadataContent zhCnResult = goldenCase.generate(zhCnOrchestrator, NegotiationGoldenCases.ZH_CN);
        MetadataContent enUsResult = goldenCase.generate(enUsOrchestrator, NegotiationGoldenCases.EN_US);

        assertEquals(goldenCase.goldenText(NegotiationGoldenCases.ZH_CN), zhCnResult.promptText());
        assertEquals(goldenCase.goldenText(NegotiationGoldenCases.EN_US), enUsResult.promptText());
        assertNotEquals(zhCnResult.promptText(), enUsResult.promptText());
        assertEquals(goldenCase.templateUri(), zhCnResult.templateUri());
        assertEquals(goldenCase.templateUri(), enUsResult.templateUri());
    }

    @ParameterizedTest(name = "the query methods list the templates of one language only [{0}]")
    @ValueSource(strings = {NegotiationGoldenCases.ZH_CN, NegotiationGoldenCases.EN_US})
    void templateQueriesReturnTheTemplatesOfTheConfiguredLanguage(String language) {
        String requirementLabel = NegotiationGoldenCases.ZH_CN.equals(language) ? "要求：" : "Requirement:";
        String otherLanguageLabel = NegotiationGoldenCases.ZH_CN.equals(language) ? "Requirement:" : "要求：";

        List<PromptTemplate> templates = negotiationPrompts(language);

        assertEquals(7, templates.size());
        for (PromptTemplate template : templates) {
            assertTrue(
                    template.content().contains(requirementLabel),
                    template.templateUri().uri() + " must be a " + language + " template");
            assertTrue(
                    !template.content().contains(otherLanguageLabel),
                    template.templateUri().uri() + " must not leak the other language");
        }

        PromptTemplate queriedTemplate =
                queryService(language).getPrompt(INFORMATION_PROPOSE_URI).orElseThrow();
        assertTrue(queriedTemplate.content().contains(requirementLabel));
    }

    @Test
    void bothLanguagesAnswerQueriesWithDifferentTemplatesForTheSameUri() {
        PromptTemplate zhCnTemplate = queryService(NegotiationGoldenCases.ZH_CN)
                .getPrompt(INFORMATION_PROPOSE_URI)
                .orElseThrow();
        PromptTemplate enUsTemplate = queryService(NegotiationGoldenCases.EN_US)
                .getPrompt(INFORMATION_PROPOSE_URI)
                .orElseThrow();

        assertEquals(INFORMATION_PROPOSE_URI, zhCnTemplate.templateUri());
        assertEquals(INFORMATION_PROPOSE_URI, enUsTemplate.templateUri());
        assertNotEquals(zhCnTemplate.content(), enUsTemplate.content());
        assertEquals(
                negotiationPrompts(NegotiationGoldenCases.ZH_CN).stream()
                        .map(PromptTemplate::templateUri)
                        .toList(),
                negotiationPrompts(NegotiationGoldenCases.EN_US).stream()
                        .map(PromptTemplate::templateUri)
                        .toList());
    }

    private static TemplateQueryService queryService(String language) {
        return new TemplateQueryService(language, "classpath", null);
    }

    private static List<PromptTemplate> negotiationPrompts(String language) {
        return queryService(language).getPrompts().stream()
                .filter(template -> StandardTemplates.NEGOTIATION_EXTENSION_NAME.equals(
                        template.templateUri().extensionName()))
                .toList();
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();
    }
}
