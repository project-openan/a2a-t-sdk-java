package net.openan.a2at.sdk.server.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthorizationContentValidatorTest {

    private static final String TEST_MOCK_PROVIDER = "test-auth-mock";

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, RecordingClient.class);
        }
    }

    @TempDir
    Path tempDir;

    @Test
    void should_validateAuthPromptAndDataFilling_WithAuthorizationTemplateUri() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv());

        Map<String, Object> schemaMap =
                Map.of("properties", Map.of("授权策略的操作类型", Map.of("type", "string")), "required", List.of("授权策略的操作类型"));
        String prompt = "新增两条动网操作授权策略：业务投诉诊断/业务抢通/隧道调优/限期生效";

        FilledParamData result = assertDoesNotThrow(() -> server.validateAuthPromptAndDataFilling(
                prompt, schemaMap, StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT));

        assertNotNull(result);
        assertEquals(Map.of("授权策略的操作类型", "新增授权策略"), result.data());
    }

    private Path writeEnv() throws IOException {
        Path envFile = tempDir.resolve("server.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=test-auth-mock
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """);
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
            return new LLMResponse(
                    """
                    {
                      "semantic_verdict": true,
                      "errors": [],
                      "params": {"授权策略的操作类型": "新增授权策略"}
                    }
                    """,
                    config.model(),
                    Map.of(),
                    Map.of());
        }
    }
}
