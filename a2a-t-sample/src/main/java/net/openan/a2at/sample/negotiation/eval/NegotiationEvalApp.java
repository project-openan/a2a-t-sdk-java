package net.openan.a2at.sample.negotiation.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import net.openan.a2at.sample.subscribe_incident.shared.mock.SampleMockLlmInstaller;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * Closed-loop evaluator for the negotiation prompt interfaces of the private-line-complaint scenario.
 *
 * <p>Each case of the bundled suite ({@code sample/negotiation/eval/eval-suite.json}) is driven through the
 * negotiation interfaces in message order, exactly the way the negotiation demo wires them:
 *
 * <ol>
 *   <li>Task-T generation — the client value from the Task-T extension metadata is either structured JSON
 *       ({@code generateTaskPromptFromDataWithSchema}: every data key maps to one slot value, the schema is passed
 *       as a call parameter) or natural language ({@code generateTaskPromptFromText}, LLM slot extraction);
 *   <li>server validation — {@code validateTaskPromptAndDataFilling} extracts the parameters against the passed
 *       JSON schema; the missing-slot set (blank slots plus semantic {@code missing_required} errors) decides
 *       whether negotiation is triggered;
 *   <li>propose generation — for the detected missing slots, {@code generateNegotiationProposePromptFromData} /
 *       {@code FromText} renders the Negotiation-T information-propose message;
 *   <li>outbound propose validation — the server self-checks the generated propose via
 *       {@code validateProposePromptAndDataFilling} before sending it (expected to pass);
 *   <li>inbound propose validation — the client validates the received propose through the symmetric client-side
 *       API {@code validateProposePromptAndDataFilling} and reads out which slots it is asked to supplement;
 *       the fill round is driven by this client-side view;
 *   <li>client fill + accept — the client merges the round-1 parameters with its fill values, RE-RENDERS the full
 *       Task-T prompt from the complete parameter map (there is no incremental merge API; the supplement is
 *       incorporated by re-generating the whole prompt) and renders the Negotiation-T accept;
 *   <li>inbound accept validation — {@code validateAcceptPromptAndDataFilling} checks the accept message and extracts
 *       the supplemented values (expected to pass with the values the client sent);
 *   <li>second Task-T validation — {@code validateTaskPromptAndDataFilling} re-validates the filled Task-T prompt;
 *       no missing slot means the negotiation completed the flow (COMPLETED), any rejection keeps it in
 *       INPUT_REQUIRED.
 * </ol>
 *
 * <p>Every intermediate artifact is recorded per case in a JSON report: the generated Task-T prompt, both
 * negotiation messages, each validation verdict with extracted parameters and per-slot errors, the client's fill
 * values — and, for every SDK call, an {@code api} entry carrying the exact method name plus its input parameters
 * (including the full JSON schema passed to generate/validate), so a reviewer can verify from the report alone
 * which interface ran with which arguments. A single case can be replayed and audited without re-running it. The
 * report is rewritten after every case, so an interrupted run keeps the cases already finished.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * java @a2a-t-sample/target/eval.javaargs.txt [--out eval-report.json] [--case PLC-04] /path/to/.env
 * }</pre>
 *
 * @since 2026-08
 */
public final class NegotiationEvalApp {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private NegotiationEvalApp() {}

    /**
     * Entry point.
     *
     * @param args options ({@code --out <path>} report file, {@code --case <id>} repeatable case filter), then the
     *     {@code .env} path carrying a real LLM API key
     */
    public static void main(String[] args) {
        Path envPath = null;
        Path outPath = Path.of("eval-report.json");
        List<String> caseFilter = new ArrayList<>();
        String negotiationChannelOverride = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--out".equals(arg) && i + 1 < args.length) {
                outPath = Path.of(args[++i]);
            } else if ("--case".equals(arg) && i + 1 < args.length) {
                caseFilter.add(args[++i]);
            } else if ("--negotiation-channel".equals(arg) && i + 1 < args.length) {
                negotiationChannelOverride = args[++i];
            } else if (!arg.startsWith("--")) {
                envPath = Path.of(arg);
            }
        }
        if (envPath == null) {
            System.err.println(
                    "Usage: java @a2a-t-sample/target/eval.javaargs.txt [--out eval-report.json] [--case PLC-04]"
                            + " [--negotiation-channel fromData|fromText] /path/to/.env");
            System.exit(1);
        }

        SampleMockLlmInstaller.installLlmLogger(false, "eval");
        Map<String, Object> suite = loadSuite();
        if (negotiationChannelOverride != null) {
            // run the whole suite with one negotiation generation channel without duplicating cases
            for (Map<String, Object> testCase : cases(suite)) {
                testCase.put("negotiation_channel", negotiationChannelOverride);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", suite.get("suite"));
        report.put("description", suite.get("description"));
        report.put("scenario", suite.get("scenario"));
        report.put("generated_at", LocalDateTime.now().format(TIMESTAMP));
        report.put("llm", llmInfo(envPath));
        report.put("case_filter", caseFilter);
        report.put("negotiation_channel", negotiationChannelOverride == null
                ? "per-case"
                : negotiationChannelOverride);

        List<Map<String, Object>> cases = new ArrayList<>();
        report.put("cases", cases);
        int index = 0;
        int total = countCases(suite, caseFilter);
        for (Map<String, Object> testCase : cases(suite)) {
            String caseId = String.valueOf(testCase.get("id"));
            if (!caseFilter.isEmpty() && !caseFilter.contains(caseId)) {
                continue;
            }
            index++;
            emit("\n[eval] ===== [" + index + "/" + total + "] case " + caseId + ": " + testCase.get("intent")
                    + " =====");
            long startNanos = System.nanoTime();
            Map<String, Object> record = runCase(envPath, suite, testCase);
            record.put("duration_seconds", round1((System.nanoTime() - startNanos) / 1_000_000_000.0));
            cases.add(record);
            report.put("metrics", metrics(cases));
            writeReport(report, outPath);
        }

        report.put("metrics", metrics(cases));
        writeReport(report, outPath);
        Map<String, Object> metrics = metrics(cases);
        emit("\n[eval] ===== Negotiation interface correctness metrics =====");
        emit("[eval] cases: " + metrics.get("cases"));
        for (String key : List.of("trigger_accuracy", "missing_slot_exact_match", "propose_outbound_valid",
                "accept_fill_value_match", "closed_loop_completion")) {
            emit("[eval] " + metrics.get(key));
        }
        emit("[eval] report written to " + outPath.toAbsolutePath());
    }

    // -- one case: the negotiation interface closed loop with full evidence capture --

    /** Runs one case end to end and returns its replayable trace record. */
    private static Map<String, Object> runCase(Path envPath, Map<String, Object> suite, Map<String, Object> testCase) {
        String caseId = String.valueOf(testCase.get("id"));
        String channel = String.valueOf(testCase.get("channel"));
        boolean fromText = "fromText".equals(channel);
        String negotiationChannel = String.valueOf(testCase.getOrDefault("negotiation_channel", "fromData"));
        boolean negotiationFromText = "fromText".equals(negotiationChannel);
        String inputText = String.valueOf(testCase.get("input_text"));
        Map<String, Object> inputData = asMap(testCase.get("input_data"));

        TemplateUri taskTemplate = parseTemplate(String.valueOf(suite.get("template_uri")));
        Map<String, Object> taskSchema = asMap(suite.get("task_schema"));
        Map<String, String> phrasing = stringMap(suite.get("negotiation_phrasing"));
        Map<String, Object> properties = asMap(taskSchema.get("properties"));
        Set<String> requiredSlots = new TreeSet<>(stringList(taskSchema.get("required")));
        Map<String, String> fillValues = mergeFills(stringMap(suite.get("client_fill_values")),
                stringMap(testCase.get("fill_override")));

        Map<String, Object> expect = asMap(testCase.get("expect"));
        boolean expectNegotiation = Boolean.TRUE.equals(expect.get("negotiation_needed"));
        Set<String> expectMissing = new TreeSet<>(stringList(expect.get("missing_slots")));
        Boolean expectFillCompletes = (Boolean) expect.get("fill_completes");
        boolean expectGenerationFails = Boolean.TRUE.equals(expect.get("generation_fails"));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("case", caseId);
        record.put("channel", channel);
        record.put("negotiation_channel", negotiationChannel);
        record.put("intent", testCase.get("intent"));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("fromText", fromText);
        if (fromText) {
            input.put("text", inputText);
        } else {
            input.put("data", inputData);
        }
        record.put("input", input);
        record.put("task_schema", taskSchema);
        Map<String, Object> expectJson = new LinkedHashMap<>();
        expectJson.put("negotiation_needed", expectNegotiation);
        expectJson.put("missing_slots", new ArrayList<>(expectMissing));
        expectJson.put("fill_completes", expectFillCompletes);
        expectJson.put("generation_fails", expectGenerationFails);
        record.put("expect", expectJson);
        List<Map<String, Object>> steps = new ArrayList<>();
        record.put("steps", steps);

        // -- step 1: Task-T prompt generation from the extension metadata value --
        Map<String, Object> taskGenerationInput = new LinkedHashMap<>();
        if (fromText) {
            taskGenerationInput.put("text", inputText);
        } else {
            taskGenerationInput.put("data", inputData);
            taskGenerationInput.put("schema", taskSchema);
        }
        taskGenerationInput.put("template_uri", taskTemplate.uri());
        String taskGenerationApi = fromText
                ? "A2ATClient.generateTaskPromptFromText"
                : "A2ATClient.generateTaskPromptFromDataWithSchema";
        String taskPrompt;
        try {
            long nanos = System.nanoTime();
            A2ATClient client = new A2ATClient(envPath);
            MetadataContent taskContent = fromText
                    ? client.generateTaskPromptFromText(inputText, taskTemplate)
                    : client.generateTaskPromptFromDataWithSchema(inputData, taskSchema, taskTemplate);
            taskPrompt = taskContent.promptText();
            Map<String, Object> step = step("1. Task-T generation (" + channel + ")", "client");
            api(step, apiCall(taskGenerationApi, taskGenerationInput));
            step.put("generated_prompt", taskPrompt);
            step.put("template_uri", taskContent.templateUri());
            steps.add(step);
            emit("[eval]   [1] Task-T prompt generated (" + channel + ", " + secs(nanos) + "s)");
        } catch (A2ATError error) {
            steps.add(errorStep("1. Task-T generation (" + channel + ")", "task_generation", error,
                    apiCall(taskGenerationApi, taskGenerationInput)));
            emit("[eval]   [1] Task-T generation FAILED: " + error.getCode() + " " + error.getMessage());
            if (expectGenerationFails) {
                // generation-time fail-fast is the expected outcome (e.g. a required slot missing from the
                // natural-language input): the task never exists, so negotiation cannot and must not trigger
                Map<String, Object> verdict = new LinkedHashMap<>();
                verdict.put("trigger_correct", !expectNegotiation);
                verdict.put("slots_correct", expectMissing.isEmpty());
                verdict.put("propose_outbound_valid", null);
                verdict.put("accept_fill_value_match", null);
                verdict.put("fill_completes_correct", null);
                verdict.put("pass", !expectNegotiation && expectMissing.isEmpty());
                verdict.put("reason", "generation failed fast as expected: " + error.getCode() + " "
                        + error.getMessage());
                record.put("verdict", verdict);
                emit("[eval]   verdict: PASS (generation fail-fast as expected)");
                return record;
            }
            return finish(record, steps, null, null, null, null, null, expectNegotiation, expectMissing,
                    expectFillCompletes);
        }

        // -- step 2: server validation of the Task-T prompt -> missing-slot detection --
        Map<String, Object> taskValidationInput = Map.of(
                "prompt", taskPrompt,
                "schema", taskSchema,
                "template_uri", taskTemplate.uri());
        Set<String> actualMissing;
        Map<String, Object> extractedRound1 = new LinkedHashMap<>();
        try {
            long nanos = System.nanoTime();
            A2ATServer server = new A2ATServer(envPath);
            FilledParamData params = server.validateTaskPromptAndDataFilling(taskPrompt, taskSchema, taskTemplate);
            actualMissing = requiredMissing(blankSlots(params), requiredSlots);
            if (params.data() != null) {
                extractedRound1.putAll(params.data());
            }
            Map<String, Object> step = stepOf(
                    "2. server Task-T validation (missing-slot detection)",
                    "server",
                    validationJson("passed", null, null, params, actualMissing));
            api(step, apiCall("A2ATServer.validateTaskPromptAndDataFilling", taskValidationInput));
            steps.add(step);
            emit("[eval]   [2] validation passed, missing slots: " + actualMissing + " (" + secs(nanos) + "s)");
        } catch (ContentValidationException error) {
            actualMissing = requiredMissing(errorSlots(error), requiredSlots);
            Map<String, Object> step = stepOf(
                    "2. server Task-T validation (missing-slot detection)",
                    "server",
                    validationJson("rejected", error.getCode(), error.errors(), null, actualMissing));
            api(step, apiCall("A2ATServer.validateTaskPromptAndDataFilling", taskValidationInput));
            steps.add(step);
            emit("[eval]   [2] validation rejected (" + error.getCode() + "), missing slots: " + actualMissing);
        }

        boolean negotiationTriggered = !actualMissing.isEmpty();
        if (!negotiationTriggered) {
            Map<String, Object> done = step("3. no negotiation needed -> flow completes directly", "server");
            done.put("task_state", "COMPLETED");
            steps.add(done);
            emit("[eval]   [3] no missing parameters -> COMPLETED without negotiation");
            return finish(record, steps, negotiationTriggered, actualMissing, null, null, null, expectNegotiation,
                    expectMissing, expectFillCompletes);
        }

        // -- step 3: Negotiation-T propose generation from the detected missing slots --
        List<NegotiationItem> missingItems = new ArrayList<>();
        for (String slot : actualMissing) {
            missingItems.add(new NegotiationItem(slot, missingHint(phrasing, properties, slot)));
        }
        NegotiationContext proposeContext = new NegotiationContext(
                UUID.randomUUID().toString(), 1, NegotiationContext.DEFAULT_MAX_ROUNDS);
        String proposeInputText = itemsToProposeText(missingItems, phrasing);
        Map<String, Object> proposeGenerationInput = new LinkedHashMap<>();
        if (negotiationFromText) {
            proposeGenerationInput.put("text", proposeInputText);
            proposeGenerationInput.put("context", contextJson(proposeContext));
        } else {
            proposeGenerationInput.put("data", Map.of(
                    "context", contextJson(proposeContext),
                    "items", itemsJson(missingItems),
                    "relationship", String.valueOf(phrasing.get("propose_relationship"))));
        }
        proposeGenerationInput.put("template_uri", DemoTemplates.NEGOTIATION_PROPOSE.uri());
        String proposeGenerationApi = negotiationFromText
                ? "A2ATServer.generateNegotiationProposePromptFromText"
                : "A2ATServer.generateNegotiationProposePromptFromData";
        String proposePrompt;
        try {
            A2ATServer server = new A2ATServer(envPath);
            MetadataContent propose = negotiationFromText
                    ? server.generateNegotiationProposePromptFromText(
                            proposeInputText, proposeContext, DemoTemplates.NEGOTIATION_PROPOSE)
                    : server.generateNegotiationProposePromptFromData(
                            new NegotiationProposeData(
                                    proposeContext,
                                    new InformationProposeContent(missingItems, phrasing.get("propose_relationship"))),
                            DemoTemplates.NEGOTIATION_PROPOSE);
            proposePrompt = propose.promptText();
            Map<String, Object> step =
                    step("3. Negotiation-T propose generation (" + negotiationChannel + ")", "server");
            api(step, apiCall(proposeGenerationApi, proposeGenerationInput));
            step.put("negotiation_items", itemsJson(missingItems));
            if (negotiationFromText) {
                step.put("input_text", proposeInputText);
            }
            step.put("generated_prompt", proposePrompt);
            step.put("template_uri", propose.templateUri());
            step.put("task_state", "INPUT_REQUIRED");
            steps.add(step);
            emit("[eval]   [3] propose rendered for " + actualMissing + " (" + negotiationChannel
                    + ") -> INPUT_REQUIRED");
        } catch (A2ATError error) {
            steps.add(errorStep("3. Negotiation-T propose generation (" + negotiationChannel + ")",
                    "propose_generation", error, apiCall(proposeGenerationApi, proposeGenerationInput)));
            emit("[eval]   [3] propose generation FAILED: " + error.getCode() + " " + error.getMessage());
            return finish(record, steps, negotiationTriggered, actualMissing, null, null, null, expectNegotiation,
                    expectMissing, expectFillCompletes);
        }

        // -- step 4: outbound validation of the generated propose --
        Map<String, Object> proposeValidationInput = Map.of(
                "prompt", proposePrompt,
                "context", contextJson(proposeContext),
                "schema", negotiationSchema(actualMissing, taskSchema),
                "template_uri", DemoTemplates.NEGOTIATION_PROPOSE.uri());
        Boolean proposeValid = null;
        try {
            long nanos = System.nanoTime();
            A2ATServer server = new A2ATServer(envPath);
            FilledParamData proposeParams = server.validateProposePromptAndDataFilling(
                    proposePrompt, proposeContext, negotiationSchema(actualMissing, taskSchema),
                    DemoTemplates.NEGOTIATION_PROPOSE);
            proposeValid = true;
            Map<String, Object> step = stepOf(
                    "4. outbound propose validation",
                    "server",
                    validationJson("passed", null, null, proposeParams, Set.of()));
            api(step, apiCall("A2ATServer.validateProposePromptAndDataFilling", proposeValidationInput));
            steps.add(step);
            emit("[eval]   [4] outbound propose validation passed (" + secs(nanos) + "s)");
        } catch (A2ATError error) {
            proposeValid = false;
            List<SlotValidationError> slotErrors = error instanceof
                    net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException extraction
                    ? extraction.getErrors()
                    : List.of();
            Map<String, Object> step = stepOf(
                    "4. outbound propose validation",
                    "server",
                    validationJson("rejected", error.getCode(), slotErrors, null, Set.of()));
            api(step, apiCall("A2ATServer.validateProposePromptAndDataFilling", proposeValidationInput));
            steps.add(step);
            emit("[eval]   [4] outbound propose validation REJECTED: " + error.getCode() + " " + error.getMessage());
        }

        // -- step 5: client validates the INBOUND propose and reads out the requested slots --
        // The SDK exposes the validate* API symmetrically on client and server: the receiving client re-validates
        // the negotiation message it got and extracts which slots the server asked it to supplement. The fill round
        // below is driven by this client-side view (not by the server's internal missing-slot set), so the report
        // shows what a real client would learn from the propose message alone.
        Set<String> clientRequestedSlots = new TreeSet<>();
        try {
            long nanos = System.nanoTime();
            A2ATClient client = new A2ATClient(envPath);
            FilledParamData requested = client.validateProposePromptAndDataFilling(
                    proposePrompt, proposeContext, negotiationSchema(actualMissing, taskSchema),
                    DemoTemplates.NEGOTIATION_PROPOSE);
            if (requested.data() != null) {
                for (String key : requested.data().keySet()) {
                    // the SDK merges the negotiation context (id/round/maxRounds) into the extracted data; only the
                    // real slot names tell the client what to supplement
                    if (!"id".equals(key) && !"round".equals(key) && !"maxRounds".equals(key)) {
                        clientRequestedSlots.add(key);
                    }
                }
            }
            Map<String, Object> step = stepOf(
                    "5. client inbound propose validation (requested-slot extraction)",
                    "client",
                    validationJson("passed", null, null, requested, Set.of()));
            api(step, apiCall("A2ATClient.validateProposePromptAndDataFilling", Map.of(
                    "prompt", proposePrompt,
                    "context", contextJson(proposeContext),
                    "schema", negotiationSchema(actualMissing, taskSchema),
                    "template_uri", DemoTemplates.NEGOTIATION_PROPOSE.uri())));
            step.put("client_requested_slots", new ArrayList<>(clientRequestedSlots));
            steps.add(step);
            emit("[eval]   [5] client extracted requested slots: " + clientRequestedSlots + " (" + secs(nanos)
                    + "s)");
        } catch (A2ATError error) {
            List<SlotValidationError> slotErrors = error instanceof
                    net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException extraction
                    ? extraction.getErrors()
                    : List.of();
            Map<String, Object> step = stepOf(
                    "5. client inbound propose validation (requested-slot extraction)",
                    "client",
                    validationJson("rejected", error.getCode(), slotErrors, null, Set.of()));
            api(step, apiCall("A2ATClient.validateProposePromptAndDataFilling", Map.of(
                    "prompt", proposePrompt,
                    "context", contextJson(proposeContext),
                    "schema", negotiationSchema(actualMissing, taskSchema),
                    "template_uri", DemoTemplates.NEGOTIATION_PROPOSE.uri())));
            step.put("client_requested_slots", List.of());
            steps.add(step);
            emit("[eval]   [5] client propose validation REJECTED: " + error.getCode() + " " + error.getMessage()
                    + " -> falling back to the server-side missing-slot set for the fill");
        }
        Set<String> slotsToFill = clientRequestedSlots.isEmpty() ? actualMissing : clientRequestedSlots;

        // -- step 6: client fills the requested slots and renders the Negotiation-T accept --
        // the filled Task-T prompt is RE-GENERATED from the complete parameter map, exactly like the demo renders
        // message 3's Task-T from the filled params — not text-appended. There is no incremental merge API: the
        // client holds the full parameter set (the fromData input, or for fromText the parameters the server
        // extracted in round 1), completed with the fill values for everything still missing, and re-renders the
        // whole Task-T prompt from that map.
        Map<String, String> fills = fills(slotsToFill, fillValues);
        Map<String, Object> baseData = fromText ? extractedRound1 : inputData;
        Map<String, Object> filledData = mergeInputData(baseData, taskSchema, mergeFills(fillValues, fills));
        String filledPrompt;
        Map<String, Object> filledGenerationInput = Map.of(
                "data", filledData,
                "schema", taskSchema,
                "template_uri", taskTemplate.uri());
        try {
            A2ATClient client = new A2ATClient(envPath);
            filledPrompt = client.generateTaskPromptFromDataWithSchema(filledData, taskSchema, taskTemplate)
                    .promptText();
        } catch (A2ATError error) {
            // a fill value violates a slot constraint: fromData generation fails fast — the closed loop cannot
            // complete, which is the expected negative outcome for constraint-violating fills
            filledPrompt = appendSupplements(taskPrompt, fills);
            Map<String, Object> step = step("6. client fill + Negotiation-T accept generation (fromData)", "client");
            api(step, apiCall("A2ATClient.generateTaskPromptFromDataWithSchema", filledGenerationInput));
            step.put("client_fill", fills);
            step.put("fill_generation_rejected", Map.of(
                    "code", error.getCode(),
                    "message", String.valueOf(error.getMessage())));
            steps.add(step);
            emit("[eval]   [6] filled Task-T generation REJECTED (" + error.getCode() + "): "
                    + error.getMessage());
            return finish(record, steps, negotiationTriggered, actualMissing, proposeValid, null, false,
                    expectNegotiation, expectMissing, expectFillCompletes);
        }
        String acceptPrompt;
        NegotiationContext acceptContext = new NegotiationContext(
                UUID.randomUUID().toString(), 1, NegotiationContext.DEFAULT_MAX_ROUNDS);
        String acceptInputText = itemsToAcceptText(filledItems(fills), phrasing);
        Map<String, Object> acceptGenerationInput = new LinkedHashMap<>();
        if (negotiationFromText) {
            acceptGenerationInput.put("text", acceptInputText);
            acceptGenerationInput.put("context", contextJson(acceptContext));
        } else {
            acceptGenerationInput.put("data", Map.of(
                    "context", contextJson(acceptContext),
                    "conclusion", NegotiationConclusion.ACCEPT.toString(),
                    "items", itemsJson(filledItems(fills))));
        }
        acceptGenerationInput.put("template_uri", DemoTemplates.NEGOTIATION_ACCEPT.uri());
        String acceptGenerationApi = negotiationFromText
                ? "A2ATClient.generateNegotiationAcceptPromptFromText"
                : "A2ATClient.generateNegotiationAcceptPromptFromData";
        try {
            A2ATClient client = new A2ATClient(envPath);
            List<NegotiationItem> filledItems = filledItems(fills);
            MetadataContent accept = negotiationFromText
                    ? client.generateNegotiationAcceptPromptFromText(acceptInputText, acceptContext,
                            DemoTemplates.NEGOTIATION_ACCEPT)
                    : client.generateNegotiationAcceptPromptFromData(
                            new NegotiationEndingData(
                                    acceptContext,
                                    new InformationEndingContent(NegotiationConclusion.ACCEPT, filledItems)),
                            DemoTemplates.NEGOTIATION_ACCEPT);
            acceptPrompt = accept.promptText();
            Map<String, Object> step =
                    step("6. client fill + Negotiation-T accept generation (" + negotiationChannel + ")", "client");
            api(step,
                    apiCall("A2ATClient.generateTaskPromptFromDataWithSchema", filledGenerationInput),
                    apiCall(acceptGenerationApi, acceptGenerationInput));
            step.put("client_fill", fills);
            step.put("filled_task_prompt", filledPrompt);
            step.put("filled_params", filledData);
            if (negotiationFromText) {
                step.put("input_text", acceptInputText);
            }
            step.put("generated_prompt", acceptPrompt);
            step.put("template_uri", accept.templateUri());
            steps.add(step);
            emit("[eval]   [6] client fills " + fills.keySet() + ", accept rendered (" + negotiationChannel + ")");
        } catch (A2ATError error) {
            steps.add(errorStep("6. client fill + Negotiation-T accept generation (" + negotiationChannel + ")",
                    "accept_generation", error, apiCall(acceptGenerationApi, acceptGenerationInput)));
            emit("[eval]   [6] accept generation FAILED: " + error.getCode() + " " + error.getMessage());
            return finish(record, steps, negotiationTriggered, actualMissing, proposeValid, null, null,
                    expectNegotiation, expectMissing, expectFillCompletes);
        }

        // -- step 7: inbound validation of the accept message; extracted values must match the client fill --
        Map<String, Object> acceptValidationInput = Map.of(
                "prompt", acceptPrompt,
                "context", contextJson(acceptContext),
                "schema", negotiationSchema(slotsToFill, taskSchema),
                "template_uri", DemoTemplates.NEGOTIATION_ACCEPT.uri());
        Boolean acceptValid = null;
        Boolean fillValueMatch = null;
        Map<String, Object> extractedAcceptParams = null;
        try {
            long nanos = System.nanoTime();
            A2ATServer server = new A2ATServer(envPath);
            FilledParamData acceptParams = server.validateAcceptPromptAndDataFilling(
                    acceptPrompt, acceptContext, negotiationSchema(slotsToFill, taskSchema),
                    DemoTemplates.NEGOTIATION_ACCEPT);
            acceptValid = true;
            extractedAcceptParams = acceptParams.data() == null ? Map.of() : new LinkedHashMap<>(acceptParams.data());
            fillValueMatch = fillValuesMatch(extractedAcceptParams, fills);
            Map<String, Object> validation = validationJson("passed", null, null, acceptParams, Set.of());
            validation.put("expected_fill", fills);
            validation.put("fill_value_match", fillValueMatch);
            Map<String, Object> step = stepOf("7. inbound accept validation", "server", validation);
            api(step, apiCall("A2ATServer.validateAcceptPromptAndDataFilling", acceptValidationInput));
            steps.add(step);
            emit("[eval]   [7] accept validation passed, fill value match: " + fillValueMatch + " (" + secs(nanos)
                    + "s)");
        } catch (A2ATError error) {
            acceptValid = false;
            fillValueMatch = false;
            List<SlotValidationError> slotErrors = error instanceof
                    net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException extraction
                    ? extraction.getErrors()
                    : List.of();
            Map<String, Object> validation = validationJson("rejected", error.getCode(), slotErrors, null, Set.of());
            validation.put("expected_fill", fills);
            validation.put("fill_value_match", false);
            Map<String, Object> step = stepOf("7. inbound accept validation", "server", validation);
            api(step, apiCall("A2ATServer.validateAcceptPromptAndDataFilling", acceptValidationInput));
            steps.add(step);
            emit("[eval]   [7] accept validation REJECTED: " + error.getCode() + " " + error.getMessage());
        }

        // -- step 8: second Task-T validation of the filled prompt -> COMPLETED or stays INPUT_REQUIRED --
        Map<String, Object> secondValidationInput = Map.of(
                "prompt", filledPrompt,
                "schema", taskSchema,
                "template_uri", taskTemplate.uri());
        Boolean fillCompleted = null;
        try {
            long nanos = System.nanoTime();
            A2ATServer server = new A2ATServer(envPath);
            FilledParamData filled = server.validateTaskPromptAndDataFilling(filledPrompt, taskSchema, taskTemplate);
            Set<String> stillMissing = requiredMissing(blankSlots(filled), requiredSlots);
            fillCompleted = stillMissing.isEmpty();
            Map<String, Object> step = stepOf(
                    "8. second Task-T validation (fill round)",
                    "server",
                    validationJson(fillCompleted ? "passed" : "rejected",
                            fillCompleted ? null : "slots_still_missing",
                            fillCompleted ? null : stillMissingErrors(stillMissing),
                            filled,
                            stillMissing));
            api(step, apiCall("A2ATServer.validateTaskPromptAndDataFilling", secondValidationInput));
            step.put("task_state", fillCompleted ? "COMPLETED" : "INPUT_REQUIRED");
            steps.add(step);
            emit("[eval]   [8] second Task-T validation " + (fillCompleted ? "-> COMPLETED" : "still missing: "
                    + stillMissing) + " (" + secs(nanos) + "s)");
        } catch (ContentValidationException error) {
            fillCompleted = false;
            Map<String, Object> step = stepOf(
                    "8. second Task-T validation (fill round)",
                    "server",
                    validationJson("rejected", error.getCode(), error.errors(), null, errorSlots(error)));
            api(step, apiCall("A2ATServer.validateTaskPromptAndDataFilling", secondValidationInput));
            steps.add(step);
            emit("[eval]   [8] second Task-T validation REJECTED (" + error.getCode() + "): " + errorSlots(error));
        }

        return finish(record, steps, negotiationTriggered, actualMissing, proposeValid, fillValueMatch,
                fillCompleted, expectNegotiation, expectMissing, expectFillCompletes);
    }

    // -- record assembly --

    private static Map<String, Object> finish(
            Map<String, Object> record,
            List<Map<String, Object>> steps,
            Boolean triggered,
            Set<String> actualMissing,
            Boolean proposeValid,
            Boolean fillValueMatch,
            Boolean fillCompleted,
            boolean expectNegotiation,
            Set<String> expectMissing,
            Boolean expectFillCompletes) {
        boolean error = triggered == null;
        boolean triggerCorrect = !error && triggered == expectNegotiation;
        boolean slotsCorrect = !error && actualMissing != null && actualMissing.equals(expectMissing);
        Boolean proposeValidCorrect = error || proposeValid == null ? null : proposeValid;
        // value match is only meaningful for cases whose fill round is expected to succeed; a negative fill case
        // (expectFillCompletes=false) is judged by the fill round NOT completing — the accept validation rejecting
        // the constraint-violating fill value is exactly the expected outcome, not a mismatch
        Boolean fillMatchCorrect =
                error || fillValueMatch == null || Boolean.FALSE.equals(expectFillCompletes) ? null : fillValueMatch;
        Boolean fillCorrect;
        if (error) {
            fillCorrect = false;
        } else if (expectFillCompletes == null) {
            fillCorrect = null;
        } else if (Boolean.TRUE.equals(triggered)) {
            fillCorrect = expectFillCompletes.equals(fillCompleted);
        } else {
            fillCorrect = !expectFillCompletes;
        }
        boolean pass = triggerCorrect && slotsCorrect
                && (proposeValidCorrect == null || proposeValidCorrect)
                && (fillMatchCorrect == null || fillMatchCorrect)
                && (fillCorrect == null || fillCorrect);

        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("negotiation_triggered", triggered);
        actual.put("missing_slots", actualMissing == null ? null : new ArrayList<>(actualMissing));
        actual.put("propose_outbound_valid", proposeValid);
        actual.put("accept_fill_value_match", fillValueMatch);
        actual.put("closed_loop_completed", fillCompleted);
        record.put("actual", actual);

        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("trigger_correct", triggerCorrect);
        verdict.put("slots_correct", slotsCorrect);
        verdict.put("propose_outbound_valid", proposeValidCorrect);
        verdict.put("accept_fill_value_match", fillMatchCorrect);
        verdict.put("fill_completes_correct", fillCorrect);
        verdict.put("pass", pass);
        verdict.put("reason", reason(error, triggerCorrect, slotsCorrect, fillMatchCorrect, fillCorrect,
                actualMissing, expectMissing, triggered, expectNegotiation, proposeValid));
        record.put("verdict", verdict);

        emit("[eval]   verdict: " + (pass ? "PASS" : "FAIL")
                + " | trigger=" + triggerCorrect + " slots=" + slotsCorrect
                + " proposeValid=" + proposeValidCorrect + " fillMatch=" + fillMatchCorrect + " fill=" + fillCorrect);
        return record;
    }

    private static String reason(
            boolean error,
            boolean triggerCorrect,
            boolean slotsCorrect,
            Boolean fillMatchCorrect,
            Boolean fillCorrect,
            Set<String> actualMissing,
            Set<String> expectMissing,
            Boolean triggered,
            boolean expectNegotiation,
            Boolean proposeValid) {
        if (error) {
            return "evaluation aborted by an error (see steps)";
        }
        List<String> notes = new ArrayList<>();
        if (!triggerCorrect) {
            notes.add("negotiation expected=" + expectNegotiation + " actual=" + triggered);
        }
        if (!slotsCorrect && actualMissing != null) {
            Set<String> missed = new TreeSet<>(expectMissing);
            missed.removeAll(actualMissing);
            Set<String> extra = new TreeSet<>(actualMissing);
            extra.removeAll(expectMissing);
            notes.add("missing-slot set mismatch: expected=" + expectMissing + " actual=" + actualMissing
                    + (missed.isEmpty() ? "" : " (not reported: " + missed + ")")
                    + (extra.isEmpty() ? "" : " (unexpected: " + extra + ")"));
        }
        if (proposeValid != null && !proposeValid) {
            notes.add("generated propose failed outbound validation (step 4)");
        }
        if (fillMatchCorrect != null && !fillMatchCorrect) {
            notes.add("server-side extracted fill values differ from what the client sent (step 7)");
        }
        if (fillCorrect != null && !fillCorrect) {
            notes.add("fill round did not complete as expected (step 8)");
        }
        return String.join("; ", notes);
    }

    // -- trace step builders --

    private static Map<String, Object> step(String name, String role) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("step", name);
        step.put("role", role);
        return step;
    }

    /** Attaches the SDK API call evidence (method name + input parameters) to a step; one step may make several calls. */
    @SafeVarargs
    private static void api(Map<String, Object> step, Map<String, Object>... calls) {
        step.put("api_calls", List.of(calls));
    }

    /** One SDK API invocation record: the exact facade method plus the parameters it was called with. */
    private static Map<String, Object> apiCall(String method, Map<String, Object> input) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("method", method);
        call.put("input", input);
        return call;
    }

    /** Negotiation context as report JSON (the same object handed to the generate/validate calls). */
    private static Map<String, Object> contextJson(NegotiationContext context) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", context.id());
        json.put("round", context.round());
        json.put("maxRounds", context.maxRounds());
        return json;
    }

    private static Map<String, Object> stepOf(String name, String role, Map<String, Object> validation) {
        Map<String, Object> step = step(name, role);
        step.put("validation", validation);
        return step;
    }

    private static Map<String, Object> validationJson(
            String outcome,
            String errorCode,
            List<SlotValidationError> slotErrors,
            FilledParamData params,
            Set<String> missingSlots) {
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("outcome", outcome);
        if (errorCode != null) {
            validation.put("error_code", errorCode);
        }
        if (slotErrors != null && !slotErrors.isEmpty()) {
            List<Map<String, Object>> errors = new ArrayList<>();
            for (SlotValidationError slotError : slotErrors) {
                errors.add(Map.of(
                        "slot", nullToEmpty(slotError.slotName()),
                        "code", nullToEmpty(slotError.code()),
                        "message", nullToEmpty(slotError.message())));
            }
            validation.put("slot_errors", errors);
        }
        if (params != null) {
            validation.put("extracted_params",
                    params.data() == null ? Map.of() : new LinkedHashMap<>(params.data()));
        }
        validation.put("missing_slots", missingSlots == null ? List.of() : new ArrayList<>(missingSlots));
        return validation;
    }

    private static List<SlotValidationError> stillMissingErrors(Set<String> stillMissing) {
        List<SlotValidationError> errors = new ArrayList<>();
        for (String slot : stillMissing) {
            errors.add(new SlotValidationError(slot, "slots_still_missing", "slot still missing after the fill round"));
        }
        return errors;
    }

    private static Map<String, Object> errorStep(
            String name, String stage, A2ATError error, Map<String, Object>... calls) {
        Map<String, Object> step = step(name, "n/a");
        step.put("error", Map.of(
                "stage", stage,
                "code", error.getCode(),
                "message", String.valueOf(error.getMessage())));
        if (calls.length > 0) {
            api(step, calls);
        }
        return step;
    }

    private static List<Map<String, Object>> itemsJson(List<NegotiationItem> items) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (NegotiationItem item : items) {
            list.add(Map.of("name", item.name(), "value", nullToEmpty(item.value())));
        }
        return list;
    }

    // -- negotiation helpers --

    /** Missing-item hint from the phrasing template ({@code {slot}}/{@code {description}} placeholders). */
    private static String missingHint(Map<String, String> phrasing, Map<String, Object> properties, String slot) {
        String template = phrasing.getOrDefault("missing_item_hint", "{slot}");
        Object description = null;
        Object property = properties.get(slot);
        if (property instanceof Map<?, ?> slotSchema
                && slotSchema.get("description") instanceof String text
                && !text.isBlank()) {
            description = text;
        }
        return template.replace("{slot}", slot).replace("{description}", description == null ? "" : String.valueOf(description));
    }

    /**
     * Assembles the natural-language propose input for the fromText negotiation channel, mirroring the demo's
     * {@code FromTextStrategy}: scenario-configured prefix, numbered item list, relationship sentence.
     */
    private static String itemsToProposeText(List<NegotiationItem> items, Map<String, String> phrasing) {
        StringBuilder text = new StringBuilder(phrasing.getOrDefault("from_text_propose_prefix", ""));
        appendNumberedItems(text, items);
        String relationship = phrasing.get("propose_relationship");
        if (relationship != null && !relationship.isBlank()) {
            text.append(relationship);
        }
        return text.toString();
    }

    /**
     * Assembles the natural-language accept input for the fromText negotiation channel: scenario-configured prefix,
     * numbered item list, suffix sentence.
     */
    private static String itemsToAcceptText(List<NegotiationItem> items, Map<String, String> phrasing) {
        StringBuilder text = new StringBuilder(phrasing.getOrDefault("from_text_accept_prefix", ""));
        appendNumberedItems(text, items);
        text.append(phrasing.getOrDefault("from_text_accept_suffix", ""));
        return text.toString();
    }

    /** Generic numbered-list rule: {@code N. name：value；} per item, value omitted when blank. */
    private static void appendNumberedItems(StringBuilder text, List<NegotiationItem> items) {
        for (int i = 0; i < items.size(); i++) {
            NegotiationItem item = items.get(i);
            text.append(i + 1).append(". ").append(item.name());
            if (item.value() != null && !item.value().isBlank()) {
                text.append("：").append(item.value());
            }
            text.append("；");
        }
    }

    /** The supplement values the simulated client supplies, keyed by slot. */
    private static Map<String, String> fills(Set<String> missingSlots, Map<String, String> fillValues) {
        Map<String, String> fills = new LinkedHashMap<>();
        for (String slot : missingSlots) {
            String value = fillValues.get(slot);
            if (value != null && !value.isBlank()) {
                fills.put(slot, value);
            }
        }
        return fills;
    }

    private static Map<String, String> mergeFills(Map<String, String> base, Map<String, String> override) {
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(override);
        return merged;
    }

    private static List<NegotiationItem> filledItems(Map<String, String> fills) {
        List<NegotiationItem> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : fills.entrySet()) {
            items.add(new NegotiationItem(entry.getKey(), entry.getValue()));
        }
        return items;
    }

    /**
     * Builds the complete parameter map for the fill round: every slot declared by the task schema carries either the
     * round-1 input value or the client's fill value, so the filled Task-T prompt can be regenerated from data.
     */
    private static Map<String, Object> mergeInputData(
            Map<String, Object> inputData, Map<String, Object> taskSchema, Map<String, String> fills) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> properties = asMap(taskSchema.get("properties"));
        for (String slot : properties.keySet()) {
            Object base = inputData.get(slot);
            Object value = base == null || (base instanceof String s && s.isBlank()) ? fills.get(slot) : base;
            merged.put(slot, value == null ? "" : value);
        }
        merged.putAll(fills);
        return merged;
    }

    /** Appends the supplement values to a prompt verbatim; fallback when regeneration is impossible. */
    private static String appendSupplements(String taskPrompt, Map<String, String> fills) {
        StringBuilder filled = new StringBuilder(taskPrompt);
        for (Map.Entry<String, String> entry : fills.entrySet()) {
            filled.append(System.lineSeparator()).append(entry.getKey()).append("：").append(entry.getValue());
        }
        return filled.toString();
    }

    /**
     * Parameter schema for the negotiation-message validations (steps 4/5/7): one property per negotiated slot,
     * derived from the task schema so the description and {@code x-a2at-value-constraint} of each slot flow into the
     * validation prompt — the extraction surface is exactly the supplemented information, with the same semantic
     * constraints the Task-T validation enforces.
     */
    private static Map<String, Object> negotiationSchema(Set<String> slots, Map<String, Object> taskSchema) {
        Map<String, Object> taskProperties = asMap(taskSchema.get("properties"));
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String slot : slots) {
            Map<String, Object> property = new LinkedHashMap<>(asMap(taskProperties.get(slot)));
            if (property.isEmpty()) {
                property.put("type", "string");
            }
            properties.put(slot, property);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new ArrayList<>(slots));
        return schema;
    }

    /**
     * Whether every server-extracted fill value matches the value the client sent for that slot. The comparison is
     * containment in either direction after whitespace removal: the extracted value may carry the slot's field label
     * (e.g. 接入端口名称：P781-…) or only the bare value (e.g. P781-…), both of which faithfully transport the fill.
     */
    private static boolean fillValuesMatch(Map<String, Object> extracted, Map<String, String> fills) {
        for (Map.Entry<String, String> entry : fills.entrySet()) {
            Object value = extracted.get(entry.getKey());
            if (value == null) {
                return false;
            }
            String extractedText = normalize(String.valueOf(value));
            String sentText = normalize(entry.getValue());
            if (!extractedText.contains(sentText) && !sentText.contains(extractedText)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    /** Slots whose extracted value is null/blank (negotiation candidates). */
    private static Set<String> blankSlots(FilledParamData params) {
        Set<String> blank = new TreeSet<>();
        if (params != null && params.data() != null) {
            for (Map.Entry<String, Object> entry : params.data().entrySet()) {
                Object value = entry.getValue();
                if (value == null || (value instanceof String s && s.isBlank())) {
                    blank.add(entry.getKey());
                }
            }
        }
        return blank;
    }

    /**
     * Restricts missing-slot candidates to the task schema's required slots: an empty optional slot is a valid
     * state and never a negotiation trigger — only a required slot that is blank (or flagged by the semantic
     * validator) means information the negotiation must ask for.
     */
    private static Set<String> requiredMissing(Set<String> candidates, Set<String> requiredSlots) {
        Set<String> missing = new TreeSet<>(candidates);
        missing.retainAll(requiredSlots);
        return missing;
    }

    /** Slots reported as errors by the semantic validation. */
    private static Set<String> errorSlots(ContentValidationException error) {
        Set<String> slots = new TreeSet<>();
        if (error.errors() != null) {
            for (SlotValidationError validationError : error.errors()) {
                if (validationError.slotName() != null && !validationError.slotName().isBlank()) {
                    slots.add(validationError.slotName());
                }
            }
        }
        return slots;
    }

    // -- metrics --

    private static Map<String, Object> metrics(List<Map<String, Object>> cases) {
        int total = cases.size();
        int triggerOk = 0;
        int slotsOk = 0;
        int proposeEvaluated = 0;
        int proposeOk = 0;
        int fillMatchEvaluated = 0;
        int fillMatchOk = 0;
        int loopEvaluated = 0;
        int loopOk = 0;
        int pass = 0;
        for (Map<String, Object> record : cases) {
            Map<?, ?> verdict = (Map<?, ?>) record.get("verdict");
            if (Boolean.TRUE.equals(verdict.get("trigger_correct"))) {
                triggerOk++;
            }
            if (Boolean.TRUE.equals(verdict.get("slots_correct"))) {
                slotsOk++;
            }
            if (verdict.get("propose_outbound_valid") != null) {
                proposeEvaluated++;
                if (Boolean.TRUE.equals(verdict.get("propose_outbound_valid"))) {
                    proposeOk++;
                }
            }
            if (verdict.get("accept_fill_value_match") != null) {
                fillMatchEvaluated++;
                if (Boolean.TRUE.equals(verdict.get("accept_fill_value_match"))) {
                    fillMatchOk++;
                }
            }
            if (verdict.get("fill_completes_correct") != null) {
                loopEvaluated++;
                if (Boolean.TRUE.equals(verdict.get("fill_completes_correct"))) {
                    loopOk++;
                }
            }
            if (Boolean.TRUE.equals(verdict.get("pass"))) {
                pass++;
            }
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("cases", total);
        metrics.put("trigger_accuracy", ratio("negotiation correctly triggered/held", triggerOk, total));
        metrics.put("missing_slot_exact_match", ratio("missing-slot set exact match", slotsOk, total));
        metrics.put("propose_outbound_valid", ratio("generated propose passes outbound validation", proposeOk,
                proposeEvaluated));
        metrics.put("accept_fill_value_match", ratio("extracted fill values match client fill", fillMatchOk,
                fillMatchEvaluated));
        metrics.put("closed_loop_completion", ratio("closed loop completes as expected", loopOk, loopEvaluated));
        metrics.put("full_pass", ratio("all dimensions pass", pass, total));
        return metrics;
    }

    private static Map<String, Object> ratio(String label, int part, int total) {
        Map<String, Object> ratio = new LinkedHashMap<>();
        ratio.put("label", label);
        ratio.put("passed", part);
        ratio.put("total", total);
        ratio.put("pct", total == 0 ? 0.0 : round1(100.0 * part / total));
        return ratio;
    }

    // -- suite loading and small utilities --

    private static Map<String, Object> loadSuite() {
        try (InputStream stream = NegotiationEvalApp.class
                .getClassLoader()
                .getResourceAsStream("sample/negotiation/eval/eval-suite.json")) {
            if (stream == null) {
                throw new IllegalStateException("Eval suite resource not found");
            }
            return MAPPER.readValue(stream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read eval suite", exception);
        }
    }

    /** LLM run configuration from the env file, without the API key. */
    private static Map<String, Object> llmInfo(Path envPath) {
        Map<String, String> values = readEnv(envPath);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("provider", values.getOrDefault("A2AT_LLM_PROVIDER", ""));
        info.put("model", values.getOrDefault("A2AT_LLM_MODEL", ""));
        info.put("base_url", values.getOrDefault("A2AT_LLM_BASE_URL", ""));
        return info;
    }

    private static Map<String, String> readEnv(Path envPath) {
        Map<String, String> values = new LinkedHashMap<>();
        if (envPath == null || !Files.exists(envPath)) {
            return values;
        }
        try {
            for (String rawLine : Files.readAllLines(envPath)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int separator = line.indexOf('=');
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read env file: " + envPath, exception);
        }
        return values;
    }

    private static void writeReport(Map<String, Object> report, Path outPath) {
        try {
            MAPPER.writeValue(outPath.toFile(), report);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write eval report: " + outPath, exception);
        }
    }

    private static int countCases(Map<String, Object> suite, List<String> filter) {
        if (filter.isEmpty()) {
            return cases(suite).size();
        }
        return (int) cases(suite).stream()
                .map(testCase -> String.valueOf(testCase.get("id")))
                .filter(filter::contains)
                .count();
    }

    private static TemplateUri parseTemplate(String templateUri) {
        return TemplateUri.parse(templateUri)
                .orElseThrow(() -> new IllegalArgumentException("Unparseable template URI: " + templateUri));
    }

    /** Negotiation template URIs used by the closed loop; kept local so the evaluator stays self-contained. */
    private static final class DemoTemplates {

        static final TemplateUri NEGOTIATION_PROPOSE = parseTemplate("Negotiation-T/information-negotiation/propose/v1");

        static final TemplateUri NEGOTIATION_ACCEPT =
                parseTemplate("Negotiation-T/information-negotiation/accept-reject/v1");

        private DemoTemplates() {}
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cases(Map<String, Object> suite) {
        Object raw = suite.get("cases");
        return raw instanceof List<?> list ? (List<Map<String, Object>>) raw : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, val) -> result.put(String.valueOf(key), String.valueOf(val)));
        }
        return result;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String secs(long startNanos) {
        return String.valueOf(round1((System.nanoTime() - startNanos) / 1_000_000_000.0));
    }

    private static void emit(String message) {
        System.out.println(message);
    }
}
