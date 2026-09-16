package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGoldenCases.GoldenCase;
import net.openan.a2at.sdk.negotiation.resources.DefaultNegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the integrity of the bundled negotiation resources from the Java side.
 *
 * <p>The checks mirror the resource contract of the negotiation content layer: the twelve built-in templates must exist
 * and be non-empty, every slot marker line of every template must resolve against the vocabulary of its language, the
 * marker script must match the template language, the four LLM prompt categories must exist non-empty for both
 * languages, and no golden fixture may contain a template requirements line or an unreplaced placeholder.
 *
 * <p>The slot-name-vs-section-title invariant is language-specific: zh-CN placeholders equal their section titles
 * except for the three summary/confirm exception slots, while en-US placeholders are snake_case identifiers that differ
 * from their English section titles by design.
 */
class NegotiationResourceIntegrityTest {

    private static final List<String> PROMPT_CATEGORIES = List.of(
            "information_negotiation",
            "target_negotiation",
            "feasibility_negotiation",
            "abort_negotiation",
            "negotiation_semantic_validation");

    private static final List<String> EXCEPTION_SLOT_KEYS =
            List.of("slot.feasibility", "slot.target", "slot.feasibility_confirm");

    private static final Pattern ZH_SLOT_LINE = Pattern.compile("^\\{\\{(.+?)\\}\\}（(必填|选填)）$");

    private static final Pattern EN_SLOT_LINE = Pattern.compile("^\\{\\{(.+?)\\}\\} \\((required|optional)\\)$");

    @Test
    void allTwelveTemplatesExistAndAreNonEmptyOnTheClasspath() {
        int templateCount = 0;
        for (String language : NegotiationGoldenCases.LANGUAGES) {
            DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader(language);
            for (NegotiationType type : NegotiationType.values()) {
                for (NegotiationPerformative performative :
                        List.of(NegotiationPerformative.PROPOSE, NegotiationPerformative.ACCEPT)) {
                    PromptTemplate template = loader.load(new NegotiationReference(type, performative, language));
                    assertFalse(template.content().isBlank());
                    templateCount++;
                }
            }
        }
        assertTrue(templateCount == 12, "exactly 3 types x 2 phases x 2 languages must exist, got " + templateCount);
    }

    @ParameterizedTest(name = "slot markers resolve against the vocabulary and the marker script [{0}]")
    @ValueSource(strings = {NegotiationGoldenCases.ZH_CN, NegotiationGoldenCases.EN_US})
    void everySlotMarkerResolvesAgainstTheVocabularyOfItsLanguage(String language) {
        Vocabulary vocabulary = Vocabulary.forLanguage(language);
        Map<String, String> valuesToKeys = valuesToKeys(vocabulary);
        Pattern slotLinePattern = NegotiationGoldenCases.ZH_CN.equals(language) ? ZH_SLOT_LINE : EN_SLOT_LINE;
        String requirementsMarker = NegotiationGoldenCases.ZH_CN.equals(language) ? "要求：" : "Requirement:";
        List<String> allSlotNames = new ArrayList<>();
        List<String> slotsDifferingFromTheirTitle = new ArrayList<>();

        for (PromptTemplate template : allTemplates(language)) {
            for (Section section : sectionsOf(template.content())) {
                if (section.slotMarkerLine == null) {
                    continue;
                }
                Matcher matcher = slotLinePattern.matcher(section.slotMarkerLine);
                assertTrue(
                        matcher.matches(),
                        "the slot marker line of section " + section.title + " in "
                                + template.templateUri().uri() + " must use the marker script of " + language + ": "
                                + section.slotMarkerLine);
                String slotName = matcher.group(1);
                assertTrue(
                        valuesToKeys.containsKey(slotName),
                        "the slot name " + slotName + " of "
                                + template.templateUri().uri() + " must be a vocabulary value of " + language);
                assertTrue(
                        valuesToKeys.containsKey(section.title),
                        "the section title " + section.title + " of "
                                + template.templateUri().uri() + " must be a vocabulary value of " + language);
                allSlotNames.add(slotName);
                if (!slotName.equals(section.title)) {
                    slotsDifferingFromTheirTitle.add(slotName);
                }
                assertTrue(
                        section.bodyLines.stream().anyMatch(line -> line.equals(requirementsMarker)),
                        "every slot section must keep its requirements line: " + section.title + " in "
                                + template.templateUri().uri());
            }
        }

        List<String> actualDifferingSlots =
                slotsDifferingFromTheirTitle.stream().distinct().sorted().toList();
        List<String> expectedDifferingSlots;
        String invariantMessage;
        if (NegotiationGoldenCases.ZH_CN.equals(language)) {
            // zh-CN: the placeholder equals the section title except for the three summary/confirm exception slots.
            expectedDifferingSlots =
                    EXCEPTION_SLOT_KEYS.stream().map(vocabulary::get).sorted().toList();
            invariantMessage = "exactly the three pinned exception slots may differ from their section title";
        } else {
            // en-US: every placeholder is a snake_case identifier distinct from its English section title.
            expectedDifferingSlots = allSlotNames.stream().distinct().sorted().toList();
            invariantMessage = "every en-US slot placeholder must differ from its English section title";
        }
        assertTrue(
                expectedDifferingSlots.equals(actualDifferingSlots),
                invariantMessage + " but were " + actualDifferingSlots);
    }

    @Test
    void promptResourcesExistAndAreNonEmptyForEveryCategoryAndLanguage() {
        NegotiationPromptResourceLoader loader = new NegotiationPromptResourceLoader();
        for (String category : PROMPT_CATEGORIES) {
            for (String language : NegotiationGoldenCases.LANGUAGES) {
                assertFalse(loader.loadSystem(category, language).isBlank(), category + "/" + language + "/system.md");
                assertFalse(loader.loadUser(category, language).isBlank(), category + "/" + language + "/user.md");
            }
        }
    }

    @Test
    void thePromptResourceSurfaceContainsNoTypeRecognitionCategory() throws IOException, URISyntaxException {
        URL promptsRoot = Thread.currentThread().getContextClassLoader().getResource("prompt_resources/prompts");
        assertNotNull(promptsRoot, "the prompt resources root must exist on the classpath");
        if (!"file".equals(promptsRoot.getProtocol())) {
            return;
        }
        List<String> categories = new ArrayList<>();
        try (var directory = Files.newDirectoryStream(Path.of(promptsRoot.toURI()))) {
            directory.forEach(entry -> {
                String name = entry.getFileName().toString();
                if (!name.startsWith(".")) {
                    categories.add(name);
                }
            });
        }
        assertTrue(
                !categories.contains("negotiation_type_recognition"),
                "the removed type-recognition prompt category must not reappear: " + categories);
        for (String category : PROMPT_CATEGORIES) {
            assertTrue(categories.contains(category), "the prompt category " + category + " must exist: " + categories);
        }
    }

    @ParameterizedTest(name = "golden outputs carry no requirements line and no placeholder [{0} {1}]")
    @MethodSource("goldenOutputCases")
    void goldenOutputsNeverContainRequirementsLinesOrPlaceholders(GoldenCase goldenCase, String language) {
        String promptText = goldenCase.goldenText(language);

        for (String line : promptText.split("\n")) {
            assertFalse(
                    line.equals("要求：") || line.equals("Requirement:"),
                    "a template requirements line must not enter a rendered message: " + line);
        }
        assertFalse(promptText.contains("{{"), "no unreplaced placeholder may remain in a rendered message");
        assertFalse(promptText.contains("<!--"), "the template description comment must not enter a rendered message");
    }

    static List<Arguments> goldenOutputCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String language : NegotiationGoldenCases.LANGUAGES) {
            for (GoldenCase goldenCase : GoldenCase.values()) {
                cases.add(Arguments.of(goldenCase, language));
            }
        }
        return cases;
    }

    private static Map<String, String> valuesToKeys(Vocabulary vocabulary) {
        Map<String, String> valuesToKeys = new HashMap<>();
        for (String key : vocabulary.canonicalKeys()) {
            valuesToKeys.put(vocabulary.get(key), key);
        }
        return valuesToKeys;
    }

    private static List<PromptTemplate> allTemplates(String language) {
        DefaultNegotiationTemplateLoader loader = new DefaultNegotiationTemplateLoader(language);
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

    private static List<Section> sectionsOf(String templateContent) {
        List<Section> sections = new ArrayList<>();
        Section current = null;
        for (String rawLine : templateContent.split("\n", -1)) {
            String line = rawLine.strip();
            if (line.startsWith("## ")) {
                current = new Section(line.substring(3).strip());
                sections.add(current);
                continue;
            }
            if (current != null) {
                current.bodyLines.add(line);
                if (current.slotMarkerLine == null && line.startsWith("{{")) {
                    current.slotMarkerLine = line;
                }
            }
        }
        return sections;
    }

    private static final class Section {

        private final String title;

        private final List<String> bodyLines = new ArrayList<>();

        private String slotMarkerLine;

        private Section(String title) {
            this.title = title;
        }
    }
}
