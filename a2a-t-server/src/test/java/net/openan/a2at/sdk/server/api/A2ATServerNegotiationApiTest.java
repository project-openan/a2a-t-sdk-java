package net.openan.a2at.sdk.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.Test;

class A2ATServerNegotiationApiTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final String INFORMATION_PROPOSE_URI = INFORMATION_PROPOSE.uri();

    @Test
    void generatesInformationProposeFromDataWithBuiltinChineseTemplates() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv("zh-CN"));

        MetadataContent result = server.generateNegotiationProposePromptFromData(
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
        A2ATServer server = new A2ATServer(writeEnv("en-US"));

        MetadataContent result = server.generateNegotiationProposePromptFromData(
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
        assertEquals(7, negotiationPrompts(new A2ATServer(writeEnv("zh-CN"))).size());
        assertEquals(7, negotiationPrompts(new A2ATServer(writeEnv("en-US"))).size());
    }

    @Test
    void generatesAbortPromptFromDataWithBuiltinChineseTemplates() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv("zh-CN"));

        MetadataContent result = server.generateNegotiationAbortPromptFromData(
                new NegotiationAbortData(
                        new NegotiationContext(UUID, 5, 5, NegotiationPerformative.ABORT),
                        new NegotiationAbortContent("达到协商轮次上限，本次协商确认结束。")),
                StandardTemplates.NEGOTIATION_ABORT_URI);

        assertEquals(StandardTemplates.NEGOTIATION_ABORT.uri(), result.templateUri());
        assertTrue(result.promptText().contains("## 协商结果\nAbort"));
        assertTrue(result.promptText().contains("## 协商终止原因"));
        assertTrue(result.promptText().contains("达到协商轮次上限，本次协商确认结束。"));
    }

    @Test
    void queriesTheCommonAbortTemplateWithoutThrowing() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv("zh-CN"));

        PromptTemplate template =
                server.getPrompt(StandardTemplates.NEGOTIATION_ABORT_URI).orElseThrow();
        assertEquals(StandardTemplates.NEGOTIATION_ABORT, template.templateUri());
        assertFalse(template.content().isBlank());
    }

    @Test
    void queriesSingleNegotiationTemplateWithoutThrowing() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv("zh-CN"));

        assertTrue(server.getPrompt(INFORMATION_PROPOSE_URI).isPresent());
        assertTrue(server.getPrompt(StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT_URI)
                .isPresent());
        assertFalse(server.getPrompt(
                        StandardTemplates.NEGOTIATION_EXTENSION_NAME + "/unknown-negotiation/propose/v1")
                .isPresent());
        PromptTemplate template = server.getPrompt(INFORMATION_PROPOSE_URI).orElseThrow();
        assertEquals(INFORMATION_PROPOSE, template.templateUri());
        assertFalse(template.content().isBlank());
    }

    private static List<PromptTemplate> negotiationPrompts(A2ATServer server) {
        return server.getPrompts().stream()
                .filter(template -> StandardTemplates.NEGOTIATION_EXTENSION_NAME.equals(
                        template.templateUri().extensionName()))
                .toList();
    }

    private static Path writeEnv(String language) throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-server-negotiation-env");
        Path envFile = tempDir.resolve("server.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=%s
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=openai
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                A2AT_PROMPT_COMPLIANCE_ENABLED=false
                """
                        .formatted(language));
        return envFile;
    }
}
