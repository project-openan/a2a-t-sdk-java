package net.openan.a2at.sdk.client.prompt.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultA2ATClientBuilderTest {

    private static final AtomicInteger CLIENT_INSTANCE_COUNT = new AtomicInteger(0);

    @BeforeEach
    void resetCounter() {
        CLIENT_INSTANCE_COUNT.set(0);
    }

    @Test
    void should_createLlmClientOnlyOnce_When_multipleBuildMethodsCalled() throws IOException {
        String provider = "test-lazy-cache";
        if (!LLMClientFactory.availableProviders().contains(provider)) {
            LLMClientFactory.register(provider, CountingClient.class);
        }

        Path envFile = createTempEnvFile(provider);
        A2ATConfig config = A2ATConfig.load(envFile);
        String resolvedRoot = envFile.getParent()
                .resolve(config.prompt().localRootDir())
                .toAbsolutePath()
                .toString();
        config = new A2ATConfig(
                new net.openan.a2at.sdk.core.model.PromptRuntimeConfig(
                        config.prompt().language(), config.prompt().sourceType(), resolvedRoot),
                config.llm(), config.inputLimits(), config.negotiation(), config.promptCompliance());

        DefaultA2ATClientBuilder builder =
                DefaultA2ATClientBuilder.builder().config(config).envPath(envFile);

        builder.buildPromptGenerationOrchestrator();
        builder.buildNegotiationGenerationOrchestrator();

        assertEquals(1, CLIENT_INSTANCE_COUNT.get());
    }

    @Test
    void buildPromptGenerationOrchestratorFallsBackToBuiltinScenarioCatalog() throws IOException {
        String provider = "test-scenario-failure";
        if (!LLMClientFactory.availableProviders().contains(provider)) {
            LLMClientFactory.register(provider, CountingClient.class);
        }

        Path envFile = createTempEnvFileWithoutScenarioCatalog(provider);
        A2ATConfig config = A2ATConfig.load(envFile);
        String resolvedRoot = envFile.getParent()
                .resolve(config.prompt().localRootDir())
                .toAbsolutePath()
                .toString();
        config = new A2ATConfig(
                new net.openan.a2at.sdk.core.model.PromptRuntimeConfig(
                        config.prompt().language(), config.prompt().sourceType(), resolvedRoot),
                config.llm(), config.inputLimits(), config.negotiation(), config.promptCompliance());

        DefaultA2ATClientBuilder builder =
                DefaultA2ATClientBuilder.builder().config(config).envPath(envFile);

        assertNotNull(
                builder.buildPromptGenerationOrchestrator(),
                "a missing local scenarios.json must fall back to the built-in scenario catalog instead of failing");
    }

    private static Path createTempEnvFileWithoutScenarioCatalog(String provider) throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-client-builder-scenario");
        Path promptRoot = tempDir.resolve("prompt_resources");
        Path scenarioPromptDir =
                promptRoot.resolve("prompts").resolve("scenario_recognition").resolve("zh-CN");
        Path slotPromptDir =
                promptRoot.resolve("prompts").resolve("slot_extraction").resolve("zh-CN");
        Path scenariosDir = promptRoot.resolve("scenarios").resolve("zh-CN");
        Files.createDirectories(scenarioPromptDir);
        Files.createDirectories(slotPromptDir);
        Files.createDirectories(scenariosDir);

        Files.writeString(scenarioPromptDir.resolve("system.md"), "You are a scenario recognition assistant.");
        Files.writeString(scenarioPromptDir.resolve("user.md"), "Identify the best matching scenario.");
        Files.writeString(slotPromptDir.resolve("system.md"), "You are a slot extraction assistant.");
        Files.writeString(slotPromptDir.resolve("user.md"), "Extract slots from the input.");

        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=local_file
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=prompt_resources
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(provider));
        return envFile;
    }

    private static Path createTempEnvFile(String provider) throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-client-builder-lazy");
        Path promptRoot = tempDir.resolve("prompt_resources");
        Path scenarioPromptDir =
                promptRoot.resolve("prompts").resolve("scenario_recognition").resolve("zh-CN");
        Path slotPromptDir =
                promptRoot.resolve("prompts").resolve("slot_extraction").resolve("zh-CN");
        Path scenariosDir = promptRoot.resolve("scenarios").resolve("zh-CN");
        Files.createDirectories(scenarioPromptDir);
        Files.createDirectories(slotPromptDir);
        Files.createDirectories(scenariosDir);

        Files.writeString(scenarioPromptDir.resolve("system.md"), "You are a scenario recognition assistant.");
        Files.writeString(scenarioPromptDir.resolve("user.md"), "Identify the best matching scenario.");
        Files.writeString(slotPromptDir.resolve("system.md"), "You are a slot extraction assistant.");
        Files.writeString(slotPromptDir.resolve("user.md"), "Extract slots from the input.");
        Files.writeString(
                scenariosDir.resolve("scenarios.json"),
                """
                {
                  "scenarios": [
                    {
                      "scenario_code": "ran-energy-saving",
                      "scenario_name": "Energy Saving",
                      "description": "Energy analysis",
                      "example": "Analyze site power"
                    }
                  ]
                }
                """);

        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=local_file
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=prompt_resources
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(provider));
        return envFile;
    }

    public static final class CountingClient implements LLMClient {

        private final LLMClientConfig config;

        public CountingClient(LLMClientConfig config) {
            this.config = config;
            CLIENT_INSTANCE_COUNT.incrementAndGet();
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
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
            return new LLMResponse("{\"slots\":" + slots + ",\"slot_errors\":[]}", config.model(), Map.of(), Map.of());
        }
    }
}
