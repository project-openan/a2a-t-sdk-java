package net.openan.a2at.sample.observability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.observability.A2ATObservabilityConfig;
import net.openan.a2at.sdk.observability.A2ATSpan;
import net.openan.a2at.sdk.observability.NegotiationMetrics;
import net.openan.a2at.sdk.observability.ObservabilityLLMClient;
import net.openan.a2at.sdk.observability.ObservabilitySetup;
import net.openan.a2at.sdk.observability.SdkOperationSpans;

/** Offline observability sample: Console exporters, scripted LLM, facade spans and negotiation metric. */
public final class ObservabilitySampleMain {

    private static final String SAMPLE_LLM_PROVIDER = "a2at-observability-sample";

    private static final String SAMPLE_ENV = """
            A2AT_LANGUAGE=zh-CN
            A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
            A2AT_LLM_PROVIDER=%s
            A2AT_LLM_MODEL=sample-model
            A2AT_LLM_BASE_URL=http://localhost:9/v1
            A2AT_LLM_API_KEY=offline-sample
            """;

    private ObservabilitySampleMain() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        run();
        awaitConsoleExport();
    }

    static void run() throws IOException {
        ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
        if (!LLMClientFactory.availableProviders().contains(SAMPLE_LLM_PROVIDER)) {
            LLMClientFactory.register(SAMPLE_LLM_PROVIDER, SampleLLM.class);
        }
        try (A2ATSpan root = new A2ATSpan("sample.workflow").open()) {
            LLMClient observed = new ObservabilityLLMClient(new SampleLLM());
            observed.structured(
                    List.of(Map.of("role", "user", "content", "hello")), Map.of(), null, null);

            Path envPath = Files.createTempFile("a2at-observability-sample", ".env");
            Files.writeString(envPath, SAMPLE_ENV.formatted(SAMPLE_LLM_PROVIDER));
            A2ATClient client = new A2ATClient(envPath, new SdkOperationSpans());
            client.getPrompts();
            try {
                client.getPrompt("Task-T/network-layer/private-line-complaint/v1");
            } catch (RuntimeException ignored) {
                // tolerated: the template URI may resolve to an empty Optional
            }

            try (A2ATSpan rounds = new A2ATSpan("sample.negotiation-rounds").open()) {
                NegotiationMetrics.reportRounds("sample-negotiation", "accept", 3);
            }
        }
    }

    private static void awaitConsoleExport() throws InterruptedException {
        Thread.sleep(Duration.ofSeconds(32).toMillis());
    }

    public static final class SampleLLM implements LLMClient {

        public SampleLLM() {}

        public SampleLLM(LLMClientConfig config) {}

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse(
                    "{}", "sample-model", Map.of("prompt_tokens", 128, "completion_tokens", 64), Map.of());
        }
    }
}
