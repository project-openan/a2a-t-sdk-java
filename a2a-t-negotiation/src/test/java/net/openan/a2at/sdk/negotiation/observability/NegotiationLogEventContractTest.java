package net.openan.a2at.sdk.negotiation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Locks the log instrumentation contract of the negotiation content layer.
 *
 * <p>Every pipeline path is driven through the real default collaborators with a scripted LLM client and the emitted
 * events are captured with a logback {@link ListAppender}. The test asserts that exactly the eleven contract event
 * names appear, each at its configured level, that every message is an English snake_case event followed by
 * {@code key=value} fields, that internal step diagnostics live in the logs only, and that neither the message text,
 * the free-text input nor the raw LLM response ever leaks into an INFO-or-higher event.
 */
class NegotiationLogEventContractTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final Map<String, Set<Level>> EXPECTED_EVENT_LEVELS = Map.ofEntries(
            Map.entry("negotiation_template_loaded", Set.of(Level.DEBUG)),
            Map.entry("negotiation_generator_dispatched", Set.of(Level.DEBUG)),
            Map.entry("negotiation_content_extraction_completed", Set.of(Level.INFO)),
            Map.entry("negotiation_content_extraction_response_invalid", Set.of(Level.WARN)),
            Map.entry("negotiation_semantic_validation_completed", Set.of(Level.INFO)),
            Map.entry("negotiation_generation_completed", Set.of(Level.INFO)),
            Map.entry("negotiation_llm_retry", Set.of(Level.WARN)),
            Map.entry("negotiation_llm_retry_exhausted", Set.of(Level.WARN)),
            Map.entry("negotiation_generation_failed", Set.of(Level.WARN)),
            Map.entry("negotiation_param_extraction_failed", Set.of(Level.WARN)),
            Map.entry("negotiation_rule_checks_completed", Set.of(Level.DEBUG, Level.WARN)));

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
    void allNegotiationEventNamesFireAtTheirConfiguredLevelsAcrossThePipelines() {
        driveSuccessfulGenerationFromData();
        driveSuccessfulGenerationFromText();
        driveRetryThenSuccessOnContentExtraction();
        driveRetryExhaustionOnContentExtraction();
        driveSuccessfulParamExtraction();
        driveSemanticRejection();
        driveRuleViolation();

        Map<String, Set<Level>> observedLevels = negotiationEventLevels();
        assertEquals(EXPECTED_EVENT_LEVELS.keySet(), observedLevels.keySet());
        EXPECTED_EVENT_LEVELS.forEach((eventName, expectedLevels) ->
                assertEquals(expectedLevels, observedLevels.get(eventName), "levels of event " + eventName));
    }

    @Test
    void everyEventMessageIsAnEnglishSnakeCaseEventWithKeyValueFields() {
        driveSuccessfulGenerationFromData();
        driveSuccessfulGenerationFromText();
        driveSuccessfulParamExtraction();

        List<ILoggingEvent> events = negotiationEvents();
        assertFalse(events.isEmpty());
        for (ILoggingEvent event : events) {
            String message = event.getFormattedMessage();
            String eventName = message.split(" ")[0];
            assertTrue(
                    eventName.matches("negotiation_[a-z0-9]+(_[a-z0-9]+)*"),
                    "event name must be snake_case but was: " + eventName);
            assertTrue(
                    message.chars().allMatch(character -> character >= 32 && character < 127),
                    "event message must be printable ASCII but was: " + message);
            List<String> keyTokens = keyTokens(message);
            assertFalse(keyTokens.isEmpty(), "event message must carry key=value fields: " + message);
            for (String key : keyTokens) {
                assertTrue(
                        key.matches("[a-z][a-z0-9_]*"),
                        "field key must be snake_case but was: " + key + " in " + message);
            }
        }
    }

    @Test
    void retryEventsCarryTheInternalStepNameThatNeverAppearsOnExceptions() {
        driveRetryExhaustionOnContentExtraction();

        List<String> retryMessages = messagesOf("negotiation_llm_retry");
        List<String> exhaustedMessages = messagesOf("negotiation_llm_retry_exhausted");
        assertEquals(1, retryMessages.size());
        assertEquals(1, exhaustedMessages.size());
        assertTrue(retryMessages.get(0).contains("step="));
        assertTrue(retryMessages.get(0).contains("attempt=1"));
        assertTrue(retryMessages.get(0).contains("max_attempts="));
        assertTrue(retryMessages.get(0).contains("code=" + ErrorCatalog.LLM_INVOCATION_FAILED.getCode()));
        assertTrue(exhaustedMessages.get(0).contains("step="));

        NegotiationGenerationException failure = generationFailureOfExhaustedExtraction();
        assertNotNull(failure);
        assertFalse(failure.getMessage().toLowerCase().contains("stage"));
        assertFalse(failure.getMessage().contains("step="));
    }

    @Test
    void infoAndHigherEventsNeverCarryMessageTextInputOrResponseContent() {
        String inputMarker = "SECRET-INPUT-MARKER-7f3a";
        String promptMarker = "SECRET-PROMPT-MARKER-9b1c";
        String responseMarker = "SECRET-RESPONSE-MARKER-5d2e";
        String paramValueMarker = "SECRET-PARAM-VALUE-MARKER-3c8d";

        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(
                        "{\"items\":[{\"name\":\"item\",\"value\":\"" + responseMarker + "\"}],\"relationship\":null}",
                        "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                                + "\"params\":{\"region\":\"" + paramValueMarker + "\"}}"))
                .build();

        MetadataContent generated = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem(promptMarker, "value")), null)),
                INFORMATION_PROPOSE_URI);
        assertTrue(generated.promptText().contains(promptMarker));
        orchestrator.generateProposeFromText(
                "free text containing " + inputMarker,
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                INFORMATION_PROPOSE_URI);
        FilledParamData filled = orchestrator.validateProposePromptAndDataFilling(
                generated.promptText(),
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                Map.of("type", "object"),
                INFORMATION_PROPOSE_URI);
        assertTrue(filled.data().containsValue(paramValueMarker));

        List<ILoggingEvent> infoAndHigher = appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.INFO))
                .toList();
        assertFalse(infoAndHigher.isEmpty());
        for (ILoggingEvent event : infoAndHigher) {
            String message = event.getFormattedMessage();
            assertFalse(message.contains(inputMarker), "input text leaked at " + event.getLevel() + ": " + message);
            assertFalse(message.contains(promptMarker), "prompt text leaked at " + event.getLevel() + ": " + message);
            assertFalse(
                    message.contains(responseMarker),
                    "response content leaked at " + event.getLevel() + ": " + message);
            assertFalse(
                    message.contains(paramValueMarker),
                    "extracted parameter value leaked at " + event.getLevel() + ": " + message);
            assertFalse(
                    message.contains("prompt_text=") || message.contains("text=") || message.contains("response="),
                    "raw content field must not be logged at " + event.getLevel() + ": " + message);
        }
    }

    @Test
    void removedTypeRecognitionEventNeverAppears() {
        driveSuccessfulGenerationFromText();
        driveSuccessfulParamExtraction();

        assertTrue(appender.list.stream()
                .noneMatch(event -> event.getFormattedMessage().startsWith("negotiation_type_recognition")));
    }

    private void driveSuccessfulGenerationFromData() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(validExtractionPayload(), validSemanticPayload()))
                .build();
        MetadataContent result = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);
        assertEquals(new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE), result.negotiationContext());
    }

    private void driveSuccessfulGenerationFromText() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(validExtractionPayload(), validSemanticPayload()))
                .build();
        MetadataContent result = orchestrator.generateProposeFromText(
                "请提供节能区域。",
                new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE),
                INFORMATION_PROPOSE_URI);
        assertTrue(result.promptText().contains("所需信息项"));
    }

    private void driveRetryThenSuccessOnContentExtraction() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient("", validExtractionPayload()))
                .maxAttempts(2)
                .build();
        MetadataContent result = orchestrator.generateProposeFromText(
                "请提供节能区域。",
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                INFORMATION_PROPOSE_URI);
        assertTrue(result.promptText().contains("所需信息项"));
    }

    private void driveRetryExhaustionOnContentExtraction() {
        NegotiationGenerationException failure = generationFailureOfExhaustedExtraction();
        assertNotNull(failure);
    }

    private NegotiationGenerationException generationFailureOfExhaustedExtraction() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new FailingClient())
                .maxAttempts(2)
                .build();
        try {
            orchestrator.generateProposeFromText(
                    "请提供节能区域。",
                    new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                    INFORMATION_PROPOSE_URI);
        } catch (NegotiationGenerationException failure) {
            assertEquals(ErrorCatalog.LLM_INVOCATION_FAILED.getCode(), failure.getCode());
            return failure;
        }
        return null;
    }

    private void driveSuccessfulParamExtraction() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(validSemanticPayload()))
                .build();
        MetadataContent message = orchestrator.generateProposeFromData(
                new NegotiationProposeData(
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null)),
                INFORMATION_PROPOSE_URI);
        FilledParamData filled = orchestrator.validateProposePromptAndDataFilling(
                message.promptText(),
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                Map.of("type", "object"),
                INFORMATION_PROPOSE_URI);
        assertEquals(UUID, filled.data().get("id"));
    }

    private void driveSemanticRejection() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(
                        "{\"semantic_verdict\":false,\"negotiation_type\":null,\"errors\":[{\"slot_name\":"
                                + "\"section.info_static\",\"code\":\"negotiation.type_mismatch\",\"facts\":"
                                + "{\"implied\":\"information\",\"declared\":\"information\"}}],\"params\":{}}"))
                .build();
        try {
            orchestrator.validateProposePromptAndDataFilling(
                    "## 所需信息项\n1. 区域\n",
                    new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                    Map.of("type", "object"),
                    INFORMATION_PROPOSE_URI);
        } catch (NegotiationParamExtractionException expected) {
            assertEquals(ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode(), expected.getCode());
        }
    }

    private void driveRuleViolation() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new ScriptedClient(validExtractionPayload(), validSemanticPayload()))
                .build();
        try {
            orchestrator.validateProposePromptAndDataFilling(
                    "## 所需信息项\n1. 区域\n",
                    new NegotiationContext(UUID, 9, 5, NegotiationPerformative.PROPOSE),
                    Map.of("type", "object"),
                    INFORMATION_PROPOSE_URI);
        } catch (NegotiationParamExtractionException expected) {
            assertEquals(ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode(), expected.getCode());
        }
    }

    private static String validExtractionPayload() {
        return "{\"items\":[{\"name\":\"节能区域\",\"value\":\"松山湖\"}],\"relationship\":null}";
    }

    private static String validSemanticPayload() {
        return "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                + "\"params\":{\"region\":\"松山湖\"}}";
    }

    private Map<String, Set<Level>> negotiationEventLevels() {
        Map<String, Set<Level>> levels = new LinkedHashMap<>();
        for (ILoggingEvent event : negotiationEvents()) {
            levels.computeIfAbsent(eventName(event), ignored -> new java.util.HashSet<>())
                    .add(event.getLevel());
        }
        return levels;
    }

    private List<ILoggingEvent> negotiationEvents() {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith("negotiation_"))
                .toList();
    }

    private List<String> messagesOf(String eventName) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(eventName + " ") || message.equals(eventName))
                .collect(Collectors.toList());
    }

    private static String eventName(ILoggingEvent event) {
        return event.getFormattedMessage().split(" ")[0];
    }

    private static List<String> keyTokens(String message) {
        return java.util.Arrays.stream(message.split(" "))
                .filter(token -> token.contains("="))
                .map(token -> token.substring(0, token.indexOf('=')))
                .collect(Collectors.toList());
    }

    private static final class ScriptedClient implements LLMClient {

        private final List<String> payloads;

        private int calls;

        private ScriptedClient(String... payloads) {
            this.payloads = List.of(payloads);
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            String payload = payloads.get(Math.min(calls, payloads.size() - 1));
            calls++;
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }

    private static final class FailingClient implements LLMClient {

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            throw new IllegalStateException("LLM endpoint unavailable.");
        }
    }
}
