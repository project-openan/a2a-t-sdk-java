package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.openan.a2at.sample.negotiation.shared.InformationNegotiationSchemas;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleEnvironment;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleFlow;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.server.A2ATServer;

/** Runs malformed business-content cases against the configured real LLM. */
public final class NegotiationBusinessAbnormalEvaluationMain {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private NegotiationBusinessAbnormalEvaluationMain() {
    }

    public static void main(String[] args) throws IOException {
        Path envPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("a2a-t-sample", "src", "main", "resources", "sample",
                        "private-line-complaint-negotiation", "qwen.env");
        String suffix = LocalDateTime.now(ZoneId.systemDefault()).format(FILE_TIMESTAMP);
        Path reportPath = args.length > 1
                ? Path.of(args[1])
                : Path.of("a2a-t-sample", "target", "negotiation-business-abnormal-report-" + suffix + ".json");
        Path processLogPath = args.length > 2
                ? Path.of(args[2])
                : Path.of("a2a-t-sample", "target", "negotiation-business-abnormal-process-log-" + suffix + ".jsonl");

        Map<String, String> environment = NegotiationSampleEnvironment.read(envPath);
        requireQwenConfiguration(environment);
        List<NegotiationBusinessAbnormalEvaluationCase> cases =
                NegotiationBusinessAbnormalEvaluationCaseLoader.load();
        String runId = UUID.randomUUID().toString();
        List<Map<String, Object>> results = new ArrayList<>();
        try (NegotiationEvaluationProcessLogger logger =
                new NegotiationEvaluationProcessLogger(OBJECT_MAPPER, processLogPath)) {
            A2ATClient client = new A2ATClient(envPath);
            A2ATServer server = new A2ATServer(envPath);
            logger.write(runStartedEvent(runId, envPath, cases.size()));
            for (NegotiationBusinessAbnormalEvaluationCase testCase : cases) {
                results.add(runCase(client, server, testCase, runId, logger));
            }
        }

        long passed = results.stream().filter(result -> Boolean.TRUE.equals(result.get("passed"))).count();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", Instant.now().toString());
        report.put("run_id", runId);
        report.put("model", environment.get("A2AT_LLM_MODEL"));
        report.put("base_url", environment.get("A2AT_LLM_BASE_URL"));
        report.put("git_revision", gitRevision());
        report.put("total", results.size());
        report.put("passed", passed);
        report.put("failed", results.size() - passed);
        report.put("success_rate", results.isEmpty() ? 1.0 : (double) passed / results.size());
        report.put("definition", "业务结构异常用例要求 SDK 识别无法渲染或无法校验的协商报文；这些用例调用真实 LLM，结果可能受模型输出影响。");
        report.put("process_log", processLogPath.toAbsolutePath().toString());
        report.put("cases", results);
        Path parent = reportPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        OBJECT_MAPPER.writeValue(reportPath.toFile(), report);
        System.out.printf("Business abnormal negotiation evaluation complete: %d/%d matched; report=%s; process-log=%s%n",
                passed, results.size(), reportPath.toAbsolutePath(), processLogPath.toAbsolutePath());
    }

    private static Map<String, Object> runStartedEvent(String runId, Path envPath, int caseCount) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("event_type", "run_started");
        event.put("run_id", runId);
        event.put("env_path", envPath.toAbsolutePath().toString());
        event.put("case_count", caseCount);
        event.put("real_llm", true);
        return event;
    }

    private static Map<String, Object> runCase(
            A2ATClient client,
            A2ATServer server,
            NegotiationBusinessAbnormalEvaluationCase testCase,
            String runId,
            NegotiationEvaluationProcessLogger logger) throws IOException {
        long startedAt = System.nanoTime();
        NegotiationPerformative performative = performativeFor(testCase.api());
        NegotiationContext context = new NegotiationContext(UUID.randomUUID().toString(), 1, 3, performative);
        Map<String, Object> request = requestFor(testCase, context);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", testCase.id());
        result.put("description", testCase.description());
        result.put("api", apiMethod(testCase.api()));
        result.put("expected_codes", testCase.expectedCodes());
        result.put("request", request);
        try {
            Object response = invoke(client, server, testCase, context);
            result.put("response", responseSummary(response));
            result.put("outcome", "unexpected_success");
            result.put("passed", false);
            logger.write(event(runId, testCase, request, responseSummary(response), result, startedAt));
        } catch (RuntimeException exception) {
            Map<String, Object> error = errorDetails(exception);
            boolean matched = testCase.expectedCodes().stream().anyMatch(code -> code.equals(error.get("code")));
            result.put("actual_error", error);
            result.put("outcome", matched ? "expected_business_failure" : "unexpected_failure");
            result.put("passed", matched);
            logger.write(event(runId, testCase, request, null, result, startedAt));
        }
        result.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
        return result;
    }

    private static Object invoke(
            A2ATClient client,
            A2ATServer server,
            NegotiationBusinessAbnormalEvaluationCase testCase,
            NegotiationContext context) {
        return switch (testCase.api()) {
            case "generate_propose" -> client.generateNegotiationProposePromptFromText(
                    testCase.input(), context, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            case "validate_propose" -> server.validateProposePromptAndDataFilling(
                    testCase.input(), context, InformationNegotiationSchemas.propose(),
                    NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            case "generate_accept" -> server.generateNegotiationAcceptPromptFromText(
                    testCase.input(), context, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            case "validate_accept" -> client.validateAcceptPromptAndDataFilling(
                    testCase.input(), context, InformationNegotiationSchemas.accept(),
                    NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            case "generate_reject" -> server.generateNegotiationRejectPromptFromText(
                    testCase.input(), context, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            case "validate_reject" -> client.validateRejectPromptAndDataFilling(
                    testCase.input(), context, InformationNegotiationSchemas.reject(),
                    NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            default -> throw new IllegalArgumentException("Unknown business abnormal API: " + testCase.api());
        };
    }

    private static Map<String, Object> requestFor(
            NegotiationBusinessAbnormalEvaluationCase testCase, NegotiationContext context) {
        Map<String, Object> request = new LinkedHashMap<>();
        if (testCase.api().startsWith("generate_")) {
            request.put("text", testCase.input());
        } else {
            request.put("prompt", testCase.input());
            request.put("schema", schemaFor(testCase.api()));
        }
        request.put("context", contextPayload(context));
        request.put("template_uri", testCase.api().contains("propose")
                ? NegotiationSampleFlow.PROPOSE_TEMPLATE_URI
                : NegotiationSampleFlow.ENDING_TEMPLATE_URI);
        return request;
    }

    private static Map<String, Object> event(
            String runId,
            NegotiationBusinessAbnormalEvaluationCase testCase,
            Map<String, Object> request,
            Map<String, Object> response,
            Map<String, Object> result,
            long startedAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("event_type", "business_abnormal_case");
        event.put("run_id", runId);
        event.put("case_id", testCase.id());
        event.put("api", apiMethod(testCase.api()));
        event.put("request", request);
        if (response != null) {
            event.put("response", response);
        }
        event.put("result", result);
        event.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
        return event;
    }

    private static NegotiationPerformative performativeFor(String api) {
        return api.contains("accept") ? NegotiationPerformative.ACCEPT
                : api.contains("reject") ? NegotiationPerformative.REJECT : NegotiationPerformative.PROPOSE;
    }

    private static Map<String, Object> schemaFor(String api) {
        return switch (api) {
            case "validate_propose" -> InformationNegotiationSchemas.propose();
            case "validate_accept" -> InformationNegotiationSchemas.accept();
            case "validate_reject" -> InformationNegotiationSchemas.reject();
            default -> Map.of();
        };
    }

    private static String apiMethod(String api) {
        return switch (api) {
            case "generate_propose" -> "A2ATClient.generateNegotiationProposePromptFromText";
            case "validate_propose" -> "A2ATServer.validateProposePromptAndDataFilling";
            case "generate_accept" -> "A2ATServer.generateNegotiationAcceptPromptFromText";
            case "validate_accept" -> "A2ATClient.validateAcceptPromptAndDataFilling";
            case "generate_reject" -> "A2ATServer.generateNegotiationRejectPromptFromText";
            case "validate_reject" -> "A2ATClient.validateRejectPromptAndDataFilling";
            default -> api;
        };
    }

    private static Map<String, Object> responseSummary(Object response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (response instanceof MetadataContent metadata) {
            summary.put("type", "MetadataContent");
            summary.put("prompt", metadata.promptText());
            summary.put("template_uri", metadata.templateUri());
            summary.put("extension_uri", metadata.extensionUri());
        } else if (response instanceof FilledParamData filled) {
            summary.put("type", "FilledParamData");
            summary.put("data", filled.data());
        } else {
            summary.put("type", response == null ? "null" : response.getClass().getName());
        }
        return summary;
    }

    private static Map<String, Object> errorDetails(RuntimeException exception) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("class", exception.getClass().getSimpleName());
        error.put("message", exception.getMessage());
        if (exception instanceof A2ATError a2atError) {
            error.put("code", a2atError.getCode());
        }
        return error;
    }

    private static Map<String, Object> contextPayload(NegotiationContext context) {
        return Map.of("id", context.id(), "round", context.round(), "maxRounds", context.maxRounds(),
                "performative", context.performative().name());
    }

    private static void requireQwenConfiguration(Map<String, String> environment) {
        if (!"openai".equals(environment.get("A2AT_LLM_PROVIDER"))
                || environment.get("A2AT_LLM_BASE_URL") == null) {
            throw new IllegalArgumentException(
                    "Business abnormal evaluation requires A2AT_LLM_PROVIDER=openai and A2AT_LLM_BASE_URL");
        }
    }

    private static String gitRevision() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .redirectErrorStream(true).start();
            String revision;
            try (var input = process.getInputStream()) {
                revision = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
            return process.waitFor() == 0 && !revision.isBlank() ? revision : "unknown";
        } catch (IOException exception) {
            return "unknown";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }
}
