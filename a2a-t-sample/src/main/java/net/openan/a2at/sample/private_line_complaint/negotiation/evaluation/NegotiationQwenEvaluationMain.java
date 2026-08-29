package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.openan.a2at.sample.negotiation.shared.InformationNegotiationSchemas;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleEnvironment;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleFlow;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATParamExtractionError;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * Runs a selected manually labelled case set against a configured real LLM and writes a JSON report.
 *
 * <p>Each case runs the full negotiation protocol chain (mirroring NegotiationEvalApp steps 3-7): the server
 * generates the propose; the client validates the inbound propose and discovers the fields it is asked to
 * supplement; the client generates the accept/reject from that discovery (fromData channel fills exactly the
 * discovered fields, fromText channel voices the ending case's natural language); the server validates the ending
 * message. The fifth argument selects the generation channel: {@code fromText} (default) or {@code fromData}.
 */
public final class NegotiationQwenEvaluationMain {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private NegotiationQwenEvaluationMain() {
    }

    public static void main(String[] args) throws IOException {
        Path envPath = args.length > 0 ? Path.of(args[0]) : NegotiationSampleEnvironment.defaultEnvPath("client");
        Path reportPath = args.length > 1 ? Path.of(args[1]) : Path.of("a2a-t-sample", "target", "negotiation-qwen-report.json");
        Path processLogPath = args.length > 2 ? Path.of(args[2]) : defaultProcessLogPath(reportPath);
        String caseSet = args.length > 3 ? args[3].trim() : "full";
        String channel = args.length > 4 ? args[4].trim() : "fromText";
        if (!"fromText".equals(channel) && !"fromData".equals(channel)) {
            throw new IllegalArgumentException("Channel must be fromText or fromData: " + channel);
        }
        List<NegotiationEvaluationFlowCase> testCases = loadCases(caseSet);
        Map<String, String> environment = NegotiationSampleEnvironment.read(envPath);
        requireQwenConfiguration(environment);

        A2ATClient client = new A2ATClient(envPath);
        A2ATServer server = new A2ATServer(envPath);
        List<Map<String, Object>> results = new ArrayList<>();
        String runId = UUID.randomUUID().toString();
        try (NegotiationEvaluationProcessLogger processLogger = new NegotiationEvaluationProcessLogger(OBJECT_MAPPER, processLogPath)) {
            processLogger.write(runStartedEvent(runId, environment, envPath));
            for (NegotiationEvaluationFlowCase testCase : testCases) {
                results.add(runCase(client, server, testCase, channel, runId, processLogger));
            }
        }

        long passed = results.stream().filter(result -> Boolean.TRUE.equals(result.get("passed"))).count();
        long proposeSucceeded = results.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("propose_succeeded")))
                .count();
        long endingSucceeded = results.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("ending_succeeded")))
                .count();
        long goldenMatched = results.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("golden_matched")))
                .count();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", Instant.now().toString());
        report.put("run_id", runId);
        report.put("model", environment.get("A2AT_LLM_MODEL"));
        report.put("base_url", environment.get("A2AT_LLM_BASE_URL"));
        report.put("git_revision", gitRevision());
        report.put("case_set", caseSet);
        report.put("channel", channel);
        report.put("case_ids", testCases.stream().map(NegotiationEvaluationFlowCase::id).toList());
        report.put("total", results.size());
        report.put("propose_succeeded", proposeSucceeded);
        report.put("ending_succeeded", endingSucceeded);
        report.put("passed", passed);
        report.put("end_to_end_success_rate", (double) passed / results.size());
        report.put("golden_matched", goldenMatched);
        report.put("golden_exact_match_rate", (double) goldenMatched / results.size());
        report.put("note", "A flow passes when all four generation/validation API calls return successfully"
                + ("fromData".equals(channel)
                        ? "; the fromData channel additionally requires an exact completedPrompt match because its rendering is deterministic"
                        : "")
                + ". Golden exact matching is an auxiliary diagnostic because semantically equivalent natural-language values may differ in wording.");
        report.put("process_log", processLogPath.toAbsolutePath().toString());
        report.put("cases", results);
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        OBJECT_MAPPER.writeValue(reportPath.toFile(), report);
        System.out.printf(
                "Qwen evaluation complete: %d/%d passed; report=%s; process-log=%s%n",
                passed, results.size(), reportPath.toAbsolutePath(), processLogPath.toAbsolutePath());
    }

    private static Map<String, Object> runCase(
            A2ATClient client,
            A2ATServer server,
            NegotiationEvaluationFlowCase testCase,
            String channel,
            String runId,
            NegotiationEvaluationProcessLogger processLogger) throws IOException {
        long startedAt = System.nanoTime();
        boolean fromData = "fromData".equals(channel);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", testCase.id());
        result.put("decision", testCase.decision());
        result.put("category", testCase.category());
        result.put("channel", channel);
        result.put("propose_case_id", testCase.proposeCase().id());
        result.put("ending_case_id", testCase.endingCase().id());
        result.put("propose_input", fromData ? testCase.proposeCase().data() : testCase.proposeCase().text());
        result.put("expected_propose", testCase.expectedPropose());
        result.put("expected_ending", testCase.expectedEnding());
        NegotiationContext context =
                new NegotiationContext(UUID.randomUUID().toString(), 1, 3, NegotiationPerformative.PROPOSE);
        result.put("client_requested_fields", null);
        result.put("ending_input", null);
        result.put("actual_propose", null);
        result.put("actual_ending", null);
        result.put("propose_generation_succeeded", false);
        result.put("propose_validation_succeeded", false);
        result.put("ending_generation_succeeded", false);
        result.put("ending_validation_succeeded", false);
        result.put("propose_succeeded", false);
        result.put("ending_succeeded", false);
        result.put("golden_matched", false);
        List<Map<String, Object>> apiTrace = new ArrayList<>();
        result.put("api_trace", apiTrace);
        try {
            // protocol chain (mirrors NegotiationEvalApp steps 3-7): the server proposes, the client validates the
            // inbound propose and discovers the fields to fill, then generates the accept/reject from that discovery;
            // the server finally validates the ending message
            MetadataContent propose = generateProposeWithLog(
                    server, testCase, context, channel, runId, processLogger, apiTrace);
            result.put("propose_generation_succeeded", true);
            result.put("generated_propose_prompt", propose.promptText());
            boolean proposeGoldenExact = propose.promptText().equals(testCase.proposeCase().completedPrompt());
            result.put("propose_golden_exact", proposeGoldenExact);
            FilledParamData proposeFilled = validateProposeWithLog(
                    client, testCase, propose.promptText(), context, channel, runId, processLogger, apiTrace);
            result.put("propose_validation_succeeded", true);
            result.put("actual_propose", proposeFilled.data());
            List<String> requestedFields = requestedFields(proposeFilled, testCase.proposeCase());
            result.put("client_requested_fields", requestedFields);
            result.put("ending_input", fromData ? requestedFields : testCase.endingCase().text());
            boolean proposeMatched = expectedValuesMatch(testCase.expectedPropose(), proposeFilled.data());
            result.put("propose_expected_matched", proposeMatched);
            result.put("propose_context_matched", contextMatches(context, proposeFilled.data()));
            result.put("propose_succeeded", true);

            NegotiationPerformative endingPerformative =
                    "accept".equals(testCase.decision()) ? NegotiationPerformative.ACCEPT : NegotiationPerformative.REJECT;
            NegotiationContext responseContext = NegotiationSampleFlow.contextFrom(proposeFilled.data(), endingPerformative);
            MetadataContent ending = generateEndingWithLog(
                    client, testCase, requestedFields, responseContext, channel, runId, processLogger, apiTrace);
            result.put("ending_generation_succeeded", true);
            result.put("generated_ending_prompt", ending.promptText());
            boolean endingGoldenExact = ending.promptText().equals(testCase.endingCase().completedPrompt());
            result.put("ending_golden_exact", endingGoldenExact);
            FilledParamData endingFilled = validateEndingWithLog(
                    server, testCase, ending.promptText(), responseContext, channel, runId, processLogger, apiTrace);
            result.put("ending_validation_succeeded", true);
            result.put("actual_ending", endingFilled.data());
            boolean endingMatched = expectedValuesMatch(testCase.expectedEnding(), endingFilled.data());
            result.put("ending_expected_matched", endingMatched);
            result.put("ending_context_matched", contextMatches(responseContext, endingFilled.data()));
            result.put("ending_succeeded", true);
            boolean goldenExact = proposeGoldenExact && endingGoldenExact;
            result.put("golden_matched", proposeMatched && endingMatched && (!fromData || goldenExact));
            // fromData rendering is deterministic, so a golden mismatch is a real defect, not wording variance
            result.put("passed", !fromData || goldenExact);
        } catch (RuntimeException exception) {
            result.put("passed", false);
            result.put("error", errorDetails(exception));
        }
        result.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
        return result;
    }

    private static MetadataContent generateProposeWithLog(
            A2ATServer server,
            NegotiationEvaluationFlowCase testCase,
            NegotiationContext context,
            String channel,
            String runId,
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace) throws IOException {
        long startedAt = System.nanoTime();
        NegotiationEvaluationCase proposeCase = testCase.proposeCase();
        boolean fromData = "fromData".equals(channel);
        Map<String, Object> request = fromData
                ? Map.of("data", proposeCase.data(), "template_uri", NegotiationSampleFlow.PROPOSE_TEMPLATE_URI)
                : Map.of("text", proposeCase.text(), "template_uri", NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
        try {
            MetadataContent content = fromData
                    ? server.generateNegotiationProposePromptFromData(
                            new NegotiationProposeData(
                                    context,
                                    new InformationProposeContent(proposeCase.dataItems(), proposeCase.dataRelationship())),
                            NegotiationSampleFlow.PROPOSE_TEMPLATE_URI)
                    : server.generateNegotiationProposePromptFromText(
                            proposeCase.text(), context, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "generate_propose", "propose", channel, context, request, Map.of(
                    "prompt", content.promptText(),
                    "template_uri", content.templateUri(),
                    "extension_uri", content.extensionUri()), startedAt, null));
            return content;
        } catch (RuntimeException exception) {
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "generate_propose", "propose", channel, context, request, null, startedAt, exception));
            throw exception;
        }
    }

    private static FilledParamData validateProposeWithLog(
            A2ATClient client,
            NegotiationEvaluationFlowCase testCase,
            String prompt,
            NegotiationContext context,
            String channel,
            String runId,
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace) throws IOException {
        long startedAt = System.nanoTime();
        Map<String, Object> schema = InformationNegotiationSchemas.propose();
        try {
            FilledParamData filled = client.validateProposePromptAndDataFilling(
                    prompt, context, schema, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "validate_propose_and_fill", "propose", channel, null, Map.of(
                    "prompt", prompt,
                    "schema", schema,
                    "template_uri", NegotiationSampleFlow.PROPOSE_TEMPLATE_URI), Map.of("filled_data", filled.data()), startedAt, null));
            return filled;
        } catch (RuntimeException exception) {
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "validate_propose_and_fill", "propose", channel, null, Map.of(
                    "prompt", prompt,
                    "schema", schema,
                    "template_uri", NegotiationSampleFlow.PROPOSE_TEMPLATE_URI), null, startedAt, exception));
            throw exception;
        }
    }

    /**
     * Generates the client's accept/reject. The client fills exactly the fields it discovered from the inbound
     * propose (step 2 output), taking each value/reason from the ending case.
     */
    private static MetadataContent generateEndingWithLog(
            A2ATClient client,
            NegotiationEvaluationFlowCase testCase,
            List<String> requestedFields,
            NegotiationContext context,
            String channel,
            String runId,
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace) throws IOException {
        long startedAt = System.nanoTime();
        boolean fromData = "fromData".equals(channel);
        boolean accept = "accept".equals(testCase.decision());
        NegotiationEvaluationCase endingCase = testCase.endingCase();
        List<NegotiationItem> endingItems = filledItems(requestedFields, endingCase);
        // the fromText channel voices the supplement explicitly (mirroring NegotiationEvalApp step 6): terse corpus
        // texts may paraphrase or omit the field names, so the LLM extraction needs the discovered fields spelled out
        String endingInputText = clientSupplementText(endingItems, accept) + "\n\n" + endingCase.text();
        Map<String, Object> request = fromData
                ? Map.of("data", Map.of(
                        "items", itemsJson(endingItems),
                        "conclusion", accept ? NegotiationConclusion.ACCEPT.toString() : NegotiationConclusion.REJECT.toString()),
                        "template_uri", NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                : Map.of("text", endingInputText,
                        "template_uri", NegotiationSampleFlow.ENDING_TEMPLATE_URI);
        try {
            MetadataContent content = fromData
                    ? accept
                            ? client.generateNegotiationAcceptPromptFromData(
                                    new NegotiationEndingData(
                                            context, new InformationEndingContent(NegotiationConclusion.ACCEPT, endingItems)),
                                    NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                            : client.generateNegotiationRejectPromptFromData(
                                    new NegotiationEndingData(
                                            context, new InformationEndingContent(NegotiationConclusion.REJECT, endingItems)),
                                    NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                    : accept
                            ? client.generateNegotiationAcceptPromptFromText(
                                    endingInputText, context, NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                            : client.generateNegotiationRejectPromptFromText(
                                    endingInputText, context, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "generate_" + testCase.decision(), testCase.decision(), channel, context,
                    request, Map.of("prompt", content.promptText(), "template_uri", content.templateUri(),
                            "extension_uri", content.extensionUri()), startedAt, null));
            return content;
        } catch (RuntimeException exception) {
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "generate_" + testCase.decision(), testCase.decision(), channel, context,
                    request, null, startedAt, exception));
            throw exception;
        }
    }

    private static FilledParamData validateEndingWithLog(
            A2ATServer server,
            NegotiationEvaluationFlowCase testCase,
            String prompt,
            NegotiationContext context,
            String channel,
            String runId,
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace) throws IOException {
        long startedAt = System.nanoTime();
        Map<String, Object> schema = "accept".equals(testCase.decision())
                ? InformationNegotiationSchemas.accept()
                : InformationNegotiationSchemas.reject();
        try {
            FilledParamData filled = "accept".equals(testCase.decision())
                    ? server.validateAcceptPromptAndDataFilling(
                            prompt, context, schema, NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                    : server.validateRejectPromptAndDataFilling(
                            prompt, context, schema, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "validate_" + testCase.decision() + "_and_fill",
                    testCase.decision(), channel, null, Map.of("prompt", prompt, "schema", schema,
                            "template_uri", NegotiationSampleFlow.ENDING_TEMPLATE_URI),
                    Map.of("filled_data", filled.data()), startedAt, null));
            return filled;
        } catch (RuntimeException exception) {
            writeStage(processLogger, apiTrace, stageEvent(runId, testCase, "validate_" + testCase.decision() + "_and_fill",
                    testCase.decision(), channel, null, Map.of("prompt", prompt, "schema", schema,
                            "template_uri", NegotiationSampleFlow.ENDING_TEMPLATE_URI), null, startedAt, exception));
            throw exception;
        }
    }

    /**
     * Field names the client discovered from the inbound propose (step 2 output); falls back to the propose case's
     * structured items when the extraction carried none.
     */
    private static List<String> requestedFields(FilledParamData filled, NegotiationEvaluationCase proposeCase) {
        List<String> names = new ArrayList<>();
        Object raw = filled.data() == null ? null : filled.data().get("items");
        if (raw instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> item && item.get("name") != null) {
                    names.add(String.valueOf(item.get("name")));
                }
            }
        }
        if (names.isEmpty()) {
            proposeCase.dataItems().forEach(item -> names.add(item.name()));
        }
        return names;
    }

    /**
     * The client fills exactly the fields it discovered from the inbound propose, taking each value (accept) or
     * reason (reject) from the ending case's structured items.
     */
    private static List<NegotiationItem> filledItems(List<String> requestedFields, NegotiationEvaluationCase endingCase) {
        Map<String, String> byName = new LinkedHashMap<>();
        for (NegotiationItem item : endingCase.dataItems()) {
            byName.put(item.name(), item.value());
        }
        List<NegotiationItem> items = new ArrayList<>();
        for (String field : requestedFields) {
            String value = byName.get(field);
            if (value == null) {
                return endingCase.dataItems();
            }
            items.add(new NegotiationItem(field, value));
        }
        return items;
    }

    private static List<Map<String, String>> itemsJson(List<NegotiationItem> items) {
        List<Map<String, String>> json = new ArrayList<>();
        for (NegotiationItem item : items) {
            json.add(Map.of("name", item.name(), "value", String.valueOf(item.value())));
        }
        return json;
    }

    /** The client's explicit supplement for the fields it discovered from the inbound propose. */
    private static String clientSupplementText(List<NegotiationItem> items, boolean accept) {
        StringBuilder text = new StringBuilder("## 客户端补充信息\n");
        for (int index = 0; index < items.size(); index++) {
            NegotiationItem item = items.get(index);
            if (index > 0) {
                text.append('\n');
            }
            text.append(index + 1).append(". ").append(item.name()).append('：');
            if (accept) {
                text.append(item.value());
            } else {
                text.append("无法提供，原因：").append(item.value());
            }
        }
        return text.toString();
    }

    private static Map<String, Object> stageEvent(
            String runId,
            NegotiationEvaluationFlowCase testCase,
            String stage,
            String phase,
            String channel,
            NegotiationContext context,
            Map<String, Object> request,
            Map<String, Object> response,
            long startedAt,
            RuntimeException exception) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("run_id", runId);
        event.put("case_id", testCase.id());
        int step = stageStep(stage);
        event.put("step", step);
        event.put("step_label", step + "/4");
        event.put("api", stageApi(stage, channel));
        event.put("caller", stageCaller(stage));
        event.put("phase", phase);
        event.put("channel", channel);
        event.put("decision", testCase.decision());
        event.put("stage", stage);
        event.put("expected", "propose".equals(phase) ? testCase.expectedPropose() : testCase.expectedEnding());
        if (context != null) {
            event.put("context", Map.of("id", context.id(), "round", context.round(), "max_rounds", context.maxRounds()));
        }
        event.put("request", request);
        if (response != null) {
            event.put("response", response);
        }
        event.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
        event.put("outcome", exception == null ? "success" : "failure");
        if (exception != null) {
            event.put("error", errorDetails(exception));
        }
        return event;
    }

    private static void writeStage(
            NegotiationEvaluationProcessLogger processLogger,
            List<Map<String, Object>> apiTrace,
            Map<String, Object> event) throws IOException {
        processLogger.write(event);
        apiTrace.add(event);
    }

    private static int stageStep(String stage) {
        if ("generate_propose".equals(stage)) {
            return 1;
        }
        if ("validate_propose_and_fill".equals(stage)) {
            return 2;
        }
        return stage.startsWith("generate_") ? 3 : 4;
    }

    private static String stageApi(String stage, String channel) {
        String api = switch (stage) {
            case "generate_propose" -> "A2ATServer.generateNegotiationProposePromptFromText";
            case "validate_propose_and_fill" -> "A2ATClient.validateProposePromptAndDataFilling";
            case "generate_accept" -> "A2ATClient.generateNegotiationAcceptPromptFromText";
            case "validate_accept_and_fill" -> "A2ATServer.validateAcceptPromptAndDataFilling";
            case "generate_reject" -> "A2ATClient.generateNegotiationRejectPromptFromText";
            case "validate_reject_and_fill" -> "A2ATServer.validateRejectPromptAndDataFilling";
            default -> stage;
        };
        return "fromData".equals(channel) ? api.replace("FromText", "FromData") : api;
    }

    private static String stageCaller(String stage) {
        if ("generate_propose".equals(stage)) {
            return "server";
        }
        return stage.startsWith("validate_propose") || stage.startsWith("generate_") ? "client" : "server";
    }

    private static Map<String, Object> runStartedEvent(String runId, Map<String, String> environment, Path envPath) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("run_id", runId);
        event.put("stage", "run_started");
        event.put("env_path", envPath.toAbsolutePath().toString());
        event.put("model", environment.get("A2AT_LLM_MODEL"));
        event.put("base_url", environment.get("A2AT_LLM_BASE_URL"));
        return event;
    }

    private static Map<String, Object> errorDetails(RuntimeException exception) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("class", exception.getClass().getName());
        error.put("message", exception.getMessage());
        if (exception instanceof A2ATError a2atError) {
            error.put("code", a2atError.getCode());
        }
        if (exception instanceof A2ATParamExtractionError extractionError) {
            error.put("slot_errors", extractionError.getErrors());
        }
        error.put("cause_chain", causeChain(exception));
        error.put("stack_trace", Arrays.stream(exception.getStackTrace()).limit(20).map(StackTraceElement::toString).toList());
        return error;
    }

    private static List<String> causeChain(Throwable exception) {
        List<String> causes = new ArrayList<>();
        Throwable current = exception;
        while (current != null) {
            causes.add(current.getClass().getName() + ": " + current.getMessage());
            current = current.getCause();
        }
        return causes;
    }

    private static List<String> parseCaseIds(String argument) {
        List<String> caseIds = Arrays.stream(argument.split(","))
                .map(String::trim)
                .filter(caseId -> !caseId.isEmpty())
                .toList();
        if (caseIds.isEmpty()) {
            throw new IllegalArgumentException("The fourth argument must contain at least one comma-separated case ID");
        }
        return caseIds;
    }

    private static List<NegotiationEvaluationFlowCase> loadCases(String selector) {
        return switch (selector.toLowerCase(java.util.Locale.ROOT)) {
            case "", "full" -> NegotiationEvaluationCaseLoader.loadFlows();
            case "smoke" -> NegotiationEvaluationCaseLoader.loadSmokeFlows();
            default -> NegotiationEvaluationCaseLoader.loadSelectedFlows(parseCaseIds(selector));
        };
    }

    private static String gitRevision() {
        String configuredRevision = System.getProperty("a2at.sample.gitRevision");
        if (configuredRevision != null && !configuredRevision.isBlank()) {
            return configuredRevision;
        }
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            String revision;
            try (var input = process.getInputStream()) {
                revision = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            return process.waitFor() == 0 && !revision.isBlank() ? revision : "unknown";
        } catch (IOException exception) {
            return "unknown";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    private static Path defaultProcessLogPath(Path reportPath) {
        String filename = reportPath.getFileName().toString();
        int extensionStart = filename.lastIndexOf('.');
        String logFilename = (extensionStart < 0 ? filename : filename.substring(0, extensionStart)) + "-process.jsonl";
        Path parent = reportPath.getParent();
        return parent == null ? Path.of(logFilename) : parent.resolve(logFilename);
    }

    /**
     * Matches the labelled expected values against the extracted items of the shared information-negotiation
     * schemas ({@code items: [{name, requirement|value|reason}]}). Bidirectional containment after whitespace
     * removal: the extracted payload may carry the full line text while the label records only the value or reason.
     */
    private static boolean expectedValuesMatch(Map<String, Object> expected, Map<String, Object> actual) {
        Map<String, Object> actualByName = itemsByName(actual);
        return expected.entrySet().stream().allMatch(entry -> {
            Object value = actualByName.get(entry.getKey());
            return value != null && valuesTransport(String.valueOf(entry.getValue()), String.valueOf(value));
        });
    }

    private static Map<String, Object> itemsByName(Map<String, Object> actual) {
        Map<String, Object> byName = new LinkedHashMap<>();
        Object raw = actual.get("items");
        if (raw instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> item && item.get("name") != null) {
                    Object payload = item.get("requirement");
                    if (payload == null) {
                        payload = item.get("value");
                    }
                    if (payload == null) {
                        payload = item.get("reason");
                    }
                    byName.put(String.valueOf(item.get("name")), payload);
                }
            }
        }
        return byName;
    }

    private static boolean valuesTransport(String expected, String actual) {
        String expectedText = expected.replaceAll("\\s+", "");
        String actualText = actual.replaceAll("\\s+", "");
        return !expectedText.isEmpty() && (actualText.contains(expectedText) || expectedText.contains(actualText));
    }

    private static boolean contextMatches(NegotiationContext context, Map<String, Object> actual) {
        return context.id().equals(actual.get("id"))
                && numberEquals(context.round(), actual.get("round"))
                && numberEquals(context.maxRounds(), actual.get("maxRounds"));
    }

    private static boolean numberEquals(int expected, Object actual) {
        return actual instanceof Number number && expected == number.intValue();
    }

    private static void requireQwenConfiguration(Map<String, String> environment) {
        if (!"openai".equals(environment.get("A2AT_LLM_PROVIDER")) || environment.get("A2AT_LLM_BASE_URL") == null) {
            throw new IllegalArgumentException("Qwen evaluation requires A2AT_LLM_PROVIDER=openai and A2AT_LLM_BASE_URL in the supplied env file");
        }
    }
}
