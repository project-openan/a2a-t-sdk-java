package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGoldenCases.GoldenCase;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the template-URI entry matrix of the from-data generation.
 *
 * <p>The seven built-in URIs (six typed negotiation templates plus the common abort template) address their templates
 * and render outputs identical to the golden fixtures; a well-formed URI that resolves to no template fails with the
 * code {@code template.not_found} before any LLM call; a typed URI that does not address a negotiation template of the
 * expected performative (wrong extension name, version, type segment, performative segment or separator, including the
 * underscore misspelling of the type segment) fails as a programming error pointing at {@code templateUri}, while
 * structural malformation is impossible by construction of {@link TemplateUri}.
 */
class TemplateUriEntryMatrixTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    /**
     * Entry (a): every one of the seven built-in URIs (six typed templates plus the common abort template) reaches its
     * template and renders output byte-identical to the golden fixtures locked by the comparison test.
     */
    @Test
    void everyBuiltInUriRendersItsGoldenFixture() {
        for (String language : NegotiationGoldenCases.LANGUAGES) {
            NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                    .language(language)
                    .build();
            Map<String, List<GoldenCase>> casesByUri = new LinkedHashMap<>();
            for (GoldenCase goldenCase : GoldenCase.values()) {
                casesByUri
                        .computeIfAbsent(goldenCase.templateUri(), uri -> new ArrayList<>())
                        .add(goldenCase);
            }
            assertEquals(7, casesByUri.size(), "the built-in template set has exactly seven URIs");
            for (Map.Entry<String, List<GoldenCase>> entry : casesByUri.entrySet()) {
                for (GoldenCase goldenCase : entry.getValue()) {
                    MetadataContent result = goldenCase.generate(orchestrator, language);
                    assertEquals(entry.getKey(), result.templateUri());
                    assertEquals(goldenCase.goldenText(language), result.promptText());
                }
            }
        }
    }

    /**
     * Entry (b): a well-formed URI that resolves to no template in any resource root fails with the code
     * {@code template.not_found} before any LLM call. With the bundled resources every well-formed v1 URI resolves in
     * both bundled languages, so the always-miss condition is realized by a loader whose every load misses.
     */
    @Test
    void wellFormedUriMissingInBothRootsFailsWithTemplateNotFound() {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .templateLoader(new MissingTemplateLoader())
                .llmClient(llm)
                .build();

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromData(informationProposeData(), INFORMATION_PROPOSE_URI));

        assertEquals(ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(), failure.getCode());
        assertTrue(
                failure.getMessage() != null && !failure.getMessage().isBlank(),
                "the load failure message must be surfaced");
        assertEquals(0, llm.calls, "template loading happens before any LLM call");
    }

    /**
     * Entry (c): every typed URI that does not address a negotiation template of the expected performative — wrong
     * extension name, version, type segment, performative segment and separator (underscore misspelling), plus unknown
     * types and unknown performative segments — fails as a programming error with an {@link IllegalArgumentException}
     * pointing at the template URI, without any LLM call. Structurally malformed URIs cannot exist as
     * {@link TemplateUri} values.
     */
    @ParameterizedTest(name = "non-addressing URI [{0}] is rejected as a templateUri programming error")
    @MethodSource("nonAddressingTemplateUris")
    void nonAddressingUriIsRejectedAsATemplateUriProgrammingError(TemplateUri templateUri) {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.generateProposeFromData(informationProposeData(), templateUri));

        assertTrue(
                failure.getMessage()
                        .contains(
                                "Template URI does not address a negotiation template of the expected performative PROPOSE"),
                "the failure must point at the template URI but was: " + failure.getMessage());
        assertEquals(0, llm.calls);
    }

    static List<TemplateUri> nonAddressingTemplateUris() {
        return List.of(
                TemplateUri.of("Task-T", "information-negotiation", "propose"),
                TemplateUri.of("Negotiation-T", List.of("information-negotiation", "propose"), "v2"),
                TemplateUri.of("Negotiation-T", "information", "propose"),
                TemplateUri.of("Negotiation-T", "information_negotiation", "propose"),
                TemplateUri.of("Negotiation-T", "unknown-negotiation", "propose"),
                TemplateUri.of("Negotiation-T", "information-negotiation", "propose-x"),
                TemplateUri.of("Negotiation-T", "information-negotiation", "accept"));
    }

    private static NegotiationProposeData informationProposeData() {
        return new NegotiationProposeData(
                new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE),
                new InformationProposeContent(
                        List.of(new NegotiationItem("接入端口名称", "P533-珠江旧城-PTN3900-23-TPA1EG24-1")), null));
    }

    private static final class MissingTemplateLoader implements NegotiationTemplateLoader {

        @Override
        public PromptTemplate load(NegotiationReference reference) {
            throw new ResourceNotFoundException(
                    "Negotiation template does not exist in any resource root.", reference.uri());
        }
    }

    private static final class CountingClient implements LLMClient {

        private int calls;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls++;
            throw new AssertionError("The entry matrix must fail before any LLM call");
        }
    }
}
