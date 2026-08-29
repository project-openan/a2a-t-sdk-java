package net.openan.a2at.sdk.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.server.A2ATServer;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InputTextLengthLimitTest {

    private static final String TEST_MOCK_PROVIDER = "test-input-limit-mock";

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, CountingClient.class);
        }
    }

    @BeforeEach
    void resetCounter() {
        CountingClient.resetCallCount();
    }

    @TempDir
    Path tempDir;

    @Test
    void checkTaskPromptFailsFastWithoutLlmCallWhenInputExceedsConfiguredLimit() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv("10"));
        String oversizedInput = "a".repeat(11);

        PromptComplianceResult result = server.checkTaskPrompt(oversizedInput);

        assertEquals(false, result.success());
        assertEquals(
                ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), result.failure().code());
        assertEquals("input_gate", result.failure().stage());
        assertEquals(0, CountingClient.callCount(), "No LLM call may happen for an oversized input");
    }

    @Test
    void checkTaskPromptDoesNotFailForLengthWhenInputIsExactlyAtDefaultLimit() throws IOException {
        A2ATServer server = new A2ATServer(writeEnv(null));
        String boundaryInput = "a".repeat(16384);

        PromptComplianceResult result = server.checkTaskPrompt(boundaryInput);

        assertNotEquals(
                ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(),
                result.success() ? null : result.failure().code(),
                "An input exactly at the default limit must not be rejected for its length");
    }

    @Test
    void validateContentPromptAndDataFillingFailsFastWithoutLlmCallWhenPromptExceedsConfiguredLimit()
            throws IOException {
        A2ATServer server = new A2ATServer(writeEnv("10"));
        String oversizedPrompt = "a".repeat(11);
        Map<String, Object> schema = Map.of();

        ContentValidationException taskError = assertThrows(
                ContentValidationException.class,
                () -> server.validateTaskPromptAndDataFilling(
                        oversizedPrompt, schema, StandardTemplates.ENERGY_SAVING_URI));
        ContentValidationException notificationError = assertThrows(
                ContentValidationException.class,
                () -> server.validateNotificationPromptAndDataFilling(
                        oversizedPrompt, schema, StandardTemplates.SUBSCRIBE_INCIDENT_URI));
        ContentValidationException authError = assertThrows(
                ContentValidationException.class,
                () -> server.validateAuthPromptAndDataFilling(
                        oversizedPrompt, schema, StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI));

        assertEquals(ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), taskError.getCode());
        assertEquals(ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), notificationError.getCode());
        assertEquals(ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), authError.getCode());
        assertEquals(0, CountingClient.callCount(), "No LLM call may happen for an oversized prompt");
    }

    private Path writeEnv(String maxTextChars) throws IOException {
        Path envFile = tempDir.resolve("server.env");
        String limitEntry = maxTextChars == null ? "" : "A2AT_INPUT_TEXT_MAX_CHARS=" + maxTextChars + "\n";
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=test-input-limit-mock
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        + limitEntry);
        return envFile;
    }

    public static final class CountingClient implements LLMClient {

        private static final AtomicInteger CALL_COUNT = new AtomicInteger(0);

        private final LLMClientConfig config;

        public CountingClient(LLMClientConfig config) {
            this.config = config;
        }

        static void resetCallCount() {
            CALL_COUNT.set(0);
        }

        static int callCount() {
            return CALL_COUNT.get();
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            CALL_COUNT.incrementAndGet();
            return new LLMResponse("{}", config.model(), Map.of(), Map.of());
        }
    }
}
