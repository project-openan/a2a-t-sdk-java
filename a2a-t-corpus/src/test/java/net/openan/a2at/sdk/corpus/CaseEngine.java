package net.openan.a2at.sdk.corpus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATParamExtractionError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.validation.NegotiationSemanticValidator;
import org.jspecify.annotations.Nullable;

/**
 * Executes one expanded corpus case against the production negotiation content wiring and asserts its expectation
 * block.
 *
 * <p>Everything except the LLM client is production assembly: the case engine builds a real
 * {@link NegotiationGenerationOrchestrator} through {@link NegotiationGenerationOrchestratorBuilder} for the language
 * of the case, wires the {@code inject} hooks onto their real builder injection points ({@code failingTemplateLoader}
 * for the generation template-not-found matrix, {@code failingSemanticValidator} for the validate-family
 * prompt-resource-not-found mapping) and dispatches on the {@link NegotiationApi} enum at compile time, so a misspelled
 * API name fails at corpus load time and a renamed service method fails this compilation. The three task-family APIs
 * run through the {@link TaskApiAssembler}, the real facade builders' assembly of the closed loop (Q21) with the
 * scripted LLM client injected at the builders' LLM seam.
 *
 * <p>Result normalization: a success run returns the produced {@link MetadataContent} or {@link FilledParamData}, a
 * failure run captures the thrown exception. The expectation comparison covers outcome, exception name, error code,
 * message fragments, slot errors (slot plus code pairs, exact but order-insensitive), the exact LLM call count, the
 * golden fixture text (per language, byte equality after CRLF normalization), the metadata echoes, the expected merged
 * parameter map, the P0 behavior contracts and the Q17 C+ differential double run (fromText == fromData == golden,
 * from-data side proven zero-call with an assertion-only client). Every mismatch fails with the case id, the JSON path
 * of the expectation and the expected-versus-actual pair.
 *
 * @since 2026-08
 */
public final class CaseEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INJECT_FAILING_TEMPLATE_LOADER = "failingTemplateLoader";

    private static final String INJECT_FAILING_SEMANTIC_VALIDATOR = "failingSemanticValidator";

    private static final int MAX_REPORTED_TEXT_LENGTH = 400;

    /**
     * Runs one case without a prompt override.
     *
     * @param testCase expanded corpus case
     * @return the normalized outcome of the run
     * @throws AssertionError when any expectation mismatches
     */
    public CaseOutcome run(NegotiationCase testCase) {
        return run(testCase, null);
    }

    /**
     * Runs one case, optionally overriding the prompt input of the validate family.
     *
     * <p>The {@link ScenarioEngine} resolves {@code prompt.fromStep} references into prompt texts and hands them in
     * through this parameter; a standalone validate case resolves its golden or inline prompt itself.
     *
     * @param testCase expanded corpus case
     * @param promptOverride prompt text resolved from an earlier scenario step, or null
     * @return the normalized outcome of the run
     * @throws AssertionError when any expectation mismatches
     */
    public CaseOutcome run(NegotiationCase testCase, @Nullable String promptOverride) {
        return run(testCase, promptOverride, false);
    }

    /**
     * Runs one case, optionally overriding the prompt input of the validate family and optionally running as a scenario
     * step.
     *
     * <p>The {@link ScenarioEngine} resolves {@code prompt.fromStep} references into prompt texts and hands them in
     * through the prompt parameter; a standalone validate case resolves its golden or inline prompt itself. Scenario
     * mode defers the {@code expect.paramsFromStep} check to the scenario engine, which resolves the cross-step
     * reference after the step ran.
     *
     * @param testCase expanded corpus case
     * @param promptOverride prompt text resolved from an earlier scenario step, or null
     * @param inScenario true inside the ScenarioEngine, false for a standalone run
     * @return the normalized outcome of the run
     * @throws AssertionError when any expectation mismatches
     */
    CaseOutcome run(NegotiationCase testCase, @Nullable String promptOverride, boolean inScenario) {
        ScriptedNegotiationLlmClient llmClient = llmClientFor(testCase);
        NegotiationContentService service = new NegotiationContentService(orchestratorFor(testCase, llmClient));
        TaskApiAssembler taskApi =
                testCase.api().family() == NegotiationApi.Family.TASK ? taskApiFor(testCase, llmClient) : null;
        Object value = null;
        Throwable failure = null;
        try {
            value = invoke(service, taskApi, testCase, promptOverride);
        } catch (RuntimeException | AssertionError error) {
            failure = error;
        }
        CaseOutcome outcome = new CaseOutcome(testCase, value, failure, llmClient.callCount(), llmClient);
        assertExpectations(outcome, inScenario);
        return outcome;
    }

    // ------------------------------------------------------------------ outcome record

    /**
     * The normalized outcome of one executed case.
     *
     * @param testCase the executed expanded corpus case
     * @param value the returned {@code MetadataContent} or {@code FilledParamData} on success, or null on failure
     * @param failure the captured exception on failure, or null on success
     * @param llmCalls exact number of LLM calls the run made
     * @param llmClient the scripted client the run used
     */
    public record CaseOutcome(
            NegotiationCase testCase,
            @Nullable Object value,
            @Nullable Throwable failure,
            int llmCalls,
            ScriptedNegotiationLlmClient llmClient) {

        /**
         * Returns the generated message of a successful generation run.
         *
         * @return the metadata content, or null when the run failed or produced filled parameters
         */
        public @Nullable MetadataContent message() {
            return value instanceof MetadataContent metadata ? metadata : null;
        }

        /**
         * Returns the filled parameters of a successful validation run.
         *
         * @return the filled parameter data, or null when the run failed or produced a message
         */
        public @Nullable FilledParamData filledParams() {
            return value instanceof FilledParamData filled ? filled : null;
        }
    }

    // ------------------------------------------------------------------ expectation comparison

    private void assertExpectations(CaseOutcome outcome, boolean inScenario) {
        NegotiationCase testCase = outcome.testCase();
        Expectation expect = testCase.expect();
        if (expect.success()) {
            if (outcome.failure() != null) {
                fail(testCase, "$.expect.outcome", "success", "failure (" + outcome.failure() + ")");
            }
            assertSuccessFields(outcome, inScenario);
        } else {
            if (outcome.failure() == null) {
                fail(
                        testCase,
                        "$.expect.outcome",
                        "failure",
                        "success (" + typeName(outcome.value()) + ": " + outcome.value() + ")");
            }
            assertFailureFields(outcome);
        }
        if (expect.llmCalls() != null && expect.llmCalls() != outcome.llmCalls()) {
            fail(testCase, "$.expect.llmCalls", String.valueOf(expect.llmCalls()), String.valueOf(outcome.llmCalls()));
        }
        for (String contractName : expect.contracts()) {
            assertContract(outcome, contractName);
        }
        if (expect.differential()) {
            runDifferential(outcome);
        }
    }

    private void assertFailureFields(CaseOutcome outcome) {
        NegotiationCase testCase = outcome.testCase();
        Expectation expect = testCase.expect();
        Throwable failure = outcome.failure();
        if (expect.exception() != null) {
            String actual = failure.getClass().getSimpleName();
            if (!expect.exception().equals(actual)) {
                fail(testCase, "$.expect.exception", expect.exception(), actual);
            }
        }
        if (expect.code() != null) {
            if (!(failure instanceof A2ATError error)) {
                throw fail(
                        testCase,
                        "$.expect.code",
                        expect.code(),
                        "no error code (exception " + failure.getClass().getSimpleName() + ")");
            }
            if (!expect.code().equals(error.getCode())) {
                fail(testCase, "$.expect.code", expect.code(), error.getCode());
            }
        }
        for (String fragment : expect.messageContains()) {
            String message = failure.getMessage();
            if (message == null || !(message.contains(fragment) || message.contains(performativeWording(fragment)))) {
                fail(
                        testCase,
                        "$.expect.messageContains",
                        "a message containing '" + fragment + "'",
                        "message " + quoted(message));
            }
        }
        if (!expect.slotErrors().isEmpty()) {
            List<SlotValidationError> slotErrors = slotErrorsOf(failure);
            if (slotErrors == null) {
                throw fail(
                        testCase,
                        "$.expect.slotErrors",
                        renderSlotErrors(expect.slotErrors()),
                        "exception " + failure.getClass().getSimpleName() + " carries no slot errors");
            }
            assertSlotErrors(testCase, expect.slotErrors(), slotErrors);
        }
    }

    /**
     * Returns the slot errors the failure carries, or null when the failure type carries none.
     *
     * <p>The negotiation content layer surfaces its failures as {@link NegotiationParamExtractionException} (an
     * {@code A2ATBusinessException} subtype since the ErrorCatalog migration) while the prompt families still use
     * {@link A2ATParamExtractionError}; the engine accepts both carriers.
     *
     * @param failure captured failure of the run
     * @return the slot validation errors of the failure, or null when the failure carries none
     */
    private static @Nullable List<SlotValidationError> slotErrorsOf(Throwable failure) {
        if (failure instanceof A2ATParamExtractionError extraction) {
            return extraction.getErrors();
        }
        if (failure instanceof NegotiationParamExtractionException negotiation) {
            return negotiation.getErrors();
        }
        return null;
    }

    private void assertSlotErrors(
            NegotiationCase testCase, List<Expectation.SlotError> expected, List<SlotValidationError> actual) {
        Map<String, Integer> expectedCounts = new LinkedHashMap<>();
        for (Expectation.SlotError slotError : expected) {
            expectedCounts.merge(slotError.slot() + ":" + slotError.code(), 1, Integer::sum);
        }
        Map<String, Integer> actualCounts = new LinkedHashMap<>();
        List<String> actualPairs = new ArrayList<>();
        for (SlotValidationError error : actual) {
            String pair = error.slotName() + ":" + error.code();
            actualCounts.merge(pair, 1, Integer::sum);
            actualPairs.add(pair);
        }
        if (!expectedCounts.equals(actualCounts)) {
            fail(
                    testCase,
                    "$.expect.slotErrors",
                    renderSlotErrors(expected),
                    String.join(", ", actualPairs.isEmpty() ? List.of("(none)") : actualPairs));
        }
    }

    private void assertSuccessFields(CaseOutcome outcome, boolean inScenario) {
        NegotiationCase testCase = outcome.testCase();
        Expectation expect = testCase.expect();
        if (expect.promptTextEqualsGolden() != null) {
            MetadataContent message = requireMessage(outcome, "$.expect.promptTextEqualsGolden");
            String golden = readGoldenFixture(testCase, expect.promptTextEqualsGolden());
            if (!normalize(message.promptText()).equals(golden)) {
                fail(
                        testCase,
                        "$.expect.promptTextEqualsGolden",
                        quoted(truncate(golden)),
                        quoted(truncate(normalize(message.promptText()))));
            }
        }
        if (expect.metadata() != null) {
            MetadataContent message = requireMessage(outcome, "$.expect.metadata");
            if (expect.metadata().templateUriEcho() != null
                    && !expect.metadata().templateUriEcho().equals(message.templateUri())) {
                fail(
                        testCase,
                        "$.expect.metadata.templateUriEcho",
                        expect.metadata().templateUriEcho(),
                        message.templateUri());
            }
            if (Boolean.TRUE.equals(expect.metadata().contextEcho())) {
                // The input context is built with the performative of the case's API, so it equals the emitted context
                // the generation pipeline stamped with the performative of the addressed template.
                NegotiationContext expectedContext = toContext(testCase.context(), testCase.api());
                if (!Objects.equals(expectedContext, message.negotiationContext())) {
                    fail(
                            testCase,
                            "$.expect.metadata.contextEcho",
                            String.valueOf(expectedContext),
                            String.valueOf(message.negotiationContext()));
                }
            }
        }
        if (!expect.params().isEmpty() || expect.missingParams() != null) {
            if (!(outcome.value() instanceof FilledParamData filled)) {
                throw fail(
                        testCase,
                        expect.missingParams() != null && expect.params().isEmpty()
                                ? "$.expect.missingParams"
                                : "$.expect.params",
                        expect.missingParams() != null
                                ? "filled parameter data with the missing-parameter set " + expect.missingParams()
                                : render(expect.params()),
                        "no filled parameter data (" + typeName(outcome.value()) + ")");
            }
            if (expect.missingParams() != null) {
                // Task semantics (Q21): the expected params are a subset check with exact values, and the missing
                // parameter set — the null-valued entries of the filled data — is asserted exactly.
                for (Map.Entry<String, Object> entry : expect.params().entrySet()) {
                    if (!filled.data().containsKey(entry.getKey())
                            || !Objects.equals(entry.getValue(), filled.data().get(entry.getKey()))) {
                        fail(
                                testCase,
                                "$.expect.params",
                                entry.getKey() + "=" + entry.getValue(),
                                entry.getKey() + "=" + filled.data().get(entry.getKey()));
                    }
                }
                List<String> actualMissing = filled.data().entrySet().stream()
                        .filter(entry -> entry.getValue() == null)
                        .map(Map.Entry::getKey)
                        .toList();
                Set<String> expectedMissing = new LinkedHashSet<>(expect.missingParams());
                if (!expectedMissing.equals(new LinkedHashSet<>(actualMissing))) {
                    fail(
                            testCase,
                            "$.expect.missingParams",
                            String.join(", ", expectedMissing),
                            actualMissing.isEmpty() ? "(none)" : String.join(", ", actualMissing));
                }
            } else if (!Objects.equals(expect.params(), filled.data())) {
                fail(testCase, "$.expect.params", render(expect.params()), render(filled.data()));
            }
        }
        for (String fragment : expect.promptTextContains()) {
            MetadataContent message = requireMessage(outcome, "$.expect.promptTextContains");
            String promptText = message.promptText() == null ? "" : message.promptText();
            if (!promptText.contains(fragment)) {
                fail(
                        testCase,
                        "$.expect.promptTextContains",
                        "a prompt text containing '" + fragment + "'",
                        quoted(truncate(promptText)));
            }
        }
        if (expect.paramsFromStep() != null && !inScenario) {
            throw new IllegalStateException(testCase.errorPrefix() + " expect.paramsFromStep " + expect.paramsFromStep()
                    + " is resolved by the ScenarioEngine; run the enclosing scenario through the"
                    + " ScenarioEngine");
        }
    }

    // ------------------------------------------------------------------ contracts (P0)

    private void assertContract(CaseOutcome outcome, String contractName) {
        NegotiationCase testCase = outcome.testCase();
        Contract contract = Contract.fromJsonName(contractName);
        if (contract == null) {
            fail(
                    testCase,
                    "$.expect.contracts",
                    "a registered contract name (one of " + knownContractNames() + ")",
                    "'" + contractName + "'");
        }
        if (!contract.isP0()) {
            fail(
                    testCase,
                    "$.expect.contracts",
                    "a P0 contract the engine asserts today",
                    "'" + contractName + "' is registered as a P1 expectation and is not yet lit");
        }
        switch (contract) {
            case CONCLUSION_LITERAL_PRESENT -> assertConclusionLiteralPresent(outcome);
            case CONTEXT_KEYS_IN_MERGED_PARAMS -> assertContextKeysInMergedParams(outcome);
            case NO_LLM_LEAK_IN_USER_MESSAGE -> assertNoLlmLeakInUserMessage(outcome);
            case METADATA_TRIPLE_SHAPE -> assertMetadataTripleShape(outcome);
            default -> fail(
                    testCase,
                    "$.expect.contracts",
                    "an implemented contract",
                    "'" + contractName + "' is flagged P0 but has no implementation");
        }
    }

    private void assertConclusionLiteralPresent(CaseOutcome outcome) {
        NegotiationCase testCase = outcome.testCase();
        String literal =
                switch (testCase.api()) {
                    case GENERATE_ACCEPT_FROM_TEXT, GENERATE_ACCEPT_FROM_DATA -> "Accept";
                    case GENERATE_REJECT_FROM_TEXT, GENERATE_REJECT_FROM_DATA -> "Reject";
                    case GENERATE_ABORT_FROM_TEXT, GENERATE_ABORT_FROM_DATA -> "Abort";
                    default -> null;
                };
        if (literal == null) {
            fail(
                    testCase,
                    "$.expect.contracts[conclusionLiteralPresent]",
                    "a terminal or abort generation API",
                    testCase.api().jsonName());
        }
        MetadataContent message = requireMessage(outcome, "$.expect.contracts[conclusionLiteralPresent]");
        if (!String.valueOf(message.promptText()).contains(literal)) {
            fail(
                    testCase,
                    "$.expect.contracts[conclusionLiteralPresent]",
                    "a prompt text containing the literal '" + literal + "'",
                    quoted(truncate(message.promptText())));
        }
    }

    private void assertContextKeysInMergedParams(CaseOutcome outcome) {
        NegotiationCase testCase = outcome.testCase();
        if (!(outcome.value() instanceof FilledParamData filled)) {
            throw fail(
                    testCase,
                    "$.expect.contracts[contextKeysInMergedParams]",
                    "filled parameter data carrying the context keys",
                    typeName(outcome.value()));
        }
        for (String key : List.of("id", "round", "maxRounds")) {
            if (!filled.data().containsKey(key)) {
                fail(
                        testCase,
                        "$.expect.contracts[contextKeysInMergedParams]",
                        "merged params containing '" + key + "'",
                        render(filled.data()));
            }
        }
    }

    private void assertNoLlmLeakInUserMessage(CaseOutcome outcome) {
        NegotiationCase testCase = outcome.testCase();
        List<String> details = outcome.llmClient().leakedFailureDetails();
        if (details.isEmpty()) {
            return;
        }
        String visible = outcome.failure() != null
                ? outcome.failure().getMessage()
                : outcome.message() != null ? outcome.message().promptText() : null;
        if (visible == null) {
            fail(
                    testCase,
                    "$.expect.contracts[noLlmLeakInUserMessage]",
                    "a user-visible message to inspect",
                    "neither a failure message nor a generated message was produced");
        }
        for (String detail : details) {
            if (visible.contains(detail)) {
                fail(
                        testCase,
                        "$.expect.contracts[noLlmLeakInUserMessage]",
                        "a message free of the raw LLM failure detail '" + detail + "'",
                        quoted(truncate(visible)));
            }
        }
    }

    private void assertMetadataTripleShape(CaseOutcome outcome) {
        NegotiationCase testCase = outcome.testCase();
        MetadataContent message = requireMessage(outcome, "$.expect.contracts[metadataTripleShape]");
        Map<String, Object> metadata = message.buildMetadataContent();
        if (metadata.size() != 3
                || !metadata.containsKey(message.extensionUri())
                || !metadata.containsKey(MetadataContent.TEMPLATE_URI_METADATA_KEY)
                || !metadata.containsKey(MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY)) {
            fail(
                    testCase,
                    "$.expect.contracts[metadataTripleShape]",
                    "exactly the three entries <extensionUri, templateUri, negotiationContext>",
                    String.valueOf(metadata.keySet()));
        }
        NegotiationPerformative performative = performativeOf(testCase.api());
        Object nested = metadata.get(MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY);
        if (!(nested instanceof Map<?, ?> context)
                || context.size() != 4
                || !context.containsKey("id")
                || !context.containsKey("round")
                || !context.containsKey("maxRounds")
                || !performative.name().equals(context.get("performative"))) {
            fail(
                    testCase,
                    "$.expect.contracts[metadataTripleShape]",
                    "the nested negotiation context with exactly <id, round, maxRounds, performative="
                            + performative.name() + ">",
                    String.valueOf(nested));
        }
    }

    // ------------------------------------------------------------------ differential (Q17 C+)

    private void runDifferential(CaseOutcome main) {
        NegotiationCase testCase = main.testCase();
        if (testCase.api().family() != NegotiationApi.Family.FROM_TEXT) {
            fail(
                    testCase,
                    "$.expect.differential",
                    "a from-text family API",
                    testCase.api().jsonName());
        }
        if (testCase.inputData() == null) {
            fail(testCase, "$.expect.differential", "input.data alongside input.text", "no input.data");
        }
        if (testCase.expect().promptTextEqualsGolden() == null) {
            fail(
                    testCase,
                    "$.expect.differential",
                    "promptTextEqualsGolden as the third comparison leg",
                    "no golden name");
        }
        MetadataContent fromText = requireMessage(main, "$.expect.differential");
        ScriptedNegotiationLlmClient zeroCallClient = ScriptedNegotiationLlmClient.assertionOnly();
        NegotiationContentService service = new NegotiationContentService(orchestratorFor(testCase, zeroCallClient));
        MetadataContent fromData;
        try {
            fromData = generateFromData(service, testCase);
        } catch (RuntimeException | AssertionError error) {
            fail(testCase, "$.expect.differential", "a successful from-data message", "failure (" + error + ")");
            return;
        }
        if (!Objects.equals(fromText.promptText(), fromData.promptText())) {
            fail(
                    testCase,
                    "$.expect.differential",
                    "fromText == fromData (" + quoted(truncate(fromText.promptText())) + ")",
                    quoted(truncate(fromData.promptText())));
        }
        String golden = readGoldenFixture(testCase, testCase.expect().promptTextEqualsGolden());
        if (!normalize(fromData.promptText()).equals(golden)) {
            fail(
                    testCase,
                    "$.expect.differential",
                    "fromData == golden " + quoted(truncate(golden)),
                    quoted(truncate(normalize(fromData.promptText()))));
        }
        if (zeroCallClient.callCount() != 0) {
            fail(
                    testCase,
                    "$.expect.differential",
                    "0 LLM calls on the from-data leg",
                    String.valueOf(zeroCallClient.callCount()));
        }
    }

    private MetadataContent generateFromData(NegotiationContentService service, NegotiationCase testCase) {
        TemplateUri templateUri = parseTemplateUri(testCase);
        NegotiationContext context = toContext(testCase.context(), testCase.api());
        JsonNode data = testCase.inputData();
        return switch (testCase.api()) {
            case GENERATE_PROPOSE_FROM_TEXT -> service.generateProposeFromData(
                    (NegotiationProposeData) TypedInputAssembler.assemble(
                            data, context, templateUri, NegotiationPerformative.PROPOSE, testCase.language()),
                    templateUri);
            case GENERATE_ACCEPT_FROM_TEXT -> service.generateAcceptFromData(
                    (NegotiationEndingData) TypedInputAssembler.assemble(
                            data, context, templateUri, NegotiationPerformative.ACCEPT, testCase.language()),
                    templateUri);
            case GENERATE_REJECT_FROM_TEXT -> service.generateRejectFromData(
                    (NegotiationEndingData) TypedInputAssembler.assemble(
                            data, context, templateUri, NegotiationPerformative.REJECT, testCase.language()),
                    templateUri);
            case GENERATE_ABORT_FROM_TEXT -> service.generateAbortFromData(
                    (NegotiationAbortData) TypedInputAssembler.assemble(
                            data, context, templateUri, NegotiationPerformative.ABORT, testCase.language()),
                    templateUri);
            default -> throw new IllegalStateException(
                    "The differential run pairs a from-text API with its from-data twin but got "
                            + testCase.api().jsonName() + ".");
        };
    }

    // ------------------------------------------------------------------ production wiring

    private static ScriptedNegotiationLlmClient llmClientFor(NegotiationCase testCase) {
        if (testCase.api().family() == NegotiationApi.Family.FROM_DATA || testCase.llm() == null) {
            return ScriptedNegotiationLlmClient.assertionOnly();
        }
        return new ScriptedNegotiationLlmClient(testCase.llm().steps());
    }

    private static NegotiationGenerationOrchestrator orchestratorFor(
            NegotiationCase testCase, ScriptedNegotiationLlmClient llmClient) {
        NegotiationGenerationOrchestratorBuilder builder = NegotiationGenerationOrchestratorBuilder.builder()
                .language(testCase.language())
                .llmClient(llmClient);
        if (testCase.llm() != null && testCase.llm().maxAttempts() != null) {
            builder.maxAttempts(testCase.llm().maxAttempts());
        }
        if (INJECT_FAILING_TEMPLATE_LOADER.equals(testCase.inject())) {
            builder.templateLoader(failingTemplateLoader());
        }
        if (INJECT_FAILING_SEMANTIC_VALIDATOR.equals(testCase.inject())) {
            builder.semanticValidator(failingSemanticValidator());
        }
        return builder.build();
    }

    /**
     * Assembles the task API wiring of the closed loop for one case through the real facade builders: the retry limit
     * comes from the case's LLM script or falls back to the builder default of 3.
     *
     * @param testCase expanded corpus case of the task family
     * @param llmClient scripted LLM client injected at the facade builders' LLM seam
     * @return the task API assembly of the case's language
     */
    private static TaskApiAssembler taskApiFor(NegotiationCase testCase, ScriptedNegotiationLlmClient llmClient) {
        int maxAttempts = testCase.llm() != null && testCase.llm().maxAttempts() != null
                ? testCase.llm().maxAttempts()
                : 3;
        return new TaskApiAssembler(testCase.language(), maxAttempts, llmClient);
    }

    private static NegotiationTemplateLoader failingTemplateLoader() {
        return new NegotiationTemplateLoader() {
            @Override
            public PromptTemplate load(NegotiationReference reference) {
                throw new ResourceNotFoundException("Negotiation template does not exist.", reference.uri());
            }
        };
    }

    /**
     * Replays the semantic validator of a language without bundled semantic validation prompt resources: the pipeline
     * maps the resource failure onto the public {@code template.not_found} code without bubbling the raw exception (the
     * validate-family leg of the template-resolution matrix; mirrors the hand-written pipeline suite).
     *
     * @return semantic validator failing every call with a resource-not-found exception
     */
    private static NegotiationSemanticValidator failingSemanticValidator() {
        return (prompt, callerSchema, reference, templateContent) -> {
            throw new ResourceNotFoundException(
                    "Negotiation semantic validation prompt resource does not exist for language "
                            + reference.language()
                            + "; set A2AT_LANGUAGE to a language with bundled prompt resources (zh-CN or en-US).",
                    "prompt_resources/prompts/negotiation_semantic_validation/" + reference.language() + "/system.md");
        };
    }

    // ------------------------------------------------------------------ API dispatch

    private Object invoke(
            NegotiationContentService service,
            @Nullable TaskApiAssembler taskApi,
            NegotiationCase testCase,
            @Nullable String promptOverride) {
        TemplateUri templateUri = parseTemplateUri(testCase);
        NegotiationContext context = toContext(testCase.context(), testCase.api());
        return switch (testCase.api()) {
            case GENERATE_PROPOSE_FROM_TEXT -> service.generateProposeFromText(
                    testCase.inputText(), context, templateUri);
            case GENERATE_ACCEPT_FROM_TEXT -> service.generateAcceptFromText(
                    testCase.inputText(), context, templateUri);
            case GENERATE_REJECT_FROM_TEXT -> service.generateRejectFromText(
                    testCase.inputText(), context, templateUri);
            case GENERATE_ABORT_FROM_TEXT -> service.generateAbortFromText(testCase.inputText(), context, templateUri);
            case GENERATE_PROPOSE_FROM_DATA -> service.generateProposeFromData(
                    (NegotiationProposeData) TypedInputAssembler.assemble(
                            requireInputData(testCase),
                            context,
                            templateUri,
                            NegotiationPerformative.PROPOSE,
                            testCase.language()),
                    templateUri);
            case GENERATE_ACCEPT_FROM_DATA -> service.generateAcceptFromData(
                    (NegotiationEndingData) TypedInputAssembler.assemble(
                            requireInputData(testCase),
                            context,
                            templateUri,
                            NegotiationPerformative.ACCEPT,
                            testCase.language()),
                    templateUri);
            case GENERATE_REJECT_FROM_DATA -> service.generateRejectFromData(
                    (NegotiationEndingData) TypedInputAssembler.assemble(
                            requireInputData(testCase),
                            context,
                            templateUri,
                            NegotiationPerformative.REJECT,
                            testCase.language()),
                    templateUri);
            case GENERATE_ABORT_FROM_DATA -> service.generateAbortFromData(
                    (NegotiationAbortData) TypedInputAssembler.assemble(
                            requireInputData(testCase),
                            context,
                            templateUri,
                            NegotiationPerformative.ABORT,
                            testCase.language()),
                    templateUri);
            case VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING -> service.validateProposePromptAndDataFilling(
                    promptOf(testCase, promptOverride), context, schemaOf(testCase), templateUri);
            case VALIDATE_ACCEPT_PROMPT_AND_DATA_FILLING -> service.validateAcceptPromptAndDataFilling(
                    promptOf(testCase, promptOverride), context, schemaOf(testCase), templateUri);
            case VALIDATE_REJECT_PROMPT_AND_DATA_FILLING -> service.validateRejectPromptAndDataFilling(
                    promptOf(testCase, promptOverride), context, schemaOf(testCase), templateUri);
            case VALIDATE_ABORT_PROMPT_AND_DATA_FILLING -> service.validateAbortPromptAndDataFilling(
                    promptOf(testCase, promptOverride), context, schemaOf(testCase), templateUri);
            case GENERATE_TASK_PROMPT_FROM_TEXT -> requireTaskApi(taskApi, testCase)
                    .generateTaskPromptFromText(requireInputText(testCase), templateUri);
            case GENERATE_TASK_PROMPT_FROM_DATA_WITH_SCHEMA -> requireTaskApi(taskApi, testCase)
                    .generateTaskPromptFromDataWithSchema(dataOf(testCase), requireSchema(testCase), templateUri);
            case VALIDATE_TASK_PROMPT_AND_DATA_FILLING -> requireTaskApi(taskApi, testCase)
                    .validateTaskPromptAndDataFilling(
                            promptOf(testCase, promptOverride), requireSchema(testCase), templateUri);
        };
    }

    private static TaskApiAssembler requireTaskApi(@Nullable TaskApiAssembler taskApi, NegotiationCase testCase) {
        if (taskApi == null) {
            throw new IllegalStateException(
                    testCase.errorPrefix() + " the task API assembly is only wired for the task family but got "
                            + testCase.api().jsonName());
        }
        return taskApi;
    }

    private static String requireInputText(NegotiationCase testCase) {
        String text = testCase.inputText();
        if (text == null) {
            throw new IllegalStateException(
                    testCase.errorPrefix() + " input.text: the task from-text API requires a text input");
        }
        return text;
    }

    private static Map<String, Object> requireSchema(NegotiationCase testCase) {
        Map<String, Object> schema = schemaOf(testCase);
        if (schema == null) {
            throw new IllegalStateException(testCase.errorPrefix() + " schema: the task APIs require a schema");
        }
        return schema;
    }

    private static Map<String, Object> dataOf(NegotiationCase testCase) {
        JsonNode data = testCase.inputData();
        if (data == null || !data.isObject()) {
            throw new IllegalStateException(
                    testCase.errorPrefix() + " input.data: the task from-data API requires typed input data");
        }
        return MAPPER.convertValue(data, new TypeReference<Map<String, Object>>() {});
    }

    private static TemplateUri parseTemplateUri(NegotiationCase testCase) {
        String raw = testCase.templateUri();
        if (raw == null) {
            return null;
        }
        return TemplateUri.parse(raw)
                .orElseThrow(() -> new IllegalArgumentException("Unparseable template URI: " + raw));
    }

    private static JsonNode requireInputData(NegotiationCase testCase) {
        JsonNode data = testCase.inputData();
        if (data == null) {
            throw new IllegalStateException(
                    testCase.errorPrefix() + " input.data: the from-data family requires typed input data");
        }
        return data;
    }

    private static String promptOf(NegotiationCase testCase, @Nullable String promptOverride) {
        if (promptOverride != null) {
            return promptOverride;
        }
        PromptSource source = testCase.prompt();
        if (source == null) {
            return null;
        }
        if (source instanceof PromptSource.Text text) {
            return text.text();
        }
        if (source instanceof PromptSource.Golden golden) {
            return readGoldenFixture(testCase, golden.golden());
        }
        PromptSource.FromStep fromStep = (PromptSource.FromStep) source;
        throw new IllegalStateException(testCase.errorPrefix() + " prompt.fromStep " + fromStep.step()
                + " is resolved by the ScenarioEngine; run the enclosing scenario through the ScenarioEngine");
    }

    private static @Nullable Map<String, Object> schemaOf(NegotiationCase testCase) {
        JsonNode schema = testCase.schema();
        if (schema == null) {
            return null;
        }
        return MAPPER.convertValue(schema, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Builds the input context of a case, stamped with the performative of the case's API.
     *
     * <p>The generation pipeline stamps the performative of the addressed template onto the emitted context, so an
     * input context built with the API's performative echoes back unchanged; a validate API receives the message of
     * exactly that performative, so its input context carries it as well.
     *
     * @param spec context spec of the case, or null
     * @param api API of the case
     * @return the negotiation context of the spec stamped with the API's performative, or null when the case carries no
     *     context spec or the task-family API takes no negotiation context
     */
    private static @Nullable NegotiationContext toContext(@Nullable ContextSpec spec, NegotiationApi api) {
        NegotiationPerformative performative = performativeOf(api);
        if (spec == null || performative == null) {
            return null;
        }
        return new NegotiationContext(spec.id(), spec.round(), spec.maxRounds(), performative);
    }

    /**
     * Returns the performative an API addresses: each generation API emits the message of one performative and each
     * validate API checks the message of one performative, while the task family produces no negotiation message.
     *
     * @param api API of the case
     * @return the performative of the generation and validate families, or null for the task family
     */
    private static @Nullable NegotiationPerformative performativeOf(NegotiationApi api) {
        return switch (api) {
            case GENERATE_PROPOSE_FROM_TEXT,
                    GENERATE_PROPOSE_FROM_DATA,
                    VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING -> NegotiationPerformative.PROPOSE;
            case GENERATE_ACCEPT_FROM_TEXT,
                    GENERATE_ACCEPT_FROM_DATA,
                    VALIDATE_ACCEPT_PROMPT_AND_DATA_FILLING -> NegotiationPerformative.ACCEPT;
            case GENERATE_REJECT_FROM_TEXT,
                    GENERATE_REJECT_FROM_DATA,
                    VALIDATE_REJECT_PROMPT_AND_DATA_FILLING -> NegotiationPerformative.REJECT;
            case GENERATE_ABORT_FROM_TEXT,
                    GENERATE_ABORT_FROM_DATA,
                    VALIDATE_ABORT_PROMPT_AND_DATA_FILLING -> NegotiationPerformative.ABORT;
            default -> null;
        };
    }

    /**
     * Rewrites the pre-rename wording of the frozen corpus fixtures: the case JSON files still say {@code expected
     * phase}, while the orchestrator's reference-resolution error has said {@code expected performative} since the
     * {@code phase} to {@code performative} rename. The corpus case JSON files are frozen by policy, so the harness
     * translates the legacy fragment instead of the fixtures.
     *
     * @param fragment message fragment declared by a frozen case fixture
     * @return the fragment with the legacy wording translated to the current one
     */
    private static String performativeWording(String fragment) {
        return fragment.replace("expected phase", "expected performative");
    }

    // ------------------------------------------------------------------ helpers

    private static MetadataContent requireMessage(CaseOutcome outcome, String jsonPath) {
        NegotiationCase testCase = outcome.testCase();
        MetadataContent message = outcome.message();
        if (message == null) {
            fail(
                    testCase,
                    jsonPath,
                    "a generated negotiation message",
                    "failure (" + outcome.failure() + ") or " + typeName(outcome.value()));
        }
        return message;
    }

    private static String readGoldenFixture(NegotiationCase testCase, String goldenName) {
        String resourcePath = "/golden/" + testCase.language() + "/" + goldenName + ".md";
        InputStream stream = CaseEngine.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new AssertionError(testCase.errorPrefix() + " golden fixture '" + goldenName
                    + "' does not exist on the test" + " classpath: " + resourcePath);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new AssertionError(
                    testCase.errorPrefix() + " failed to read the golden fixture " + resourcePath, exception);
        }
    }

    private static String normalize(@Nullable String text) {
        return text == null ? null : text.replace("\r\n", "\n");
    }

    private static String typeName(@Nullable Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static String render(Map<String, Object> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (IOException exception) {
            return String.valueOf(values);
        }
    }

    private static String renderSlotErrors(List<Expectation.SlotError> slotErrors) {
        List<String> pairs = new ArrayList<>();
        for (Expectation.SlotError slotError : slotErrors) {
            pairs.add(slotError.slot() + ":" + slotError.code());
        }
        return String.join(", ", pairs);
    }

    private static String knownContractNames() {
        List<String> names = new ArrayList<>();
        for (Contract contract : Contract.values()) {
            names.add(contract.jsonName());
        }
        return String.join(", ", names);
    }

    private static String truncate(String text) {
        return text.length() <= MAX_REPORTED_TEXT_LENGTH ? text : text.substring(0, MAX_REPORTED_TEXT_LENGTH) + "...";
    }

    private static String quoted(@Nullable String text) {
        return text == null ? "null" : "<" + text + ">";
    }

    private static AssertionError fail(NegotiationCase testCase, String jsonPath, String expected, String actual) {
        throw new AssertionError(
                testCase.errorPrefix() + " " + jsonPath + ": expected " + expected + " but was " + actual);
    }
}
