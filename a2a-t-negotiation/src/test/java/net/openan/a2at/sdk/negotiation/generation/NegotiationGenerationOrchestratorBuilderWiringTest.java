package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMError;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.validation.NegotiationComplianceChecker;
import net.openan.a2at.sdk.negotiation.validation.NegotiationRuleCheckResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Verifies the wiring defaults and injection seams of {@link NegotiationGenerationOrchestratorBuilder}.
 *
 * <p>The builder falls back to the module logger when no logger is injected, honors an injected compliance checker,
 * propagates the attempt limit to both retry chains (content extraction and semantic validation), and exposes no
 * negotiation-type recognizer injection point.
 */
class NegotiationGenerationOrchestratorBuilderWiringTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private Logger rootLogger;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
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
    void absentLoggerFallsBackToTheModuleLogger() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .build();

        MetadataContent result = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);

        assertFalse(result.promptText().isBlank());
        ILoggingEvent completed = appender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith("negotiation_generation_completed"))
                .findFirst()
                .orElseThrow();
        assertEquals(NegotiationGenerationOrchestrator.class.getName(), completed.getLoggerName());
        assertEquals(Level.INFO, completed.getLevel());
    }

    @Test
    void injectedLoggerReceivesThePipelineEvents() {
        Logger customLogger = (Logger) LoggerFactory.getLogger("negotiation.wiring.custom-logger");
        ListAppender<ILoggingEvent> customAppender = new ListAppender<>();
        customAppender.start();
        customLogger.addAppender(customAppender);
        try {
            NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                    .language("zh-CN")
                    .logger(customLogger)
                    .build();

            orchestrator.generateProposeFromData(
                    new NegotiationProposeData(
                            new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                            new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                    INFORMATION_PROPOSE_URI);

            ILoggingEvent completed = customAppender.list.stream()
                    .filter(event -> event.getFormattedMessage().startsWith("negotiation_generation_completed"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("negotiation.wiring.custom-logger", completed.getLoggerName());
        } finally {
            customLogger.detachAppender(customAppender);
            customAppender.stop();
        }
    }

    @Test
    void injectedComplianceCheckerIsUsedByTheValidationPipeline() {
        RecordingComplianceChecker checker = new RecordingComplianceChecker();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(
                        "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                                + "\"params\":{\"region\":\"松山湖\"}}"))
                .complianceChecker(checker)
                .build();

        orchestrator.validateProposePromptAndDataFilling(
                "## 所需信息项\n1. 区域\n",
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                Map.of("type", "object"),
                INFORMATION_PROPOSE_URI);

        assertTrue(checker.calls.get() >= 1, "the injected compliance checker must be used");
    }

    @Test
    void nullTemplateContentFromCustomLoaderIsReportedAsTemplateNotFoundNotNpe() {
        NegotiationTemplateLoader nullContentLoader =
                reference -> new PromptTemplate(reference.templateUri(), "", null, PromptTemplate.SOURCE_CLASSPATH);
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .templateLoader(nullContentLoader)
                .build();

        NegotiationParamExtractionException exception = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        "## 所需信息项\n1. 区域\n",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        Map.of("type", "object"),
                        INFORMATION_PROPOSE_URI));

        assertEquals(ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void maxAttemptsReachesBothRetryChains() {
        CountingFailingClient generationClient = new CountingFailingClient();
        NegotiationGenerationOrchestrator generationOrchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(generationClient)
                .maxAttempts(2)
                .build();
        NegotiationGenerationException generationFailure = org.junit.jupiter.api.Assertions.assertThrows(
                NegotiationGenerationException.class,
                () -> generationOrchestrator.generateProposeFromText(
                        "请提供节能区域。",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        INFORMATION_PROPOSE_URI));
        assertEquals(ErrorCatalog.LLM_INVOCATION_FAILED.getCode(), generationFailure.getCode());
        assertEquals(2, generationClient.calls.get(), "generation chain must retry up to the limit");

        CountingFailingClient semanticClient = new CountingFailingClient();
        NegotiationGenerationOrchestrator validationOrchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(semanticClient)
                .maxAttempts(2)
                .build();
        org.junit.jupiter.api.Assertions.assertThrows(
                net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException.class,
                () -> validationOrchestrator.validateProposePromptAndDataFilling(
                        "## 所需信息项\n1. 区域\n",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        Map.of("type", "object"),
                        INFORMATION_PROPOSE_URI));
        assertEquals(2, semanticClient.calls.get(), "semantic validation chain must retry up to the limit");
    }

    @Test
    void builderExposesNoTypeRecognizerInjectionPoint() {
        for (Method method : NegotiationGenerationOrchestratorBuilder.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertFalse(
                    name.contains("recogn"),
                    "the builder must not expose a type recognizer injection point but declares " + method.getName());
        }
    }

    private static final class RecordingComplianceChecker implements NegotiationComplianceChecker {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public NegotiationRuleCheckResult check(net.openan.a2at.sdk.core.model.NegotiationContext context) {
            calls.incrementAndGet();
            return new NegotiationRuleCheckResult(true, List.of());
        }
    }

    private static final class ScriptedClient implements LLMClient {

        private final String payload;

        private ScriptedClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }

    private static final class CountingFailingClient implements LLMClient {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls.incrementAndGet();
            throw new LLMError("LLM endpoint unavailable.");
        }
    }
}
