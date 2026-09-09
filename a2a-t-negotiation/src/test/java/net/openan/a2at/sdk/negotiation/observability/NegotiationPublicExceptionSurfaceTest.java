package net.openan.a2at.sdk.negotiation.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATParamExtractionError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMError;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProcessingException;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the public failure surface of the negotiation content layer never exposes internal pipeline stages.
 *
 * <p>Internal step names exist as diagnostics in the logs only: no public exception type carries a stage property, and
 * no failure message mentions the word stage. The tests inspect the exception types reflectively and drive the real
 * failure paths to lock the message behavior.
 */
class NegotiationPublicExceptionSurfaceTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final List<Class<?>> PUBLIC_EXCEPTION_TYPES = List.of(
            A2ATError.class,
            A2ATParamExtractionError.class,
            NegotiationProcessingException.class,
            NegotiationGenerationException.class,
            NegotiationParamExtractionException.class);

    @Test
    void noPublicExceptionTypeExposesAStageProperty() {
        for (Class<?> type : PUBLIC_EXCEPTION_TYPES) {
            for (Method method : type.getMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                assertFalse(
                        name.contains("stage"),
                        type.getSimpleName() + " must not expose a stage property but declares " + method.getName());
            }
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                assertFalse(
                        name.contains("stage"),
                        type.getSimpleName() + " must not carry a stage field but declares " + field.getName());
            }
        }
    }

    @Test
    void noFailureMessageOfTheRealPipelinesMentionsAStage() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new FailingClient())
                .maxAttempts(2)
                .build();

        List<String> messages = List.of(
                failureMessageOf(() -> orchestrator.generateProposeFromText(
                        "请提供节能区域。",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        INFORMATION_PROPOSE_URI)),
                failureMessageOf(() -> orchestrator.validateProposePromptAndDataFilling(
                        "## 所需信息项\n1. 区域\n",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        Map.of("type", "object"),
                        INFORMATION_PROPOSE_URI)),
                failureMessageOf(() -> new NegotiationContext(" ", 1, 5, NegotiationPerformative.PROPOSE)),
                failureMessageOf(() -> orchestrator.generateProposeFromText(
                        "text",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        StandardTemplates.ENERGY_SAVING)));

        for (String message : messages) {
            assertTrue(message != null && !message.isBlank(), "failure messages must not be blank");
            assertFalse(
                    message.toLowerCase(Locale.ROOT).contains("stage"),
                    "failure message must not mention a stage but was: " + message);
        }
    }

    @Test
    void publicFailuresStillCarryTheirStructuredDetails() {
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(new FailingClient())
                .maxAttempts(2)
                .build();

        NegotiationGenerationException generationFailure = catchFailure(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供节能区域。",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        INFORMATION_PROPOSE_URI));
        assertTrue(generationFailure.getCode().equals(ErrorCatalog.LLM_INVOCATION_FAILED.getCode()));

        NegotiationParamExtractionException extractionFailure = catchFailure(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        "## 所需信息项\n1. 区域\n",
                        new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                        Map.of("type", "object"),
                        INFORMATION_PROPOSE_URI));
        assertTrue(extractionFailure.getCode().equals(ErrorCatalog.LLM_INVOCATION_FAILED.getCode()));
        assertFalse(extractionFailure.getErrors().isEmpty());
        assertTrue(extractionFailure.getErrors().stream()
                .allMatch(error -> error.slotName() != null && !error.slotName().isBlank()));
    }

    private static String failureMessageOf(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            return failure.getMessage();
        }
        return null;
    }

    private static <T extends RuntimeException> T catchFailure(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            if (type.isInstance(failure)) {
                return type.cast(failure);
            }
            throw failure;
        }
        throw new AssertionError("the action was expected to fail with " + type.getSimpleName());
    }

    private static final class FailingClient implements LLMClient {

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            throw new LLMError("LLM endpoint unavailable.");
        }
    }
}
