package net.openan.a2at.sdk.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthorizationEntryPointMetadataTest {

    private static final String TEST_MOCK_PROVIDER = "test-authz-mock";

    @TempDir
    Path tempDir;

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, RecordingClient.class);
        }
    }

    @Test
    void should_generateAuthPromptFromText_ProducesMetadataContentWithAuthorizationExtensionUri()
            throws IOException {
        Path envFile = writeMinimalClasspathClientEnv(tempDir, TEST_MOCK_PROVIDER);
        A2ATClient client = new A2ATClient(envFile);

        MetadataContent result = client.generateAuthPromptFromText(
                "新增两条动网操作授权策略：业务投诉诊断/业务抢通/隧道调优/限期生效（2026-06-01~2030-06-18）；载波调度/业务抢通/载波调度/永久生效",
                StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT);

        assertNotNull(result);
        assertEquals(
                StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT.uri(),
                result.templateUri());
        assertNotNull(result.promptText());
        assertEquals(ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI, result.extensionUri());
    }

    @Test
    void should_generateAuthPromptFromDataWithSchema_ProducesMetadataContentWithAuthorizationExtensionUri()
            throws IOException {
        Path envFile = writeMinimalClasspathClientEnv(tempDir, TEST_MOCK_PROVIDER);
        A2ATClient client = new A2ATClient(envFile);
        Map<String, Object> data = Map.of("操作类型", "新增授权策略");
        Map<String, Object> schema = Map.of("操作类型", "授权策略操作类型，取值范围：新增/修改/删除/查询授权策略");

        MetadataContent result = client.generateAuthPromptFromDataWithSchema(
                data, schema, StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT);

        assertNotNull(result);
        assertEquals(
                StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT.uri(),
                result.templateUri());
        assertNotNull(result.promptText());
        assertEquals(ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI, result.extensionUri());
    }

    private static Path writeMinimalClasspathClientEnv(Path tempDir, String provider) throws IOException {
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(provider));
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
            return new LLMResponse(buildResponse(jsonSchema), config.model(), Map.of(), Map.of());
        }

        private static String buildResponse(Map<String, Object> jsonSchema) {
            Object slotNames = jsonSchema.get("slotNames");
            StringBuilder slots = new StringBuilder("{");
            if (slotNames instanceof List<?> names) {
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) {
                        slots.append(",");
                    }
                    slots.append("\"").append(names.get(i)).append("\":\"placeholder\"");
                }
            }
            slots.append("}");
            return "{\"slots\":" + slots + ",\"slot_errors\":[]}";
        }
    }
}