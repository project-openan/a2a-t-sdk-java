package net.openan.a2at.sdk.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A2ATClientNegotiationApiTest {

    private static final String TEST_MOCK_PROVIDER = "test-negotiation-mock";

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, RecordingClient.class);
        }
    }

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final String INFORMATION_PROPOSE_URI = INFORMATION_PROPOSE.uri();

    @Test
    void generatesInformationProposeFromDataWithBuiltinChineseTemplates() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("zh-CN"));

        MetadataContent result = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);

        assertEquals(INFORMATION_PROPOSE_URI, result.templateUri());
        assertFalse(result.promptText().isBlank());
        assertFalse(result.promptText().contains("协商上下文"), "the context section must not be rendered");
        Map<String, Object> metadata = result.buildMetadataContent();
        assertEquals(3, metadata.size());
        assertEquals(result.promptText(), metadata.get(result.extensionUri()));
        assertEquals(result.templateUri(), metadata.get(MetadataContent.TEMPLATE_URI_METADATA_KEY));
        assertEquals(new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE), result.negotiationContext());
    }

    @Test
    void generatesInformationProposeFromDataWithBuiltinEnglishTemplates() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("en-US"));

        MetadataContent result = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("Region", "Songshan Lake")), null)),
                INFORMATION_PROPOSE_URI);

        assertEquals(INFORMATION_PROPOSE_URI, result.templateUri());
        assertFalse(result.promptText().isBlank());
        assertFalse(result.promptText().contains("Negotiation Context"), "the context section must not be rendered");
        assertTrue(result.promptText().contains("Required Information Items"));
    }

    @Test
    void listsAllSevenNegotiationTemplatesPerLanguage() throws IOException {
        assertEquals(7, negotiationPrompts(new A2ATClient(writeEnv("zh-CN"))).size());
        assertEquals(7, negotiationPrompts(new A2ATClient(writeEnv("en-US"))).size());
    }

    @Test
    void generatesAbortPromptFromDataWithBuiltinChineseTemplates() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("zh-CN"));

        MetadataContent result = client.generateNegotiationAbortPromptFromData(
                new NegotiationAbortData(
                        new NegotiationContext(UUID, 5, 5, NegotiationPerformative.ABORT),
                        new NegotiationAbortContent("达到协商轮次上限，本次协商确认结束。")),
                StandardTemplates.NEGOTIATION_ABORT_URI);

        assertEquals(StandardTemplates.NEGOTIATION_ABORT.uri(), result.templateUri());
        assertTrue(result.promptText().contains("## 协商结果\nAbort"));
        assertTrue(result.promptText().contains("## 协商终止原因"));
        assertTrue(result.promptText().contains("达到协商轮次上限，本次协商确认结束。"));
        assertEquals(5, result.negotiationContext().round());
    }

    @Test
    void generatesAbortPromptFromDataWithBuiltinEnglishTemplates() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("en-US"));

        MetadataContent result = client.generateNegotiationAbortPromptFromData(
                new NegotiationAbortData(
                        new NegotiationContext(UUID, 3, 5, NegotiationPerformative.ABORT),
                        new NegotiationAbortContent(
                                "Reached the negotiation round limit. This negotiation is confirmed and ended.")),
                StandardTemplates.NEGOTIATION_ABORT_URI);

        assertEquals(StandardTemplates.NEGOTIATION_ABORT.uri(), result.templateUri());
        assertTrue(result.promptText().contains("## Negotiation Result\nAbort"));
        assertTrue(result.promptText().contains("## Negotiation Termination Reason"));
        assertTrue(result.promptText().contains("Reached the negotiation round limit."));
    }

    @Test
    void queriesTheCommonAbortTemplateWithoutThrowing() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("zh-CN"));

        PromptTemplate template =
                client.getPrompt(StandardTemplates.NEGOTIATION_ABORT_URI).orElseThrow();
        assertEquals(StandardTemplates.NEGOTIATION_ABORT, template.templateUri());
        assertFalse(template.content().isBlank());
    }

    @Test
    void queriesSingleNegotiationTemplateWithoutThrowing() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("zh-CN"));

        assertTrue(client.getPrompt(INFORMATION_PROPOSE_URI).isPresent());
        assertTrue(client.getPrompt(StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT_URI)
                .isPresent());
        assertFalse(client.getPrompt("Negotiation-T/information-negotiation/propose/v9").isPresent());
        PromptTemplate template = client.getPrompt(INFORMATION_PROPOSE_URI).orElseThrow();
        assertEquals(INFORMATION_PROPOSE, template.templateUri());
        assertFalse(template.content().isBlank());
    }

    @Test
    void generateNegotiationPromptFromDataThrowsOnMalformedTemplateUriString() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("zh-CN"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.generateNegotiationProposePromptFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                        "Task-T/only-one-segment"));
        assertTrue(ex.getMessage().contains("Unparseable template URI"), "message was: " + ex.getMessage());
    }

    @Test
    void validateNegotiationPromptThrowsOnBlankTemplateUriString() throws IOException {
        A2ATClient client = new A2ATClient(writeEnv("zh-CN"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.validateProposePromptAndDataFilling(
                        "rendered negotiation message",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        Map.of("type", "object"),
                        "  "));
        assertTrue(ex.getMessage().contains("Unparseable template URI"), "message was: " + ex.getMessage());
    }

    private static List<PromptTemplate> negotiationPrompts(A2ATClient client) {
        return client.getPrompts().stream()
                .filter(template -> StandardTemplates.NEGOTIATION_EXTENSION_NAME.equals(
                        template.templateUri().extensionName()))
                .toList();
    }

    private static Path writeEnv(String language) throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-client-negotiation-env");
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=%s
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=test-negotiation-mock
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(language));
        return envFile;
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
