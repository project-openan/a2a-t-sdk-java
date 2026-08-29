package net.openan.a2at.sample.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the output symmetry of the negotiation content layer across the client and the server facade.
 *
 * <p>Both facades expose the twelve negotiation API methods as one symmetric surface and produce, for identical inputs,
 * identical generation results: the whole MetadataContent record and the metadata map built from it are equal on both
 * sides. The inputs are the fixed golden fixture inputs of the negotiation module, so this test also proves that both
 * facades are wired to the same built-in resources.
 */
class NegotiationFacadeOutputSymmetryTest {

    private static final String TEST_MOCK_PROVIDER = "test-symmetry-mock";

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, RecordingClient.class);
        }
    }

    private static final List<String> NEGOTIATION_API_METHODS = List.of(
            "generateNegotiationProposePromptFromData",
            "generateNegotiationAcceptPromptFromData",
            "generateNegotiationRejectPromptFromData",
            "generateNegotiationAbortPromptFromData",
            "generateNegotiationProposePromptFromText",
            "generateNegotiationAcceptPromptFromText",
            "generateNegotiationRejectPromptFromText",
            "generateNegotiationAbortPromptFromText",
            "validateProposePromptAndDataFilling",
            "validateAcceptPromptAndDataFilling",
            "validateRejectPromptAndDataFilling",
            "validateAbortPromptAndDataFilling");

    @TempDir
    Path tempDir;

    @Test
    void bothFacadesExposeTheSameTwelveMethodSignatures() {
        Map<String, String> clientSurface = negotiationApiSurface(A2ATClient.class);
        Map<String, String> serverSurface = negotiationApiSurface(A2ATServer.class);

        assertEquals(NEGOTIATION_API_METHODS.stream().sorted().toList(), sortedKeys(clientSurface));
        assertEquals(clientSurface, serverSurface);
    }

    @Test
    void bothFacadesProduceIdenticalMetadataContentForTheSameInput() throws IOException {
        for (String language : List.of("zh-CN", "en-US")) {
            A2ATClient client = new A2ATClient(envFile(language, "client.env"));
            A2ATServer server = new A2ATServer(envFile(language, "server.env"));

            for (SymmetryCase symmetryCase : symmetryCases()) {
                MetadataContent clientResult = symmetryCase.generate(
                        client::generateNegotiationProposePromptFromData,
                        client::generateNegotiationAcceptPromptFromData,
                        client::generateNegotiationRejectPromptFromData);
                MetadataContent serverResult = symmetryCase.generate(
                        server::generateNegotiationProposePromptFromData,
                        server::generateNegotiationAcceptPromptFromData,
                        server::generateNegotiationRejectPromptFromData);

                assertNotNull(clientResult.promptText());
                assertEquals(clientResult, serverResult, "case " + symmetryCase.label() + " [" + language + "]");
                assertEquals(clientResult.buildMetadataContent(), serverResult.buildMetadataContent());
                assertEquals(symmetryCase.templateUri(), clientResult.templateUri());
                assertEquals(
                        symmetryCase.performative(),
                        clientResult.negotiationContext().performative(),
                        "stamped performative of case " + symmetryCase.label() + " [" + language + "]");
            }
        }
    }

    private Path envFile(String language, String fileName) throws IOException {
        Path envFile = tempDir.resolve(language + "-" + fileName);
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=%s
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=test-symmetry-mock
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(language));
        return envFile;
    }

    private static List<String> sortedKeys(Map<String, String> surface) {
        return surface.keySet().stream().sorted().collect(Collectors.toList());
    }

    private static Map<String, String> negotiationApiSurface(Class<?> facade) {
        Map<String, String> surface = new LinkedHashMap<>();
        for (Method method : facade.getMethods()) {
            if (NEGOTIATION_API_METHODS.contains(method.getName())) {
                String parameters = Arrays.stream(method.getParameterTypes())
                        .map(Class::getName)
                        .collect(Collectors.joining(","));
                surface.put(
                        method.getName(),
                        parameters + "->" + method.getReturnType().getName());
            }
        }
        return surface;
    }

    /**
     * Builds the nine golden fixture cases of the negotiation module: one fixed typed content, context and template URI
     * per negotiation type and performative, identical on the client and the server side.
     */
    private static List<SymmetryCase> symmetryCases() {
        return List.of(
                new SymmetryCase(
                        "information_propose",
                        NegotiationPerformative.PROPOSE,
                        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI,
                        new NegotiationProposeData(
                                new NegotiationContext(
                                        "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5, NegotiationPerformative.PROPOSE),
                                new InformationProposeContent(
                                        List.of(
                                                new NegotiationItem(
                                                        "ran-energy-saving area information", "e.g. Songshan Lake"),
                                                new NegotiationItem("VLANId", null)),
                                        "OR"))),
                new SymmetryCase(
                        "target_propose",
                        NegotiationPerformative.PROPOSE,
                        StandardTemplates.TARGET_NEGOTIATION_PROPOSE_URI,
                        new NegotiationProposeData(
                                new NegotiationContext(
                                        "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 1, 5, NegotiationPerformative.PROPOSE),
                                new TargetProposeContent(
                                        "Clarify the intent of the ran-energy-saving task.",
                                        List.of(new NegotiationItem("task intent", "ran-energy-saving optimization")),
                                        null,
                                        List.of(new NegotiationItem("area", "which site is covered")),
                                        null))),
                new SymmetryCase(
                        "feasibility_propose",
                        NegotiationPerformative.PROPOSE,
                        StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE_URI,
                        new NegotiationProposeData(
                                new NegotiationContext(
                                        "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5, NegotiationPerformative.PROPOSE),
                                new FeasibilityProposeContent(
                                        "Please assess the adjusted rate target.",
                                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                        List.of(new NegotiationItem("adjusted target", "rate lowered to 2Mbps")),
                                        null,
                                        null))),
                endingCase(
                        "information_accept",
                        NegotiationPerformative.ACCEPT,
                        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI,
                        new InformationEndingContent(
                                NegotiationConclusion.ACCEPT,
                                List.of(new NegotiationItem("area information", "Songshan Lake")))),
                endingCase(
                        "target_accept",
                        NegotiationPerformative.ACCEPT,
                        StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI,
                        new TargetEndingContent(NegotiationConclusion.ACCEPT, "The confirmed intent.", null)),
                endingCase(
                        "feasibility_accept",
                        NegotiationPerformative.ACCEPT,
                        StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT_URI,
                        new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, "The target is achievable.")),
                endingCase(
                        "information_reject",
                        NegotiationPerformative.REJECT,
                        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI,
                        new InformationEndingContent(
                                NegotiationConclusion.REJECT,
                                List.of(new NegotiationItem("area information", "not available")))),
                endingCase(
                        "target_reject",
                        NegotiationPerformative.REJECT,
                        StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI,
                        new TargetEndingContent(NegotiationConclusion.REJECT, null, "The intent is unclear.")),
                endingCase(
                        "feasibility_reject",
                        NegotiationPerformative.REJECT,
                        StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT_URI,
                        new FeasibilityEndingContent(NegotiationConclusion.REJECT, "The target is not achievable.")));
    }

    private static SymmetryCase endingCase(
            String label,
            NegotiationPerformative performative,
            String templateUri,
            NegotiationEndingContent content) {
        return new SymmetryCase(
                label,
                performative,
                templateUri,
                new NegotiationEndingData(
                        new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5, performative), content));
    }

    /** One symmetry case: a fixed input addressed to one of the three from-data generation methods. */
    private record SymmetryCase(
            String label, NegotiationPerformative performative, String templateUri, Object data) {

        MetadataContent generate(ProposeGenerator propose, EndingGenerator accept, EndingGenerator reject) {
            return switch (performative) {
                case PROPOSE -> propose.generate((NegotiationProposeData) data, templateUri);
                case ACCEPT -> accept.generate((NegotiationEndingData) data, templateUri);
                case REJECT -> reject.generate((NegotiationEndingData) data, templateUri);
                case ABORT -> throw new IllegalArgumentException("The symmetry cases carry no abort performative.");
            };
        }
    }

    @FunctionalInterface
    private interface ProposeGenerator {

        MetadataContent generate(NegotiationProposeData data, String templateUri);
    }

    @FunctionalInterface
    private interface EndingGenerator {

        MetadataContent generate(NegotiationEndingData data, String templateUri);
    }

    public static final class RecordingClient implements LLMClient {

        private final LLMClientConfig config;

        public RecordingClient(LLMClientConfig config) {
            this.config = config;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse("{}", config.model(), Map.of(), Map.of());
        }
    }
}
