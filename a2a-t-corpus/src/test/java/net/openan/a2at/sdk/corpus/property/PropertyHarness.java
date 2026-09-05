package net.openan.a2at.sdk.corpus.property;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.corpus.LlmFailMarker;
import net.openan.a2at.sdk.corpus.LlmScriptStep;
import net.openan.a2at.sdk.corpus.ScriptedNegotiationLlmClient;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;

/**
 * Shared plumbing of the jqwik property layer: production-wired service assembly through the real builder, JSON payload
 * construction and the scripted-LLM seam reused from the corpus testdata package.
 *
 * <p>Mirroring the {@code CaseEngine} discipline, everything except the LLM client is production assembly; the property
 * layer only decides inputs and expected invariants.
 *
 * @since 2026-08
 */
final class PropertyHarness {

    /** Default attempt limit of the property runs; the retry partition property depends on this exact value. */
    static final int MAX_ATTEMPTS = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PropertyHarness() {}

    /**
     * Assembles the production negotiation content service for one property run.
     *
     * @param language message language such as {@code zh-CN}
     * @param llmClient scripted LLM client of the run
     * @return production-wired negotiation content service
     */
    static NegotiationContentService service(String language, LLMClient llmClient) {
        return new NegotiationContentService(orchestrator(language, llmClient));
    }

    /**
     * Assembles the production service with a template loader that fails every load, the property-layer stand-in of the
     * corpus {@code inject: failingTemplateLoader} hook.
     *
     * @param language message language
     * @param llmClient scripted LLM client of the run
     * @return production-wired service whose template loader always misses
     */
    static NegotiationContentService serviceWithFailingTemplateLoader(String language, LLMClient llmClient) {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .llmClient(llmClient)
                .maxAttempts(MAX_ATTEMPTS)
                .templateLoader(failingTemplateLoader())
                .build();
        return new NegotiationContentService(orchestrator);
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language, LLMClient llmClient) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .llmClient(llmClient)
                .maxAttempts(MAX_ATTEMPTS)
                .build();
    }

    private static NegotiationTemplateLoader failingTemplateLoader() {
        return new NegotiationTemplateLoader() {
            @Override
            public PromptTemplate load(NegotiationReference reference) {
                throw new ResourceNotFoundException("Negotiation template does not exist.", reference.uri());
            }
        };
    }

    /**
     * Parses one raw template URI string.
     *
     * @param raw template URI such as {@code Negotiation-T/information-negotiation/propose/v1}
     * @return parsed template URI
     */
    static TemplateUri templateUri(String raw) {
        return TemplateUri.parse(raw)
                .orElseThrow(() -> new IllegalArgumentException("Unparseable template URI: " + raw));
    }

    /**
     * Serializes one value into the JSON payload text of a scripted LLM step.
     *
     * @param value payload value
     * @return JSON text
     */
    static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The property payload could not be serialized.", exception);
        }
    }

    /**
     * Builds the semantic-validation payload of an accepting verdict carrying the given extracted parameters.
     *
     * @param negotiationType negotiation type name the verdict reports, or null for the type-independent abort phase
     * @param params extracted parameters the scripted validator returns
     * @return JSON payload text of the scripted semantic-validation answer
     */
    static String semanticVerdict(String negotiationType, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("semantic_verdict", true);
        payload.put("negotiation_type", negotiationType);
        payload.put("errors", List.of());
        payload.put("params", params);
        return json(payload);
    }

    /**
     * Creates a scripted client answering exactly one payload step.
     *
     * @param payloadJson payload text of the single answer
     * @return strictly consuming scripted client
     */
    static ScriptedNegotiationLlmClient scripted(String payloadJson) {
        return new ScriptedNegotiationLlmClient(List.of(new LlmScriptStep.Payload(payloadJson)));
    }

    /**
     * Creates a scripted client replaying one failure marker for every attempt of the run.
     *
     * @param marker failure marker to replay
     * @return strictly consuming scripted client that always fails
     */
    static ScriptedNegotiationLlmClient failing(LlmFailMarker marker) {
        return new ScriptedNegotiationLlmClient(java.util.stream.IntStream.rangeClosed(1, MAX_ATTEMPTS)
                .mapToObj(ignored -> (LlmScriptStep) new LlmScriptStep.Fail(marker))
                .toList());
    }

    /**
     * Builds a flat object JSON Schema over the given properties.
     *
     * @param properties per-key type schemas keyed by parameter name
     * @return caller parameter schema of a validate run
     */
    static Map<String, Object> objectSchema(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    /**
     * Derives the JSON Schema type of one parameter value.
     *
     * @param value parameter value generated by a property
     * @return single-key type schema
     */
    static Map<String, Object> typeSchema(Object value) {
        if (value instanceof String) {
            return Map.of("type", "string");
        }
        if (value instanceof Boolean) {
            return Map.of("type", "boolean");
        }
        if (value instanceof Double) {
            return Map.of("type", "number");
        }
        return Map.of("type", "integer");
    }
}
