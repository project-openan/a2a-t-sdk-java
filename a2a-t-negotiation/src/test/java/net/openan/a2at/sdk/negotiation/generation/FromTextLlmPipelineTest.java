package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.LlmConfig;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGoldenCases.GoldenCase;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

/**
 * Integration tests of the from-text generation pipeline against the real built-in resources.
 *
 * <p>The LLM is replaced by a scripted client that returns queued payloads or failures; every other collaborator is the
 * production wiring. The suite covers the single-step content extraction (one LLM call, no type-recognition step), the
 * byte-equality of the from-text output with the from-data output and the golden fixtures, the step-level retry policy
 * with its warning logs, the infrastructure failure paths, the non-retryable failure codes that must stop after one LLM
 * call, the max-attempts configuration semantics and the default en-US end-to-end chain.
 */
class FromTextLlmPipelineTest {

    private static final String ZH_CN = NegotiationGoldenCases.ZH_CN;

    private static final String EN_US = NegotiationGoldenCases.EN_US;

    private static final TemplateUri INFORMATION_PROPOSE_URI = GoldenCase.INFORMATION_PROPOSE.template();

    private final ListAppender<ILoggingEvent> orchestratorAppender = new ListAppender<>();

    private final ListAppender<ILoggingEvent> extractorAppender = new ListAppender<>();

    private final ListAppender<ILoggingEvent> llmConfigAppender = new ListAppender<>();

    private final Logger orchestratorLogger = (Logger) LoggerFactory.getLogger(NegotiationGenerationOrchestrator.class);

    private final Logger extractorLogger = (Logger) LoggerFactory.getLogger(DefaultNegotiationContentExtractor.class);

    private final Logger llmConfigLogger = (Logger) LoggerFactory.getLogger(LlmConfig.class);

    @BeforeEach
    void attachLogAppenders() {
        orchestratorAppender.start();
        orchestratorLogger.addAppender(orchestratorAppender);
        extractorAppender.start();
        extractorLogger.addAppender(extractorAppender);
        llmConfigAppender.start();
        llmConfigLogger.addAppender(llmConfigAppender);
    }

    @AfterEach
    void detachLogAppenders() {
        orchestratorLogger.detachAppender(orchestratorAppender);
        extractorLogger.detachAppender(extractorAppender);
        llmConfigLogger.detachAppender(llmConfigAppender);
    }

    /**
     * IT-B-001: the from-text chain runs exactly one content-extraction LLM call and then renders deterministically:
     * the output equals both the from-data generation of the same content and the committed golden fixture byte for
     * byte, the metadata contract holds, and no type-recognition step exists anywhere in the pipeline.
     */
    @Test
    void fromTextMatchesFromDataAndTheGoldenFixtureAfterOneExtractionCall() {
        GoldenCase goldenCase = GoldenCase.INFORMATION_PROPOSE;
        ScriptedExtractionClient llm =
                new ScriptedExtractionClient(extractionJson(goldenCase.content(ZH_CN), goldenCase.performative()));
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 3);

        MetadataContent fromText = orchestrator.generateProposeFromText(
                "请补充接入端口名称（如P533-珠江旧城-PTN3900-23-TPA1EG24-1）、投诉分类（如专线质差）与专线业务标识信息。",
                goldenCase.context(),
                goldenCase.template());

        assertEquals(1, llm.calls, "from-text generation must run exactly one content extraction call");
        assertEquals(goldenCase.templateUri(), fromText.templateUri());

        MetadataContent fromData = goldenCase.generate(orchestrator, ZH_CN);
        assertEquals(1, llm.calls, "the from-data variant must not add any LLM call");
        assertEquals(fromData.promptText(), fromText.promptText());
        assertEquals(goldenCase.goldenText(ZH_CN), fromText.promptText());

        Map<String, Object> metadata = fromText.buildMetadataContent();
        assertEquals(3, metadata.size());
        assertEquals(fromText.promptText(), metadata.get(fromText.extensionUri()));
        assertEquals(fromText.templateUri(), metadata.get(MetadataContent.TEMPLATE_URI_METADATA_KEY));
        assertEquals(goldenCase.context().withPerformative(goldenCase.performative()), fromText.negotiationContext());
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedContext =
                (Map<String, Object>) metadata.get(MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY);
        assertEquals(goldenCase.context().id(), nestedContext.get("id"));
        assertEquals(goldenCase.context().round(), nestedContext.get("round"));
        assertEquals(goldenCase.context().maxRounds(), nestedContext.get("maxRounds"));

        assertEquals(
                2, llm.lastMessages.size(), "the extraction step must be a single system-plus-user message exchange");
        assertTrue(
                logMessages(extractorAppender, Level.INFO).stream()
                        .anyMatch(message -> message.startsWith("negotiation_content_extraction_completed")),
                "the completed extraction step must emit its info event");
        assertTrue(
                logMessages(orchestratorAppender, Level.INFO).stream()
                                .noneMatch(message -> message.contains("negotiation_type_recognition"))
                        && logMessages(extractorAppender, Level.INFO).stream()
                                .noneMatch(message -> message.contains("negotiation_type_recognition")),
                "the removed type-recognition step must never appear in the events");
    }

    /**
     * IT-B-002: every from-text method (propose, accept, reject, abort) succeeds for every negotiation type with
     * exactly one LLM call per generation, the caller-supplied template URI is echoed, the output matches the golden
     * fixture, the phase token reaches the LLM so the accept and reject phases can be distinguished, and the abort
     * method addresses the termination-specific extraction prompt.
     */
    @ParameterizedTest(name = "from-text succeeds for {0}")
    @EnumSource(GoldenCase.class)
    void fromTextSucceedsForEveryMethodAndTypeCombination(GoldenCase goldenCase) {
        ScriptedExtractionClient llm =
                new ScriptedExtractionClient(extractionJson(goldenCase.content(ZH_CN), goldenCase.performative()));
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 3);

        MetadataContent result = generateFromText(orchestrator, goldenCase, "请根据以下专线投诉诊断协商文本生成协商报文。");

        assertEquals(1, llm.calls);
        assertEquals(goldenCase.templateUri(), result.templateUri());
        assertEquals(goldenCase.goldenText(ZH_CN), result.promptText());
        String userPrompt = llm.lastMessages.get(1).get("content");
        if (goldenCase.performative() == NegotiationPerformative.ABORT) {
            assertTrue(
                    userPrompt.contains("终止"),
                    "the abort user prompt must address the termination content but was: " + userPrompt);
        } else {
            assertTrue(
                    userPrompt.contains(phaseToken(goldenCase.performative())),
                    "the user prompt must carry the phase token of the addressed method");
        }
    }

    /**
     * IT-B-002: the accept and reject methods share the same accept-reject template URI; both load that one template
     * and differ only in the rendered conclusion literal.
     */
    @Test
    void acceptAndRejectShareTheSameAcceptRejectTemplate() {
        assertEquals(
                GoldenCase.INFORMATION_ACCEPT.templateUri(),
                GoldenCase.INFORMATION_REJECT.templateUri(),
                "accept and reject must address the same accept-reject template");

        ScriptedExtractionClient acceptLlm = new ScriptedExtractionClient(
                extractionJson(GoldenCase.INFORMATION_ACCEPT.content(ZH_CN), NegotiationPerformative.ACCEPT));
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, acceptLlm, 3);
        MetadataContent acceptResult =
                generateFromText(orchestrator, GoldenCase.INFORMATION_ACCEPT, "请确认补充接入端口名称与投诉分类。");
        assertEquals(1, acceptLlm.calls);

        ScriptedExtractionClient rejectLlm = new ScriptedExtractionClient(
                extractionJson(GoldenCase.INFORMATION_REJECT.content(ZH_CN), NegotiationPerformative.REJECT));
        MetadataContent rejectResult =
                generateFromText(orchestrator(ZH_CN, rejectLlm, 3), GoldenCase.INFORMATION_REJECT, "请拒绝。");
        assertEquals(1, rejectLlm.calls);

        assertTrue(
                acceptResult.promptText().contains("\nAccept\n"), "the accept output must render the Accept literal");
        assertTrue(
                rejectResult.promptText().contains("\nReject\n"), "the reject output must render the Reject literal");
        assertEquals(acceptResult.templateUri(), rejectResult.templateUri());
    }

    /**
     * IT-B-003: a retryable extraction failure is retried until it succeeds; every failed attempt logs a warning with
     * the step, attempt, attempt limit and failure code, and the successful output still matches the golden fixture.
     */
    @Test
    void retriesContentExtractionUntilItSucceeds() {
        GoldenCase goldenCase = GoldenCase.INFORMATION_PROPOSE;
        ScriptedExtractionClient llm = new ScriptedExtractionClient(
                "", "", extractionJson(goldenCase.content(ZH_CN), goldenCase.performative()));
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 3);

        MetadataContent result =
                orchestrator.generateProposeFromText("请提供接入端口名称。", goldenCase.context(), goldenCase.template());

        assertEquals(3, llm.calls, "two failures followed by one success must result in three calls");
        assertEquals(goldenCase.goldenText(ZH_CN), result.promptText());

        List<String> retries = warningMessages("negotiation_llm_retry ");
        assertEquals(2, retries.size());
        for (int attempt = 1; attempt <= 2; attempt++) {
            String retry = retries.get(attempt - 1);
            assertTrue(retry.contains("step=negotiation_content_extract"), retry);
            assertTrue(retry.contains("attempt=" + attempt), retry);
            assertTrue(retry.contains("max_attempts=3"), retry);
            assertTrue(retry.contains("code=" + ErrorCatalog.LLM_RESPONSE_INVALID.getCode()), retry);
        }
        assertTrue(
                warningMessages("negotiation_llm_retry_exhausted").isEmpty(),
                "a successful retry sequence must not log the exhaustion event");
    }

    /**
     * IT-B-003: when every attempt fails with the retryable extraction failure code, the original error code is
     * rethrown after the exhaustion warning has been logged.
     */
    @Test
    void rethrowsTheOriginalCodeWhenRetriesAreExhausted() {
        ScriptedExtractionClient llm = new ScriptedExtractionClient("", "", "");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 3);

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供接入端口名称。", GoldenCase.INFORMATION_PROPOSE.context(), INFORMATION_PROPOSE_URI));

        assertEquals(ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), failure.getCode());
        assertEquals(3, llm.calls);

        List<String> exhausted = warningMessages("negotiation_llm_retry_exhausted");
        assertEquals(1, exhausted.size());
        assertTrue(exhausted.get(0).contains("step=negotiation_content_extract"), exhausted.get(0));
        assertTrue(exhausted.get(0).contains("max_attempts=3"), exhausted.get(0));
        assertTrue(exhausted.get(0).contains("code=" + ErrorCatalog.LLM_RESPONSE_INVALID.getCode()), exhausted.get(0));
        assertEquals(2, warningMessages("negotiation_llm_retry ").size());
        assertTrue(
                warningMessages("negotiation_generation_failed").stream()
                        .anyMatch(message -> message.contains("code=" + ErrorCatalog.LLM_RESPONSE_INVALID.getCode())),
                "the surfaced generation failure must be logged with its code");
    }

    /**
     * IT-B-004: persistent transport failures of the LLM client are retried and surface as the infrastructure error
     * code on the generation side.
     */
    @Test
    void retriesInfrastructureFailuresUntilExhaustion() {
        ScriptedExtractionClient llm = new ScriptedExtractionClient(new IllegalStateException("connection refused"));
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 2);

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供接入端口名称。", GoldenCase.INFORMATION_PROPOSE.context(), INFORMATION_PROPOSE_URI));

        assertEquals(ErrorCatalog.LLM_INVOCATION_FAILED.getCode(), failure.getCode());
        assertEquals(2, llm.calls);
        assertEquals(1, warningMessages("negotiation_llm_retry_exhausted").size());
        assertTrue(warningMessages("negotiation_llm_retry_exhausted")
                .get(0)
                .contains("code=" + ErrorCatalog.LLM_INVOCATION_FAILED.getCode()));
    }

    /**
     * IT-B-004: a persistently unparseable LLM response fails with the retryable extraction failure code after the
     * retries are exhausted, rethrowing the original code.
     */
    @Test
    void retriesUnparseableResponsesUntilExhaustion() {
        ScriptedExtractionClient llm = new ScriptedExtractionClient("<not a json object>");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 2);

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供接入端口名称。", GoldenCase.INFORMATION_PROPOSE.context(), INFORMATION_PROPOSE_URI));

        assertEquals(ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), failure.getCode());
        assertEquals(2, llm.calls);
        assertEquals(1, warningMessages("negotiation_llm_retry ").size());
        assertEquals(1, warningMessages("negotiation_llm_retry_exhausted").size());
    }

    /**
     * IT-B-005 (1): an extracted conclusion that contradicts the method phase fails with the non-retryable
     * invalid-input code after exactly one LLM call and without any retry event.
     */
    @Test
    void phaseConclusionMismatchFailsFastWithConclusionMismatch() {
        ScriptedExtractionClient llm = new ScriptedExtractionClient(
                "{\"conclusion\":\"Reject\",\"items\":[{\"name\":\"接入端口名称\",\"value\":\"P533-珠江旧城-PTN3900-23-TPA1EG24-1\"}]}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 3);

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateAcceptFromText(
                        "请接受。", GoldenCase.INFORMATION_ACCEPT.context(), GoldenCase.INFORMATION_ACCEPT.template()));

        assertEquals(ErrorCatalog.NEGOTIATION_CONCLUSION_MISMATCH.getCode(), failure.getCode());
        assertEquals(1, llm.calls, "non-retryable failures must not be attempted again");
        assertTrue(warningMessages("negotiation_llm_retry ").isEmpty());
        assertTrue(warningMessages("negotiation_llm_retry_exhausted").isEmpty());
    }

    /**
     * IT-B-005 (2): an extraction response missing a required field fails with the non-retryable slot-missing code
     * after exactly one LLM call.
     */
    @Test
    void missingRequiredFieldFailsFastWithSlotMissing() {
        ScriptedExtractionClient llm = new ScriptedExtractionClient("{\"relationship\":null}");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 3);

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供接入端口名称。", GoldenCase.INFORMATION_PROPOSE.context(), INFORMATION_PROPOSE_URI));

        assertEquals(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), failure.getCode());
        assertEquals(1, llm.calls, "non-retryable failures must not be attempted again");
        assertTrue(warningMessages("negotiation_llm_retry ").isEmpty());
        assertTrue(warningMessages("negotiation_llm_retry_exhausted").isEmpty());
    }

    /**
     * IT-B-005 (3): a template URI without any loadable template fails with template-not-found before the LLM is
     * touched at all.
     */
    @Test
    void missingTemplateFailsBeforeAnyLlmCall() {
        ScriptedExtractionClient llm = new ScriptedExtractionClient(
                extractionJson(GoldenCase.INFORMATION_PROPOSE.content(ZH_CN), NegotiationPerformative.PROPOSE));
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language(ZH_CN)
                .llmClient(llm)
                .templateLoader(missingTemplateLoader())
                .build();

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供接入端口名称。", GoldenCase.INFORMATION_PROPOSE.context(), INFORMATION_PROPOSE_URI));

        assertEquals(ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(), failure.getCode());
        assertEquals(0, llm.calls, "the template must be loaded before the LLM step is started");
    }

    /**
     * IT-B-005 (4): a template URI whose phase segment contradicts the method is a programming error with an
     * {@link IllegalArgumentException} pointing at the template URI and never reaches the LLM.
     */
    @Test
    void phaseMismatchedTemplateUriFailsBeforeAnyLlmCall() {
        ScriptedExtractionClient llm = new ScriptedExtractionClient(
                extractionJson(GoldenCase.INFORMATION_PROPOSE.content(ZH_CN), NegotiationPerformative.PROPOSE));
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 3);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供接入端口名称。",
                        GoldenCase.INFORMATION_PROPOSE.context(),
                        GoldenCase.INFORMATION_ACCEPT.template()));

        assertTrue(
                failure.getMessage()
                        .contains(
                                "Template URI does not address a negotiation template of the expected performative PROPOSE"),
                "the failure must point at the template URI but was: " + failure.getMessage());
        assertEquals(0, llm.calls);
    }

    /**
     * IT-B-006 (1): a single allowed attempt makes the retryable extraction failure surface after exactly one call,
     * with the exhaustion warning but without any per-attempt retry warning.
     */
    @Test
    void singleAttemptFailsFast() {
        ScriptedExtractionClient llm = new ScriptedExtractionClient("");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 1);

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供接入端口名称。", GoldenCase.INFORMATION_PROPOSE.context(), INFORMATION_PROPOSE_URI));

        assertEquals(ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), failure.getCode());
        assertEquals(1, llm.calls);
        assertTrue(warningMessages("negotiation_llm_retry ").isEmpty());
        assertEquals(1, warningMessages("negotiation_llm_retry_exhausted").size());
    }

    /** IT-B-006 (2): an attempt limit of two allows exactly one retry of a retryable failure. */
    @Test
    void twoAttemptsRetryExactlyOnce() {
        ScriptedExtractionClient llm = new ScriptedExtractionClient("", "");
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 2);

        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> orchestrator.generateProposeFromText(
                        "请提供接入端口名称。", GoldenCase.INFORMATION_PROPOSE.context(), INFORMATION_PROPOSE_URI));

        assertEquals(ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), failure.getCode());
        assertEquals(2, llm.calls);
        assertEquals(1, warningMessages("negotiation_llm_retry ").size());
        assertTrue(warningMessages("negotiation_llm_retry ").get(0).contains("max_attempts=2"));
        assertEquals(1, warningMessages("negotiation_llm_retry_exhausted").size());
    }

    /**
     * IT-B-006 (3): the clamped attempt limits of the unified LLM config drive the retry loop: the value 99 clamps to
     * 10 with a warning and allows nine failed attempts before a success, and the value 0 clamps to 1 with a warning
     * and fails fast.
     */
    @Test
    void clampedConfigValuesDriveTheRetryLoop() {
        LlmConfig large = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "99"));
        assertEquals(10, large.maxAttempts());
        assertTrue(
                warningMessagesOf(llmConfigAppender).stream()
                        .anyMatch(message -> message.contains("raw_value=99") && message.contains("clamped_value=10")),
                "the clamp of the value 99 must be warned about");

        GoldenCase goldenCase = GoldenCase.INFORMATION_PROPOSE;
        ScriptedExtractionClient recovering = new ScriptedExtractionClient(
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                extractionJson(goldenCase.content(ZH_CN), goldenCase.performative()));
        NegotiationGenerationOrchestrator recoveringOrchestrator = orchestrator(ZH_CN, recovering, large.maxAttempts());

        MetadataContent result = recoveringOrchestrator.generateProposeFromText(
                "请提供接入端口名称。", goldenCase.context(), goldenCase.template());

        assertEquals(10, recovering.calls, "nine failed attempts plus one success must consume the clamped limit");
        assertEquals(goldenCase.goldenText(ZH_CN), result.promptText());
        assertEquals(9, warningMessages("negotiation_llm_retry ").size());

        LlmConfig small = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "0"));
        assertEquals(1, small.maxAttempts());
        assertTrue(
                warningMessagesOf(llmConfigAppender).stream()
                        .anyMatch(message -> message.contains("raw_value=0") && message.contains("clamped_value=1")),
                "the clamp of the value 0 must be warned about");

        ScriptedExtractionClient failing = new ScriptedExtractionClient("");
        NegotiationGenerationOrchestrator failingOrchestrator = orchestrator(ZH_CN, failing, small.maxAttempts());
        NegotiationGenerationException failure = assertThrows(
                NegotiationGenerationException.class,
                () -> failingOrchestrator.generateProposeFromText(
                        "请提供接入端口名称。", goldenCase.context(), goldenCase.template()));

        assertEquals(ErrorCatalog.LLM_RESPONSE_INVALID.getCode(), failure.getCode());
        assertEquals(1, failing.calls);
    }

    /**
     * IT-B-007: with no language configured the unified config resolves the default en-US, and the whole chain works
     * against the real en-US resources: one extraction call for the from-text generation, prompt resources that are
     * really loaded from the classpath, and a second single LLM call for the validation pipeline.
     */
    @Test
    void defaultEnglishConfigurationWorksEndToEnd(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=local_rule
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """);
        A2ATConfig config = A2ATConfig.load(envFile);
        assertEquals(EN_US, config.prompt().language(), "the default language without A2AT_LANGUAGE is en-US");
        assertEquals(3, config.llm().maxAttempts(), "the default attempt limit is 3");

        GoldenCase goldenCase = GoldenCase.INFORMATION_PROPOSE;
        String inputText = "Please provide the access port name and the complaint category of the private line.";
        Map<String, Object> bizSchema = Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "accessPort", Map.of("type", "string"),
                        "bizScenario", Map.of("type", "string")),
                "required",
                List.of("accessPort", "bizScenario"));
        ScriptedExtractionClient llm = new ScriptedExtractionClient(
                extractionJson(goldenCase.content(EN_US), goldenCase.performative()),
                "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"accessPort\":\"P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1\","
                        + "\"bizScenario\":\"dedicated-line quality degradation\"}}");
        NegotiationGenerationOrchestrator orchestrator =
                orchestrator(config.prompt().language(), llm, config.llm().maxAttempts());

        MetadataContent message =
                orchestrator.generateProposeFromText(inputText, goldenCase.context(), goldenCase.template());

        assertEquals(1, llm.calls, "the en-US from-text chain needs exactly one extraction call");
        assertEquals(goldenCase.goldenText(EN_US), message.promptText());
        assertEquals(2, llm.lastMessages.size());
        assertFalse(llm.lastMessages.get(0).get("content").isBlank(), "the en-US system prompt must be loadable");
        assertTrue(llm.lastMessages.get(1).get("content").contains(inputText));
        assertTrue(
                llm.lastMessages.get(1).get("content").contains("propose"),
                "the user prompt must carry the phase token");

        FilledParamData filled = orchestrator.validateProposePromptAndDataFilling(
                message.promptText(), goldenCase.context(), bizSchema, goldenCase.template());

        assertEquals(2, llm.calls, "the validation pipeline adds exactly one semantic validation call");
        assertEquals(goldenCase.context().id(), filled.data().get("id"));
        assertEquals(goldenCase.context().round(), filled.data().get("round"));
        assertEquals(goldenCase.context().maxRounds(), filled.data().get("maxRounds"));
        assertEquals(
                "P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1", filled.data().get("accessPort"));
        assertEquals("dedicated-line quality degradation", filled.data().get("bizScenario"));
    }

    /**
     * IT-B-008: the negotiation context of the generated message comes from the caller-supplied context: the extraction
     * schema sent to the LLM has no context fields and the metadata carries exactly the caller's context.
     */
    @Test
    void contextIsInjectedFromTheCallerWithoutAnyLlmInvolvement() {
        NegotiationContext context =
                new NegotiationContext(NegotiationGoldenCases.SESSION_ID, 4, 7, NegotiationPerformative.PROPOSE);
        ScriptedExtractionClient llm = new ScriptedExtractionClient(
                extractionJson(GoldenCase.INFORMATION_PROPOSE.content(ZH_CN), NegotiationPerformative.PROPOSE));
        NegotiationGenerationOrchestrator orchestrator = orchestrator(ZH_CN, llm, 3);

        MetadataContent result = orchestrator.generateProposeFromText("请提供接入端口名称。", context, INFORMATION_PROPOSE_URI);

        assertEquals(1, llm.calls);
        Map<String, Object> schemaProperties = schemaProperties(llm.lastSchema);
        assertFalse(schemaProperties.containsKey("id"), "the extraction schema must not ask for the context id");
        assertFalse(schemaProperties.containsKey("round"), "the extraction schema must not ask for the round");
        assertFalse(schemaProperties.containsKey("maxRounds"), "the extraction schema must not ask for the limit");
        assertEquals(
                context.withPerformative(NegotiationPerformative.PROPOSE),
                result.negotiationContext(),
                "the caller context travels in the metadata, stamped with the addressed performative");
        assertFalse(
                result.promptText().contains("- id: " + NegotiationGoldenCases.SESSION_ID),
                "the context lines must not be rendered into the message");
    }

    private static NegotiationGenerationOrchestrator orchestrator(
            String language, LLMClient llmClient, int maxAttempts) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .llmClient(llmClient)
                .maxAttempts(maxAttempts)
                .build();
    }

    private static MetadataContent generateFromText(
            NegotiationGenerationOrchestrator orchestrator, GoldenCase goldenCase, String text) {
        return switch (goldenCase.performative()) {
            case PROPOSE -> orchestrator.generateProposeFromText(text, goldenCase.context(), goldenCase.template());
            case ACCEPT -> orchestrator.generateAcceptFromText(text, goldenCase.context(), goldenCase.template());
            case REJECT -> orchestrator.generateRejectFromText(text, goldenCase.context(), goldenCase.template());
            case ABORT -> orchestrator.generateAbortFromText(text, goldenCase.context(), goldenCase.template());
        };
    }

    private static String phaseToken(NegotiationPerformative performative) {
        return switch (performative) {
            case PROPOSE -> "propose";
            case ACCEPT -> "accept";
            case REJECT -> "reject";
            case ABORT -> "abort";
        };
    }

    private static NegotiationTemplateLoader missingTemplateLoader() {
        return new NegotiationTemplateLoader() {
            @Override
            public PromptTemplate load(NegotiationReference reference) {
                throw new ResourceNotFoundException("Negotiation template does not exist.", reference.uri());
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperties(Map<String, Object> jsonSchema) {
        return (Map<String, Object>) jsonSchema.get("properties");
    }

    private static List<String> logMessages(ListAppender<ILoggingEvent> appender, Level level) {
        List<String> messages = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            if (event.getLevel() == level) {
                messages.add(event.getFormattedMessage());
            }
        }
        return messages;
    }

    private static List<String> warningMessagesOf(ListAppender<ILoggingEvent> appender) {
        return logMessages(appender, Level.WARN);
    }

    private List<String> warningMessages(String prefix) {
        List<String> matches = new ArrayList<>();
        for (String message : warningMessagesOf(orchestratorAppender)) {
            if (message.contains(prefix)) {
                matches.add(message);
            }
        }
        return matches;
    }

    /**
     * Builds the snake_case extraction JSON of one typed content so the scripted LLM response maps back to exactly that
     * content.
     */
    private static String extractionJson(NegotiationContent content, NegotiationPerformative performative) {
        if (performative == NegotiationPerformative.PROPOSE) {
            if (content instanceof InformationProposeContent info) {
                return "{\"items\":" + itemsJson(info.items()) + ",\"relationship\":"
                        + stringOrNull(info.relationship()) + "}";
            }
            if (content instanceof TargetProposeContent target) {
                return "{\"target_negotiation_description\":"
                        + quote(target.targetNegotiationDescription())
                        + ",\"intent_understanding\":"
                        + itemsJson(target.intentUnderstanding())
                        + ",\"alignment_and_clarification\":"
                        + itemsJson(target.alignmentAndClarification())
                        + ",\"request_for_clarification\":"
                        + itemsJson(target.requestForClarification())
                        + ",\"target_confirm_request\":"
                        + stringOrNull(target.targetConfirmRequest())
                        + "}";
            }
            FeasibilityProposeContent feasibility = (FeasibilityProposeContent) content;
            return "{\"feasibility_negotiation_description\":"
                    + quote(feasibility.feasibilityNegotiationDescription())
                    + ",\"action\":\""
                    + feasibility.action().name()
                    + "\",\"contents_to_evaluate\":"
                    + itemsJson(feasibility.contentsToEvaluate())
                    + ",\"infeasibility_details_and_proposal\":"
                    + itemsJson(feasibility.infeasibilityDetailsAndProposal())
                    + ",\"feasibility_confirm_request\":"
                    + stringOrNull(feasibility.feasibilityConfirmRequest())
                    + "}";
        }
        if (content instanceof NegotiationAbortContent abort) {
            return "{\"termination_reason\":" + quote(abort.terminationReason()) + "}";
        }
        if (content instanceof InformationEndingContent info) {
            return "{\"conclusion\":" + quote(info.conclusion().literal()) + ",\"items\":" + itemsJson(info.items())
                    + "}";
        }
        if (content instanceof TargetEndingContent target) {
            return "{\"conclusion\":"
                    + quote(target.conclusion().literal())
                    + ",\"confirmed_intent\":"
                    + stringOrNull(target.confirmedIntent())
                    + ",\"failure_reason\":"
                    + stringOrNull(target.failureReason())
                    + "}";
        }
        FeasibilityEndingContent feasibility = (FeasibilityEndingContent) content;
        return "{\"conclusion\":"
                + quote(feasibility.conclusion().literal())
                + ",\"feasibility_summary\":"
                + stringOrNull(feasibility.feasibilitySummary())
                + "}";
    }

    private static String itemsJson(List<NegotiationItem> items) {
        if (items == null) {
            return "null";
        }
        List<String> entries = new ArrayList<>();
        for (NegotiationItem item : items) {
            entries.add("{\"name\":" + quote(item.name()) + ",\"value\":" + stringOrNull(item.value()) + "}");
        }
        return "[" + String.join(",", entries) + "]";
    }

    private static String stringOrNull(String value) {
        return value == null ? "null" : quote(value);
    }

    private static String quote(String value) {
        return "\""
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                + "\"";
    }

    /**
     * Scripted LLM client: returns the queued payloads in order, throws the queued failure, and repeats the last entry
     * once the queue is exhausted. Every call records the request for later assertions.
     */
    private static final class ScriptedExtractionClient implements LLMClient {

        private final List<Object> script;

        private int calls;

        private List<Map<String, String>> lastMessages;

        private Map<String, Object> lastSchema;

        private ScriptedExtractionClient(Object... entries) {
            this.script = List.of(entries);
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            Object next = script.get(Math.min(calls, script.size() - 1));
            calls++;
            lastMessages = messages;
            lastSchema = jsonSchema;
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            return new LLMResponse(
                    (String) next, "scripted-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }
}
