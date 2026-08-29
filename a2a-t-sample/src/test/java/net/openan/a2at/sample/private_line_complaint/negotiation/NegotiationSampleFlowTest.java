package net.openan.a2at.sample.private_line_complaint.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationDecision;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleFlow;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationScenarioLoader;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.mock.NegotiationMockLlmInstaller;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NegotiationSampleFlowTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void installMockProvider() {
        NegotiationMockLlmInstaller.install();
    }

    @Test
    void acceptFlowCoversFourApis() throws IOException {
        NegotiationSampleFlow.NegotiationFlowResult result = run(NegotiationDecision.ACCEPT);

        assertEquals(NegotiationDecision.ACCEPT, result.decision());
        assertEquals(NegotiationSampleFlow.PROPOSE_TEMPLATE_URI, result.propose().templateUri());
        assertEquals(NegotiationSampleFlow.ENDING_TEMPLATE_URI, result.ending().templateUri());
        assertEquals(ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI, result.propose().extensionUri());
        assertContextIsShared(result);
        assertEquals(
                List.of(
                        Map.of(
                                "name", "接入端口名称",
                                "value", "P781-珠江新城-PTN7900-23-TPA1EG24-17(cvlan=100)"),
                        Map.of("name", "投诉分类", "value", "专线质差")),
                result.endingData().data().get("items"));
    }

    @Test
    void rejectFlowCoversFourApis() throws IOException {
        NegotiationSampleFlow.NegotiationFlowResult result = run(NegotiationDecision.REJECT);

        assertEquals(NegotiationDecision.REJECT, result.decision());
        assertContextIsShared(result);
        assertEquals(
                List.of(
                        Map.of("name", "接入端口名称", "reason", "当前账号没有资源系统查询权限"),
                        Map.of("name", "投诉分类", "reason", "当前账号没有资源系统查询权限")),
                result.endingData().data().get("items"));
    }

    private NegotiationSampleFlow.NegotiationFlowResult run(NegotiationDecision decision) throws IOException {
        Path env = envFile(decision.name().toLowerCase() + ".env");
        return NegotiationSampleFlow.run(
                new A2ATClient(env),
                new A2ATServer(env),
                NegotiationScenarioLoader.load(),
                decision);
    }

    private Path envFile(String fileName) throws IOException {
        Path envFile = tempDir.resolve(fileName);
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=mock-model
                A2AT_LLM_BASE_URL=http://localhost:0
                A2AT_LLM_API_KEY=mock-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(NegotiationMockLlmInstaller.PROVIDER));
        return envFile;
    }

    private static void assertContextIsShared(NegotiationSampleFlow.NegotiationFlowResult result) {
        Map<String, Object> proposeData = result.proposeData().data();
        Map<String, Object> endingData = result.endingData().data();
        assertEquals(result.requestContext().id(), proposeData.get("id"));
        assertEquals(result.requestContext().id(), endingData.get("id"));
        assertEquals(result.requestContext().round(), ((Number) proposeData.get("round")).intValue());
        assertEquals(result.requestContext().round(), ((Number) endingData.get("round")).intValue());
        assertEquals(result.requestContext().maxRounds(), ((Number) proposeData.get("maxRounds")).intValue());
        assertEquals(result.requestContext().maxRounds(), ((Number) endingData.get("maxRounds")).intValue());
        assertEquals(
                List.of(
                        Map.of("name", "接入端口名称", "requirement", "物理端口或逻辑端口名称"),
                        Map.of("name", "投诉分类", "requirement", "专线中断或专线质差")),
                proposeData.get("items"));
        assertNull(proposeData.get("relationship"));
        assertFalse(proposeData.containsKey("access_port_name"));
        assertFalse(endingData.containsKey("complaint_category"));
    }
}
