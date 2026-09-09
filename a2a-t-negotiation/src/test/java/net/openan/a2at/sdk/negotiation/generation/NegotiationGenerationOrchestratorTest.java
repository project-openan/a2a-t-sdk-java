package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateQueryService;
import org.junit.jupiter.api.Test;

class NegotiationGenerationOrchestratorTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final String INFORMATION_PROPOSE_URI = INFORMATION_PROPOSE.uri();

    @Test
    void generatesInformationProposeFromDataInChinese() {
        MetadataContent result = zhOrchestrator()
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                        INFORMATION_PROPOSE);

        assertEquals(INFORMATION_PROPOSE_URI, result.templateUri());
        assertEquals(NegotiationHandler.NEGOTIATION_T_URI, result.extensionUri());
        assertFalse(result.promptText().isBlank());
        assertFalse(result.promptText().contains("协商上下文"), "the context section must not be rendered");
        assertTrue(result.promptText().contains("所需信息项"));

        Map<String, Object> metadata = result.buildMetadataContent();
        assertEquals(3, metadata.size());
        assertEquals(result.promptText(), metadata.get(NegotiationHandler.NEGOTIATION_T_URI));
        assertEquals(result.templateUri(), metadata.get(MetadataContent.TEMPLATE_URI_METADATA_KEY));
        assertEquals(new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE), result.negotiationContext());
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedContext =
                (Map<String, Object>) metadata.get(MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY);
        assertEquals(UUID, nestedContext.get("id"));
        assertEquals(1, nestedContext.get("round"));
        assertEquals(5, nestedContext.get("maxRounds"));
        assertEquals("PROPOSE", nestedContext.get("performative"));
    }

    @Test
    void generatesInformationProposeFromDataInEnglish() {
        MetadataContent result = orchestrator("en-US")
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE),
                                new InformationProposeContent(
                                        List.of(new NegotiationItem("Region", "Songshan Lake")), null)),
                        INFORMATION_PROPOSE);

        assertEquals(INFORMATION_PROPOSE_URI, result.templateUri());
        assertFalse(result.promptText().isBlank());
        assertFalse(result.promptText().contains("Negotiation Context"), "the context section must not be rendered");
        assertEquals(new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE), result.negotiationContext());
        assertTrue(result.promptText().contains("Required Information Items"));
    }

    @Test
    void generatesProposeFromTextWithScriptedExtraction() {
        ScriptedClient llm =
                new ScriptedClient("{\"items\":[{\"name\":\"故障发生时间\",\"value\":\"精确到分钟的时间点\"}],\"relationship\":null}");
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();

        MetadataContent result = orchestrator.generateProposeFromText(
                "请提供故障发生时间。", new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE), INFORMATION_PROPOSE);

        assertEquals(1, llm.calls);
        assertEquals(INFORMATION_PROPOSE_URI, result.templateUri());
        assertFalse(result.promptText().isBlank());
        assertEquals(2, result.negotiationContext().round());
        assertTrue(result.promptText().contains("故障发生时间"));
    }

    @Test
    void validatesAndFillsProposeDataWithScriptedSemanticResponse() {
        ScriptedClient llm =
                new ScriptedClient("{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"region\":\"松山湖\"}}");
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();
        MetadataContent message = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE);

        FilledParamData filled = orchestrator.validateProposePromptAndDataFilling(
                message.promptText(),
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                Map.of("type", "object", "properties", Map.of("region", Map.of("type", "string"))),
                INFORMATION_PROPOSE);

        assertEquals(1, llm.calls);
        assertEquals(UUID, filled.data().get("id"));
        assertEquals(1, filled.data().get("round"));
        assertEquals(5, filled.data().get("maxRounds"));
        assertEquals("松山湖", filled.data().get("region"));
    }

    @Test
    void listsAllSevenTemplatesPerLanguage() {
        assertEquals(7, negotiationPrompts("zh-CN").size());
        assertEquals(7, negotiationPrompts("en-US").size());
    }

    @Test
    void queriesSingleTemplateByUri() {
        assertTrue(queryService("zh-CN").getPrompt(INFORMATION_PROPOSE).isPresent());
        assertTrue(queryService("zh-CN")
                .getPrompt(StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT)
                .isPresent());
        assertFalse(queryService("zh-CN")
                .getPrompt(TemplateUri.of("Negotiation-T", "unknown-negotiation", "propose"))
                .isPresent());
    }

    @Test
    void retriesContentExtractionUntilItSucceeds() {
        ScriptedClient llm = new ScriptedClient("", "", "{\"items\":[{\"name\":\"区域\",\"value\":\"松山湖\"}]}");
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .maxAttempts(3)
                .build();

        MetadataContent result = orchestrator.generateProposeFromText(
                "请提供区域。", new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE), INFORMATION_PROPOSE);

        assertEquals(3, llm.calls);
        assertFalse(result.promptText().isBlank());
    }

    @Test
    void rethrowsOriginalCodeWhenRetriesAreExhausted() {
        FailingClient llm = new FailingClient();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .maxAttempts(2)
                .build();

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供区域。",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        INFORMATION_PROPOSE));

        assertEquals(2, llm.calls);
        assertEquals(ErrorCatalog.LLM_INVOCATION_FAILED.getCode(), failure.getCode());
    }

    @Test
    void reportsMissingTemplateAsTemplateNotFoundOnBothPipelines() {
        NegotiationGenerationOrchestrator generationOrchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .templateLoader(missingTemplateLoader())
                .build();

        NegotiationGenerationException generationFailure = assertThrows(
                NegotiationGenerationException.class,
                () -> generationOrchestrator.generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                new InformationProposeContent(List.of(new NegotiationItem("区域", "松山湖")), null)),
                        INFORMATION_PROPOSE));
        assertEquals(ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(), generationFailure.getCode());

        NegotiationGenerationOrchestrator validationOrchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .semanticValidator((prompt, callerSchema, reference, templateContent) -> {
                    throw new ResourceNotFoundException(
                            "Semantic validation prompt does not exist.", "prompt_resources/prompts");
                })
                .build();

        NegotiationParamExtractionException extractionFailure = assertThrows(
                NegotiationParamExtractionException.class,
                () -> validationOrchestrator.validateProposePromptAndDataFilling(
                        "## 所需信息项\n1. 区域\n",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        Map.of("type", "object"),
                        INFORMATION_PROPOSE));
        assertEquals(ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(), extractionFailure.getCode());
    }

    @Test
    void validationTemplateLoadFailurePreservesTheOriginalCause() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .templateLoader(missingTemplateLoader())
                .build();

        NegotiationParamExtractionException failure = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        "## 所需信息项\n1. 区域\n",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        Map.of("type", "object"),
                        INFORMATION_PROPOSE));

        assertEquals(ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(), failure.getCode());
        assertTrue(
                failure.getCause() instanceof ContentValidationException,
                "the template-load failure must preserve the mapped pipeline failure as cause for debugging");
        assertTrue(
                failure.getCause().getCause() instanceof ResourceNotFoundException,
                "the original resource failure stays reachable through the cause chain");
    }

    @Test
    void rejectsNonNegotiationPromptInParameterExtraction() {
        ScriptedClient llm = new ScriptedClient(
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":{}}");
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .build();

        NegotiationParamExtractionException failure = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        "plain text without any negotiation section",
                        null,
                        Map.of("type", "object"),
                        INFORMATION_PROPOSE));

        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), failure.getCode());
        assertEquals(0, llm.calls);
    }

    @Test
    void rejectsTemplateUriThatDoesNotAddressTheExpectedPerformative() {
        IllegalArgumentException performativeMismatchFailure =
                assertThrows(IllegalArgumentException.class, () -> zhOrchestrator()
                        .generateProposeFromData(
                                new NegotiationProposeData(
                                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                        new InformationProposeContent(List.of(new NegotiationItem("区域", "松山湖")), null)),
                                StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT));
        assertTrue(performativeMismatchFailure
                .getMessage()
                .contains("Template URI does not address a negotiation template of the expected performative"
                        + " PROPOSE (propose)"));

        IllegalArgumentException wrongExtensionFailure =
                assertThrows(IllegalArgumentException.class, () -> zhOrchestrator()
                        .generateProposeFromData(
                                new NegotiationProposeData(
                                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                        new InformationProposeContent(List.of(new NegotiationItem("区域", "松山湖")), null)),
                                StandardTemplates.ENERGY_SAVING));
        assertTrue(wrongExtensionFailure
                .getMessage()
                .contains("Template URI does not address a negotiation template of the expected performative"
                        + " PROPOSE (propose)"));
    }

    private static NegotiationGenerationOrchestrator zhOrchestrator() {
        return orchestrator("zh-CN");
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();
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

    private static NegotiationTemplateLoader missingTemplateLoader() {
        return new NegotiationTemplateLoader() {
            @Override
            public PromptTemplate load(NegotiationReference reference) {
                throw new ResourceNotFoundException("Negotiation template does not exist.", reference.uri());
            }
        };
    }

    private static final class ScriptedClient implements LLMClient {

        private final List<String> payloads;

        private int calls;

        private ScriptedClient(String... payloads) {
            this.payloads = List.of(payloads);
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            String payload = payloads.get(Math.min(calls, payloads.size() - 1));
            calls++;
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }

    private static final class FailingClient implements LLMClient {

        private int calls;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls++;
            throw new IllegalStateException("LLM endpoint unavailable.");
        }
    }
}
