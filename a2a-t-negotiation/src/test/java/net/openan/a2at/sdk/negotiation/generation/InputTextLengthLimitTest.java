package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGoldenCases.GoldenCase;
import org.junit.jupiter.api.Test;

/**
 * Tests of the free-text input length limit of the negotiation pipelines.
 *
 * <p>Oversized inputs fail fast with the code {@code input.text_too_long} before any LLM call, inputs at exactly the
 * limit pass the gate and reach the LLM, and the limit is configurable through the builder.
 */
class InputTextLengthLimitTest {

    private static final String ZH_CN = NegotiationGoldenCases.ZH_CN;

    private static final GoldenCase PROPOSE = GoldenCase.INFORMATION_PROPOSE;

    /** A text one character over the default limit must be rejected with the dedicated code before any LLM call. */
    @Test
    void rejectsFromTextOverTheDefaultLimitBeforeAnyLlmCall() {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = defaultOrchestrator(llm);

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        textOfLength(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS + 1),
                        PROPOSE.context(),
                        PROPOSE.template()));

        assertEquals(ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), failure.getCode());
        assertTrue(
                failure.getMessage().contains(String.valueOf(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS + 1)),
                "the violation message must state the actual length but was: " + failure.getMessage());
        assertTrue(
                failure.getMessage().contains(String.valueOf(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS)),
                "the violation message must state the limit but was: " + failure.getMessage());
        assertEquals(0, llm.calls, "an oversized text must never reach the LLM");
    }

    /** A text at exactly the default limit passes the gate and reaches the extraction LLM call. */
    @Test
    void acceptsFromTextAtExactlyTheDefaultLimit() {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = defaultOrchestrator(llm);

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        textOfLength(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS), PROPOSE.context(), PROPOSE.template()));

        assertNotEquals(ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), failure.getCode());
        assertTrue(llm.calls >= 1, "a text at exactly the limit must reach the LLM");
    }

    /** An oversized prompt of the validation pipeline is rejected with the dedicated code before any LLM call. */
    @Test
    void rejectsValidationPromptOverTheDefaultLimitBeforeAnyLlmCall() {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = defaultOrchestrator(llm);

        NegotiationParamExtractionException failure = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        textOfLength(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS + 1),
                        PROPOSE.context(),
                        Map.of("type", "object"),
                        PROPOSE.template()));

        assertEquals(ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), failure.getCode());
        assertEquals(0, llm.calls, "an oversized prompt must never reach the LLM");
    }

    /**
     * A validation prompt at exactly the default limit passes the gate and reaches the semantic validation LLM call.
     */
    @Test
    void acceptsValidationPromptAtExactlyTheDefaultLimit() {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = defaultOrchestrator(llm);

        NegotiationParamExtractionException failure = assertThrows(
                NegotiationParamExtractionException.class,
                () -> orchestrator.validateProposePromptAndDataFilling(
                        textOfLength(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS),
                        PROPOSE.context(),
                        Map.of("type", "object"),
                        PROPOSE.template()));

        assertNotEquals(ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), failure.getCode());
        assertTrue(llm.calls >= 1, "a prompt at exactly the limit must reach the LLM");
    }

    /** A configured limit below the default drives the rejection: a text one character over it fails fast. */
    @Test
    void customLimitDrivesTheRejection() {
        CountingClient llm = new CountingClient();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language(ZH_CN)
                .llmClient(llm)
                .maxTextChars(10)
                .build();

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText("01234567890", PROPOSE.context(), PROPOSE.template()));

        assertEquals(ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), failure.getCode());
        assertEquals(0, llm.calls);
    }

    /** The builder rejects a non-positive limit as a programming error. */
    @Test
    void builderRejectsNonPositiveLimit() {
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> NegotiationGenerationOrchestratorBuilder.builder()
                        .language(ZH_CN)
                        .maxTextChars(0)
                        .build());

        assertTrue(
                failure.getMessage().contains("max text chars"),
                "the failure must point at the limit but was: " + failure.getMessage());
    }

    private static NegotiationGenerationOrchestrator defaultOrchestrator(LLMClient llmClient) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(ZH_CN)
                .llmClient(llmClient)
                .build();
    }

    private static String textOfLength(int length) {
        return "x".repeat(length);
    }

    /**
     * LLM client stub recording every call and returning an empty payload, so the tests can assert both that a call
     * happened and that the pipeline fails downstream of the length gate instead of inside it.
     */
    private static final class CountingClient implements LLMClient {

        private int calls;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls++;
            return new LLMResponse("", "counting-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }
}
