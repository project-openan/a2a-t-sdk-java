package net.openan.a2at.sdk.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import net.openan.a2at.sdk.server.A2ATServer;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Task-T and Negotiation-T coexistence regression: one server instance answers both the Task-T compliance API and the
 * negotiation content-layer APIs without interference, and the pre-existing negotiation state machine keeps working
 * next to the new APIs.
 */
class TaskPromptNegotiationCoexistenceTest {

    private static final String TEST_MOCK_PROVIDER = "test-coexistence-mock";

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, RecordingClient.class);
        }
    }

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI;

    private static final String TASK_T_MESSAGE = "## 任务类型(Task Type)\n"
            + "无线能效优化\n\n"
            + "## 任务对象(Task Object)\n"
            + "松山湖管委会\n\n"
            + "## 任务目标(Task Target)\n"
            + "总功耗降低30%\n";

    @TempDir
    Path tempDir;

    @Test
    void checkTaskPromptAndValidateAndFillingCoexistOnOneServerInstance() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv());

        PromptComplianceResult complianceResult = server.checkTaskPrompt(TASK_T_MESSAGE);

        assertFalse(complianceResult.success());
        assertEquals("scenario.not_matched", complianceResult.failure().code());
        assertEquals("prompt_parse", complianceResult.failure().stage());

        NegotiationParamExtractionException negotiationFailure = assertThrows(
                NegotiationParamExtractionException.class,
                () -> server.validateProposePromptAndDataFilling(
                        TASK_T_MESSAGE,
                        null,
                        Map.of("type", "object", "properties", Map.of("region", Map.of("type", "string"))),
                        INFORMATION_PROPOSE_URI));

        assertEquals("negotiation.invalid_input", negotiationFailure.getCode());
        assertEquals("输入的协商内容无效:缺少协商上下文(该报文不是协商报文)", negotiationFailure.getMessage());

        MetadataContent negotiationMessage = server.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);
        assertEquals(INFORMATION_PROPOSE_URI, negotiationMessage.templateUri());
        assertEquals(UUID, negotiationMessage.negotiationContext().id());
    }

    @Test
    void oldNegotiationStateMachineKeepsWorkingNextToTheContentLayerApis() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv());

        Map<String, Object> started = server.startNegotiation(
                NegotiationType.INFORMATION, "需要完整的任务提示词。", Map.of("source", "coexistence-test"));
        Map<?, ?> context = (Map<?, ?>) started.get(NegotiationHandler.NEGOTIATION_T_URI_NL);
        assertEquals("information", context.get("negotiationType"));
        assertEquals("in-progress", context.get("status"));
        assertEquals("coexistence-test", ((Map<?, ?>) started.get("facts")).get("source"));

        MetadataContent negotiationMessage = server.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);
        assertEquals(2, negotiationMessage.negotiationContext().round());

        Map<String, Object> startedAgain = server.startNegotiation(NegotiationType.TARGET, "请澄清目标。", Map.of());
        assertEquals(
                "target",
                ((Map<?, ?>) startedAgain.get(NegotiationHandler.NEGOTIATION_T_URI_NL)).get("negotiationType"));
    }

    private Path writeEnv() throws IOException {
        Path envFile = tempDir.resolve("server.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=test-coexistence-mock
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted());
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
