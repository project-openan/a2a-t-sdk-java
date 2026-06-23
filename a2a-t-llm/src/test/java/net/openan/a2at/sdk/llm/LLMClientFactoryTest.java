package net.openan.a2at.sdk.llm;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.providers.OpenAIClient;
import org.junit.jupiter.api.Test;

class LLMClientFactoryTest {

    @Test
    void availableProvidersIncludesDefaultOpenAiProvider() {
        assertTrue(LLMClientFactory.availableProviders().contains("openai"));
    }

    @Test
    void createsDefaultOpenAiClient() {
        LLMClient client = LLMClientFactory.create("openai", buildConfig("openai"));

        assertInstanceOf(OpenAIClient.class, client);
    }

    @Test
    void registersAndCreatesCustomProviderClient() {
        LLMClientConfig config = buildConfig("customphasefour");

        LLMClientFactory.register("customphasefour", CustomClient.class);
        LLMClient client = LLMClientFactory.create("customphasefour", config);

        CustomClient customClient = assertInstanceOf(CustomClient.class, client);
        assertSame(config, customClient.config());
        assertTrue(LLMClientFactory.availableProviders().contains("customphasefour"));
    }

    @Test
    void rejectsDuplicateProviderRegistration() {
        LLMClientFactory.register("duplicatephasefour", CustomClient.class);

        LLMConfigError error = assertThrows(
                LLMConfigError.class, () -> LLMClientFactory.register("duplicatephasefour", CustomClient.class));

        assertTrue(error.getMessage().contains("duplicatephasefour"));
        assertTrue(error.getMessage().contains("already registered"));
    }

    @Test
    void rejectsInvalidProviderNames() {
        List<String> invalidProviders = List.of("", "  ", "Custom", "custom provider");

        for (String provider : invalidProviders) {
            LLMConfigError error =
                    assertThrows(LLMConfigError.class, () -> LLMClientFactory.register(provider, CustomClient.class));

            assertTrue(error.getMessage().contains("LLM provider"));
        }
    }

    @Test
    void rejectsUnknownProviderOnCreate() {
        LLMConfigError error = assertThrows(
                LLMConfigError.class,
                () -> LLMClientFactory.create("missingphasefour", buildConfig("missingphasefour")));

        assertTrue(error.getMessage().contains("missingphasefour"));
        assertTrue(error.getMessage().contains("openai"));
    }

    @Test
    void rejectsProviderClassWithoutPublicConfigConstructor() {
        LLMClientFactory.register("badconstructorphasefour", MissingConfigConstructorClient.class);

        LLMConfigError error = assertThrows(
                LLMConfigError.class,
                () -> LLMClientFactory.create("badconstructorphasefour", buildConfig("badconstructorphasefour")));

        assertTrue(error.getMessage().contains("badconstructorphasefour"));
        assertTrue(error.getMessage().contains("LLMClientConfig"));
    }

    private static LLMClientConfig buildConfig(String provider) {
        return new LLMClientConfig(provider, "test-model", "sk-test", null, 10, null, null, null, 300, 100);
    }

    public static final class CustomClient implements LLMClient {

        private final LLMClientConfig config;

        public CustomClient(LLMClientConfig config) {
            this.config = config;
        }

        LLMClientConfig config() {
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

    public static final class MissingConfigConstructorClient implements LLMClient {

        public MissingConfigConstructorClient() {}

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse("{}", "missing", Map.of(), Map.of());
        }
    }
}
