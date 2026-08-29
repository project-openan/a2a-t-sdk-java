package net.openan.a2at.sdk.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the env-configured negotiation behavior of the client facade end to end.
 *
 * <p>The negotiation-relevant env keys must become observable in the facade behavior: the language selects the Chinese
 * templates, and the LLM attempt limit bounds the retry loop of the from-text generation. The negotiation templates are
 * classpath-fixed, so a configured local resource root is ignored in {@code classpath} mode: the built-in template wins
 * over any local override. A second test proves the zero-configuration defaults (English templates, three attempts,
 * built-in resources) work out of the box.
 */
class A2ATClientNegotiationEnvConfigTest {

    private static final String TEST_MOCK_PROVIDER = "test-negotiation-failing-mock";

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, FailingClient.class);
        }
    }

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final String INFORMATION_PROPOSE_URI = INFORMATION_PROPOSE.uri();

    private static final String CUSTOM_TEMPLATE_MARKER = "CUSTOM-TEMPLATE-MARKER-7d31";

    @TempDir
    Path tempDir;

    private Logger rootLogger;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        rootLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void languageAndMaxAttemptsAreObservableAndClasspathIgnoresLocalRoot() throws IOException {
        Path customRoot = writeCustomRootWithMarkeredTemplate();
        Path envFile = writeEnvFile(
                "A2AT_LANGUAGE=zh-CN",
                "A2AT_PROMPT_SOURCE_TYPE=classpath",
                "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=" + customRoot.toString().replace('\\', '/'),
                "A2AT_LLM_PROVIDER=" + TEST_MOCK_PROVIDER,
                "A2AT_LLM_MODEL=example-model",
                "A2AT_LLM_BASE_URL=https://llm.example.test/v1",
                "A2AT_LLM_API_KEY=test-key",
                "A2AT_LLM_MAX_ATTEMPTS=2",
                "A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory");
        A2ATClient client = new A2ATClient(envFile);

        MetadataContent result = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);

        assertTrue(result.promptText().contains("所需信息项"), "the zh-CN language must select the Chinese templates");
        assertFalse(
                result.promptText().contains(CUSTOM_TEMPLATE_MARKER),
                "the configured local resource root must be ignored in classpath mode and the built-in template used");

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> client.generateNegotiationProposePromptFromText(
                        "请提供节能区域。",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        INFORMATION_PROPOSE_URI));
        assertEquals(A2ATErrorCodes.NEGOTIATION_LLM_INFRASTRUCTURE_ERROR, failure.getCode());
        assertEquals(
                1,
                retryEventCount(),
                "A2AT_LLM_MAX_ATTEMPTS=2 must allow exactly one retry before surfacing the failure");
        assertEquals(1, exhaustedEventCount(), "the retry loop must report exhaustion exactly once");
    }

    @Test
    void zeroConfigDefaultsWorkOutOfTheBox() throws IOException {
        Path envFile = writeEnvFile(
                "A2AT_LLM_PROVIDER=" + TEST_MOCK_PROVIDER,
                "A2AT_LLM_MODEL=example-model",
                "A2AT_LLM_BASE_URL=https://llm.example.test/v1",
                "A2AT_LLM_API_KEY=test-key");
        A2ATClient client = new A2ATClient(envFile);

        MetadataContent result = client.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("Region", "Songshan Lake")), null)),
                INFORMATION_PROPOSE_URI);

        assertTrue(result.promptText().contains("## Information Negotiation"), "the default language must be en-US");
        assertTrue(result.promptText().contains("Required Information Items"));
        assertEquals(7, negotiationPrompts(client).size(), "the built-in resources must be used by default");
        assertTrue(client.getPrompt(INFORMATION_PROPOSE_URI).isPresent());

        assertThrows(
                NegotiationGenerationException.class,
                () -> client.generateNegotiationProposePromptFromText(
                        "Provide the ran-energy-saving region.",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        INFORMATION_PROPOSE_URI));
        assertEquals(
                2,
                retryEventCount(),
                "the default attempt limit of 3 must allow exactly two retries before surfacing the failure");
        assertEquals(1, exhaustedEventCount());
    }

    private Path writeEnvFile(String... lines) throws IOException {
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(envFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return envFile;
    }

    private static List<net.openan.a2at.sdk.core.model.PromptTemplate> negotiationPrompts(A2ATClient client) {
        return client.getPrompts().stream()
                .filter(template -> StandardTemplates.NEGOTIATION_EXTENSION_NAME.equals(
                        template.templateUri().extensionName()))
                .toList();
    }

    private Path writeCustomRootWithMarkeredTemplate() throws IOException {
        Path customTemplate = tempDir.resolve("custom-root")
                .resolve("templates")
                .resolve("Negotiation-T")
                .resolve("information-negotiation")
                .resolve("propose")
                .resolve("v1")
                .resolve("zh-CN")
                .resolve("template.md");
        Files.createDirectories(customTemplate.getParent());
        Files.writeString(
                customTemplate,
                builtinTemplate(INFORMATION_PROPOSE_URI, "zh-CN") + "\n\n## 自定义标记\n" + CUSTOM_TEMPLATE_MARKER + "\n",
                StandardCharsets.UTF_8);
        return tempDir.resolve("custom-root");
    }

    private static String builtinTemplate(String templateUri, String language) throws IOException {
        String[] segments = templateUri.split("/");
        String typeSegment = segments[1];
        String phaseSegment = segments[2];
        String classpathPath = "prompt_resources/templates/Negotiation-T/" + typeSegment + "/" + phaseSegment + "/"
                + TemplateUri.DEFAULT_TEMPLATE_VERSION + "/" + language + "/template.md";
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathPath);
        assertFalse(stream == null, "the built-in template must exist on the classpath: " + classpathPath);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private long retryEventCount() {
        return countEvents("negotiation_llm_retry");
    }

    private long exhaustedEventCount() {
        return countEvents("negotiation_llm_retry_exhausted");
    }

    private long countEvents(String eventName) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(eventName + " "))
                .count();
    }

    public static final class FailingClient implements LLMClient {

        private final LLMClientConfig config;

        public FailingClient(LLMClientConfig config) {
            this.config = config;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            throw new LLMRuntimeError("test mock failure");
        }
    }
}
