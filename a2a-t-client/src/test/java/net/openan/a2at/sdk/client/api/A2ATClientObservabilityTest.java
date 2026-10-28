package net.openan.a2at.sdk.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.telemetry.TelemetryHook;
import net.openan.a2at.sdk.core.telemetry.TelemetryOperation;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A2ATClientObservabilityTest {

    private static final String MOCK_PROVIDER = "a2at-observability-mock";

    public static final class NoopLLM implements LLMClient {
        public NoopLLM(LLMClientConfig config) {}

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages, Map<String, Object> schema, Double temperature, Integer maxTokens) {
            return new LLMResponse("{}", "mock", Map.of(), Map.of());
        }
    }

    static final class RecordingHook implements TelemetryHook {
        final List<String> started = new ArrayList<>();
        final List<String> succeeded = new ArrayList<>();
        final List<String> rounds = new ArrayList<>();

        @Override
        public TelemetryOperation startSdkOperation(String role, String method) {
            String name = role + "." + method;
            started.add(name);
            return new TelemetryOperation() {
                @Override public void setAttribute(String key, Object value) {}

                @Override public void markSuccess() {
                    succeeded.add(name);
                }

                @Override public void markError(Throwable error) {
                    started.add("FAILED:" + name);
                }

                @Override public void close() {}
            };
        }

        @Override
        public void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds) {
            rounds.add(negotiationId + ":" + outcome + ":" + totalRounds);
        }
    }

    static final class ThrowingHook implements TelemetryHook {
        private final boolean throwOnStart;

        ThrowingHook(boolean throwOnStart) {
            this.throwOnStart = throwOnStart;
        }

        @Override
        public TelemetryOperation startSdkOperation(String role, String method) {
            if (throwOnStart) {
                throw new IllegalStateException("startSdkOperation failed");
            }
            return new TelemetryOperation() {
                @Override public void setAttribute(String key, Object value) {}

                @Override public void markSuccess() {}

                @Override public void markError(Throwable error) {
                    throw new IllegalStateException("markError failed");
                }

                @Override public void close() {}
            };
        }

        @Override
        public void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds) {}
    }

    @TempDir
    Path tempDir;

    private A2ATClient newClient(TelemetryHook hook) throws IOException {
        if (!LLMClientFactory.availableProviders().contains(MOCK_PROVIDER)) {
            LLMClientFactory.register(MOCK_PROVIDER, NoopLLM.class);
        }
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(envFile, """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                .formatted(MOCK_PROVIDER));
        return new A2ATClient(envFile, hook);
    }

    @Test
    void llmFreePathIsWrappedAsSdkOperation() throws IOException {
        RecordingHook hook = new RecordingHook();
        A2ATClient client = newClient(hook);
        client.getPrompts();
        assertTrue(hook.started.contains("client.getPrompts"));
        assertTrue(hook.succeeded.contains("client.getPrompts"));
    }

    @Test
    void runtimeFailureMarksErrorAndPropagates() throws IOException {
        RecordingHook hook = new RecordingHook();
        A2ATClient client = newClient(hook);
        assertThrows(NullPointerException.class, () -> client.generateTaskPromptFromText(null, null));
        assertTrue(hook.started.contains("client.generateTaskPromptFromText"));
        assertTrue(hook.started.contains("FAILED:client.generateTaskPromptFromText"));
    }

    @Test
    void throwingHookDoesNotBreakBusinessFlow() throws IOException {
        A2ATClient startFailure = newClient(new ThrowingHook(true));
        assertDoesNotThrow(startFailure::getPrompts);

        A2ATClient markErrorFailure = newClient(new ThrowingHook(false));
        NullPointerException error = assertThrows(
                NullPointerException.class, () -> markErrorFailure.generateTaskPromptFromText(null, null));
        assertEquals("text", error.getMessage());
    }

    @Test
    void legacyConstructorAndFacadeCallsComplete() throws IOException {
        if (!LLMClientFactory.availableProviders().contains(MOCK_PROVIDER)) {
            LLMClientFactory.register(MOCK_PROVIDER, NoopLLM.class);
        }
        Path envFile = tempDir.resolve("client-legacy.env");
        Files.writeString(envFile, """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                .formatted(MOCK_PROVIDER));
        A2ATClient client = new A2ATClient(envFile);
        assertDoesNotThrow(client::getPrompts);
        assertDoesNotThrow(client::getPrompts);
    }

    @Test
    void terminalOutcomeMapping() {
        assertEquals("accept", A2ATClient.terminalOutcome(NegotiationStatus.AGREED));
        assertEquals("reject", A2ATClient.terminalOutcome(NegotiationStatus.REJECTED));
        assertEquals(null, A2ATClient.terminalOutcome(NegotiationStatus.IN_PROGRESS));
    }
}
