package net.openan.a2at.sdk.server.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.core.model.LlmConfig;
import net.openan.a2at.sdk.core.model.NegotiationConfig;
import net.openan.a2at.sdk.core.model.PromptComplianceConfig;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.server.compliance.DefaultServerPromptComplianceOrchestrator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultA2ATServerBuilderTest {

    private static final String TEST_MOCK_PROVIDER = "test-builder-mock";

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, CountingRecorderClient.class);
        }
    }

    @BeforeEach
    void resetCounter() {
        CountingRecorderClient.reset();
    }

    @TempDir
    Path tempDir;

    @Test
    void llmClientCreatedOnlyOnceAcrossMultipleBuildMethods() throws IOException {
        Path envFile = writeEnv();
        A2ATConfig config = A2ATConfig.load(envFile);
        DefaultA2ATServerBuilder builder =
                DefaultA2ATServerBuilder.builder().config(config).envPath(envFile);

        builder.buildPromptComplianceOrchestrator();
        builder.buildNegotiationGenerationOrchestrator();
        builder.buildTaskContentValidator();
        builder.buildNotificationContentValidator();
        builder.buildAuthContentValidator();
        builder.buildNegotiationOrchestrator();

        assertEquals(1, CountingRecorderClient.instanceCount());
    }

    @Test
    void buildNegotiationOrchestratorReusesCachedComplianceOrchestrator() throws IOException {
        Path envFile = writeEnv();
        A2ATConfig config = A2ATConfig.load(envFile);
        DefaultA2ATServerBuilder builder =
                DefaultA2ATServerBuilder.builder().config(config).envPath(envFile);

        DefaultServerPromptComplianceOrchestrator first = builder.buildPromptComplianceOrchestrator();
        DefaultServerPromptComplianceOrchestrator second = builder.buildPromptComplianceOrchestrator();
        assertSame(first, second, "Compliance orchestrator should be lazily cached");

        builder.buildNegotiationOrchestrator();

        assertEquals(
                1,
                CountingRecorderClient.instanceCount(),
                "LLM client should still be created only once after negotiation orchestrator build");
    }

    @Test
    void llmClientConfigDerivedFromConfigLlmNotFromEnvPath() throws IOException {
        Path envFile = writeEnvWithDifferentModel();
        A2ATConfig config = new A2ATConfig(
                new PromptRuntimeConfig("zh-CN", "classpath", "."),
                new LlmConfig(
                        TEST_MOCK_PROVIDER,
                        "from-config-model",
                        "from-config-key",
                        "https://from-config.example.com",
                        10,
                        null,
                        null,
                        null,
                        300,
                        100,
                        false,
                        true,
                        false,
                        null,
                        3,
                        List.of()),
                new InputLimitConfig(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS),
                new NegotiationConfig("in_memory"),
                new PromptComplianceConfig(false));

        DefaultA2ATServerBuilder builder =
                DefaultA2ATServerBuilder.builder().config(config).envPath(envFile);

        builder.buildPromptComplianceOrchestrator();

        assertEquals(1, CountingRecorderClient.instanceCount());
        LLMClientConfig recorded = CountingRecorderClient.lastInstance().recordedConfig();
        assertEquals("from-config-model", recorded.model(), "Model should come from config.llm(), not from envPath");
        assertEquals("from-config-key", recorded.apiKey(), "API key should come from config.llm(), not from envPath");
        assertEquals(
                LLMClientConfig.from(config.llm()),
                recorded,
                "Recorded config should equal LLMClientConfig.from(config.llm())");
    }

    private Path writeEnv() throws IOException {
        Path envFile = tempDir.resolve("server.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=test-builder-mock
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """);
        return envFile;
    }

    private Path writeEnvWithDifferentModel() throws IOException {
        Path envFile = tempDir.resolve("alt.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=test-builder-mock
                A2AT_LLM_MODEL=from-env-model
                A2AT_LLM_API_KEY=from-env-key
                A2AT_LLM_BASE_URL=https://from-env.example.com
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """);
        return envFile;
    }

    public static final class CountingRecorderClient implements LLMClient {

        private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(0);

        private static volatile CountingRecorderClient lastInstance;

        private final LLMClientConfig config;

        public CountingRecorderClient(LLMClientConfig config) {
            this.config = config;
            lastInstance = this;
            INSTANCE_COUNT.incrementAndGet();
        }

        static void reset() {
            INSTANCE_COUNT.set(0);
            lastInstance = null;
        }

        static int instanceCount() {
            return INSTANCE_COUNT.get();
        }

        static CountingRecorderClient lastInstance() {
            return lastInstance;
        }

        LLMClientConfig recordedConfig() {
            return config;
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
