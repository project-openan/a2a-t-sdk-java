package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.openan.a2at.sample.negotiation.shared.InformationNegotiationSchemas;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleFlow;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.resources.NegotiationTemplateLoader;
import net.openan.a2at.sdk.server.A2ATServer;

/** Runs deterministic abnormal-input checks against all six Negotiation-T facade APIs and core pipelines. */
public final class NegotiationAbnormalEvaluationMain {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> NEGOTIATION_ERROR_CODES = List.of(
            "template.not_found",
            "negotiation.semantic_rejected",
            "negotiation.field_missing",
            "negotiation.invalid_input",
            "negotiation.invalid_context_id",
            "negotiation.round_exceeded",
            "llm.invocation_failed",
            "llm.response_invalid");

    private NegotiationAbnormalEvaluationMain() {}

    public static void main(String[] args) throws IOException {
        Path envPath = args.length > 0
                ? Path.of(args[0])
                : Path.of(
                        "a2a-t-sample",
                        "src",
                        "main",
                        "resources",
                        "sample",
                        "private-line-complaint-negotiation",
                        "qwen.env");
        String suffix = LocalDateTime.now(ZoneId.systemDefault()).format(FILE_TIMESTAMP);
        Path reportPath = args.length > 1
                ? Path.of(args[1])
                : Path.of("a2a-t-sample", "target", "negotiation-abnormal-report-" + suffix + ".json");
        Path processLogPath = args.length > 2
                ? Path.of(args[2])
                : Path.of("a2a-t-sample", "target", "negotiation-abnormal-process-log-" + suffix + ".jsonl");

        A2ATClient client = new A2ATClient(envPath);
        A2ATServer server = new A2ATServer(envPath);
        String runId = UUID.randomUUID().toString();
        List<Map<String, Object>> results = new ArrayList<>();
        try (NegotiationEvaluationProcessLogger logger =
                new NegotiationEvaluationProcessLogger(OBJECT_MAPPER, processLogPath)) {
            logger.write(event(
                    "run_started",
                    runId,
                    null,
                    Map.of(
                            "env_path",
                            envPath.toAbsolutePath().toString(),
                            "case_count",
                            NegotiationAbnormalEvaluationCaseLoader.load().size()
                                    + scriptedCases().size()),
                    null,
                    null));
            for (NegotiationAbnormalEvaluationCase testCase : NegotiationAbnormalEvaluationCaseLoader.load()) {
                results.add(runCase(client, server, testCase, runId, logger));
            }
            for (ScriptedAbnormalCase testCase : scriptedCases()) {
                results.add(runScriptedCase(testCase, runId, logger));
            }
        }

        long passed = results.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("passed")))
                .count();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", Instant.now().toString());
        report.put("run_id", runId);
        report.put("git_revision", gitRevision());
        report.put("total", results.size());
        report.put("passed", passed);
        report.put("failed", results.size() - passed);
        report.put("success_rate", results.isEmpty() ? 1.0 : (double) passed / results.size());
        report.put("definition", "异常用例在预期 SDK 异常发生且错误类型/错误码匹配时判定通过；门面用例使用边界输入，脚本化用例注入固定 LLM 响应，不访问外部 LLM。");
        report.put("error_code_coverage", errorCodeCoverage(results));
        report.put("process_log", processLogPath.toAbsolutePath().toString());
        report.put("cases", results);
        Path parent = reportPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        OBJECT_MAPPER.writeValue(reportPath.toFile(), report);
        System.out.printf(
                "Abnormal negotiation evaluation complete: %d/%d passed; report=%s; process-log=%s%n",
                passed, results.size(), reportPath.toAbsolutePath(), processLogPath.toAbsolutePath());
    }

    private static Map<String, Object> errorCodeCoverage(List<Map<String, Object>> results) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        NEGOTIATION_ERROR_CODES.forEach(code -> counts.put(code, 0));
        Set<String> observed = new LinkedHashSet<>();
        for (Map<String, Object> result : results) {
            Object error = result.get("actual_error");
            if (error instanceof Map<?, ?> errorMap && errorMap.get("code") instanceof String code) {
                observed.add(code);
                if (counts.containsKey(code)) {
                    counts.computeIfPresent(code, (ignored, count) -> count + 1);
                }
            }
        }
        List<String> missing = NEGOTIATION_ERROR_CODES.stream()
                .filter(code -> counts.get(code) == 0)
                .toList();
        List<String> unexpected =
                observed.stream().filter(code -> !counts.containsKey(code)).toList();
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("expected_codes", NEGOTIATION_ERROR_CODES);
        coverage.put("observed_counts", counts);
        coverage.put("missing_codes", missing);
        coverage.put("unexpected_codes", unexpected);
        coverage.put("all_expected_codes_covered", missing.isEmpty());
        return coverage;
    }

    private static Map<String, Object> runCase(
            A2ATClient client,
            A2ATServer server,
            NegotiationAbnormalEvaluationCase testCase,
            String runId,
            NegotiationEvaluationProcessLogger logger)
            throws IOException {
        long startedAt = System.nanoTime();
        NegotiationPerformative performative = NegotiationPerformative.valueOf(testCase.performative());
        NegotiationContext context = contextFor(testCase.contextMode(), performative);
        String templateUri = templateFor(testCase.template());
        Map<String, Object> request = new LinkedHashMap<>();
        if (testCase.api().startsWith("generate_")) {
            request.put("text", testCase.input());
        } else {
            request.put("prompt", testCase.input());
            request.put("schema", schemaFor(testCase.api(), testCase.schemaMode()));
        }
        request.put("template_uri", templateUri);
        request.put("context", context == null ? null : contextPayload(context));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", testCase.id());
        result.put("description", testCase.description());
        result.put("api", apiMethod(testCase.api()));
        result.put("expected_exception", testCase.expectedException());
        result.put("expected_code", testCase.expectedCode());
        result.put("request", request);
        try {
            Object response = invoke(client, server, testCase, context, templateUri);
            result.put("outcome", "unexpected_success");
            result.put("response", responseSummary(response));
            result.put("passed", false);
            logger.write(event(
                    "abnormal_case", runId, testCase.id(), request, Map.of("outcome", "unexpected_success"), result));
        } catch (RuntimeException exception) {
            Map<String, Object> actualError = errorDetails(exception);
            boolean classMatched = testCase.expectedException().equals(actualError.get("class"));
            boolean codeMatched =
                    testCase.expectedCode() == null || testCase.expectedCode().equals(actualError.get("code"));
            result.put("actual_error", actualError);
            result.put("passed", classMatched && codeMatched);
            result.put("outcome", classMatched && codeMatched ? "expected_failure" : "unexpected_failure");
            logger.write(event("abnormal_case", runId, testCase.id(), request, null, result));
        }
        result.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
        return result;
    }

    private static List<ScriptedAbnormalCase> scriptedCases() {
        return List.of(
                new ScriptedAbnormalCase(
                        "G-PROPOSE-LLM-MALFORMED",
                        "generate_propose",
                        "请补充接入端口名称和投诉分类。",
                        "not-json",
                        false,
                        "NegotiationGenerationException",
                        "llm.response_invalid"),
                new ScriptedAbnormalCase(
                        "G-ACCEPT-LLM-INFRA",
                        "generate_accept",
                        "接受协商，端口为 PE1-GZ-ETH-0/0/0.200，投诉分类为专线中断。",
                        "throw",
                        false,
                        "NegotiationGenerationException",
                        "llm.invocation_failed"),
                new ScriptedAbnormalCase(
                        "G-REJECT-SLOT-MISSING",
                        "generate_reject",
                        "拒绝协商，原因是资源系统暂时无法查询。",
                        "{\"conclusion\":\"Reject\",\"items\":null}",
                        false,
                        "NegotiationGenerationException",
                        "negotiation.field_missing"),
                new ScriptedAbnormalCase(
                        "V-PROPOSE-SEMANTIC-REJECTED",
                        "validate_propose",
                        "## 信息协商\n请根据<所需信息项>补充相关内容。",
                        semanticRejectedPayload(),
                        false,
                        "NegotiationParamExtractionException",
                        "negotiation.semantic_rejected"),
                new ScriptedAbnormalCase(
                        "V-ACCEPT-LLM-INFRA",
                        "validate_accept",
                        "## 信息协商结果\nAccept\n\n## 信息协商结果内容\n1. 接入端口名称：PE1-GZ-ETH-0/0/0.200\n2. 投诉分类：专线中断",
                        "throw",
                        false,
                        "NegotiationParamExtractionException",
                        "llm.invocation_failed"),
                new ScriptedAbnormalCase(
                        "V-REJECT-SEMANTIC-REJECTED",
                        "validate_reject",
                        "## 信息协商结果\nReject\n\n## 信息协商结果内容\n1. 接入端口名称：无法提供，原因：资源系统暂时无法查询\n2. 投诉分类：无法提供，原因：资源系统暂时无法查询",
                        semanticRejectedPayload(),
                        false,
                        "NegotiationParamExtractionException",
                        "negotiation.semantic_rejected"),
                new ScriptedAbnormalCase(
                        "G-PROPOSE-TEMPLATE-NOT-FOUND",
                        "generate_propose",
                        "请补充接入端口名称和投诉分类。",
                        "unused",
                        true,
                        "NegotiationGenerationException",
                        "template.not_found"),
                new ScriptedAbnormalCase(
                        "V-PROPOSE-TEMPLATE-NOT-FOUND",
                        "validate_propose",
                        "## 信息协商\n请根据<所需信息项>补充相关内容。",
                        "unused",
                        true,
                        "NegotiationParamExtractionException",
                        "template.not_found"));
    }

    private static String semanticRejectedPayload() {
        return "{\"semantic_verdict\":false,\"negotiation_type\":\"information\","
                + "\"errors\":[{\"slot_name\":\"section.info_result_content\","
                + "\"code\":\"negotiation.missing_result_content\","
                + "\"facts\":{\"section_label\":\"信息协商结果内容\"}}],"
                + "\"params\":{}}";
    }

    private static Map<String, Object> runScriptedCase(
            ScriptedAbnormalCase testCase, String runId, NegotiationEvaluationProcessLogger logger) throws IOException {
        long startedAt = System.nanoTime();
        NegotiationPerformative performative = testCase.api().contains("accept")
                ? NegotiationPerformative.ACCEPT
                : testCase.api().contains("reject") ? NegotiationPerformative.REJECT : NegotiationPerformative.PROPOSE;
        NegotiationContext context = new NegotiationContext(UUID.randomUUID().toString(), 1, 3, performative);
        String template = testCase.api().contains("propose")
                ? NegotiationSampleFlow.PROPOSE_TEMPLATE_URI
                : NegotiationSampleFlow.ENDING_TEMPLATE_URI;
        ScriptedLlmClient llm = new ScriptedLlmClient(testCase.payload());
        NegotiationGenerationOrchestratorBuilder builder = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .maxAttempts(1);
        if (testCase.missingTemplate()) {
            builder.templateLoader(new MissingTemplateLoader());
        }
        NegotiationGenerationOrchestrator orchestrator = builder.build();
        Map<String, Object> request = new LinkedHashMap<>();
        if (testCase.api().startsWith("generate_")) {
            request.put("text", testCase.input());
        } else {
            request.put("prompt", testCase.input());
            request.put("schema", schemaFor(testCase.api(), null));
        }
        request.put("context", contextPayload(context));
        request.put("template_uri", template);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", testCase.id());
        result.put("api", scriptedApiMethod(testCase.api()));
        result.put("request", request);
        result.put("expected_exception", testCase.expectedException());
        result.put("expected_code", testCase.expectedCode());
        try {
            Object response = invoke(orchestrator, testCase, context, parseTemplateUri(template));
            result.put("response", responseSummary(response));
            result.put("outcome", "unexpected_success");
            result.put("passed", false);
            logger.write(
                    event("abnormal_scripted_case", runId, testCase.id(), request, responseSummary(response), result));
        } catch (RuntimeException exception) {
            Map<String, Object> actualError = errorDetails(exception);
            boolean matched = testCase.expectedException().equals(actualError.get("class"))
                    && testCase.expectedCode().equals(actualError.get("code"));
            result.put("actual_error", actualError);
            result.put("outcome", matched ? "expected_failure" : "unexpected_failure");
            result.put("passed", matched);
            logger.write(event("abnormal_scripted_case", runId, testCase.id(), request, null, result));
        }
        result.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
        return result;
    }

    private static Object invoke(
            NegotiationGenerationOrchestrator orchestrator,
            ScriptedAbnormalCase testCase,
            NegotiationContext context,
            TemplateUri template) {
        return switch (testCase.api()) {
            case "generate_propose" -> orchestrator.generateProposeFromText(testCase.input(), context, template);
            case "generate_accept" -> orchestrator.generateAcceptFromText(testCase.input(), context, template);
            case "generate_reject" -> orchestrator.generateRejectFromText(testCase.input(), context, template);
            case "validate_propose" -> orchestrator.validateProposePromptAndDataFilling(
                    testCase.input(), context, InformationNegotiationSchemas.propose(), template);
            case "validate_accept" -> orchestrator.validateAcceptPromptAndDataFilling(
                    testCase.input(), context, InformationNegotiationSchemas.accept(), template);
            case "validate_reject" -> orchestrator.validateRejectPromptAndDataFilling(
                    testCase.input(), context, InformationNegotiationSchemas.reject(), template);
            default -> throw new IllegalArgumentException("Unknown scripted abnormal API: " + testCase.api());
        };
    }

    private static Object invoke(
            A2ATClient client,
            A2ATServer server,
            NegotiationAbnormalEvaluationCase testCase,
            NegotiationContext context,
            String templateUri) {
        return switch (testCase.api()) {
            case "generate_propose" -> client.generateNegotiationProposePromptFromText(
                    testCase.input(), context, templateUri);
            case "validate_propose" -> server.validateProposePromptAndDataFilling(
                    testCase.input(), context, schemaFor(testCase.api(), testCase.schemaMode()), templateUri);
            case "generate_accept" -> server.generateNegotiationAcceptPromptFromText(
                    testCase.input(), context, templateUri);
            case "validate_accept" -> client.validateAcceptPromptAndDataFilling(
                    testCase.input(), context, schemaFor(testCase.api(), testCase.schemaMode()), templateUri);
            case "generate_reject" -> server.generateNegotiationRejectPromptFromText(
                    testCase.input(), context, templateUri);
            case "validate_reject" -> client.validateRejectPromptAndDataFilling(
                    testCase.input(), context, schemaFor(testCase.api(), testCase.schemaMode()), templateUri);
            default -> throw new IllegalArgumentException("Unknown abnormal evaluation API: " + testCase.api());
        };
    }

    private static Map<String, Object> schemaFor(String api, String mode) {
        if ("null".equalsIgnoreCase(mode)) {
            return null;
        }
        return switch (api) {
            case "validate_propose" -> InformationNegotiationSchemas.propose();
            case "validate_accept" -> InformationNegotiationSchemas.accept();
            case "validate_reject" -> InformationNegotiationSchemas.reject();
            default -> Map.of();
        };
    }

    private static NegotiationContext contextFor(String mode, NegotiationPerformative performative) {
        if ("null".equalsIgnoreCase(mode)) {
            return null;
        }
        if ("invalid_id".equalsIgnoreCase(mode)) {
            return new NegotiationContext("not-a-uuid", 1, 3, performative);
        }
        if ("exhausted".equalsIgnoreCase(mode)) {
            return new NegotiationContext(UUID.randomUUID().toString(), 4, 3, performative);
        }
        return new NegotiationContext(UUID.randomUUID().toString(), 1, 3, performative);
    }

    private static String templateFor(String template) {
        return switch (template == null ? "null" : template.toLowerCase(java.util.Locale.ROOT)) {
            case "null" -> null;
            case "propose" -> NegotiationSampleFlow.PROPOSE_TEMPLATE_URI;
            case "ending" -> NegotiationSampleFlow.ENDING_TEMPLATE_URI;
            case "unknown_propose" -> "Negotiation-T/information-negotiation/propose/v999";
            case "unknown_ending" -> "Negotiation-T/information-negotiation/accept-reject/v999";
            default -> throw new IllegalArgumentException("Unknown abnormal evaluation template: " + template);
        };
    }

    /** Parses a template URI string for the typed {@code NegotiationGenerationOrchestrator} seam. */
    private static TemplateUri parseTemplateUri(String templateUri) {
        return TemplateUri.parse(templateUri)
                .orElseThrow(() -> new IllegalArgumentException("Unparseable template URI: " + templateUri));
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
        } else if (response instanceof FilledParamData filled) {
            summary.put("type", "FilledParamData");
            summary.put("data", filled.data());
        } else {
            summary.put("type", response == null ? "null" : response.getClass().getName());
        }
        return summary;
    }

    private static Map<String, Object> event(
            String stage,
            String runId,
            String caseId,
            Map<String, Object> request,
            Map<String, Object> response,
            Map<String, Object> result) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("run_id", runId);
        event.put("stage", stage);
        if (result != null && result.get("api") != null) {
            event.put("api", result.get("api"));
        }
        if (caseId != null) {
            event.put("case_id", caseId);
        }
        event.put("request", request);
        if (response != null) {
            event.put("response", response);
        }
        if (result != null) {
            event.put("result", result);
        }
        return event;
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
        return Map.of(
                "id", context.id(),
                "round", context.round(),
                "maxRounds", context.maxRounds(),
                "performative", context.performative().name());
    }

    private static String scriptedApiMethod(String api) {
        return switch (api) {
            case "generate_propose" -> "NegotiationGenerationOrchestrator.generateProposeFromText";
            case "generate_accept" -> "NegotiationGenerationOrchestrator.generateAcceptFromText";
            case "generate_reject" -> "NegotiationGenerationOrchestrator.generateRejectFromText";
            case "validate_propose" -> "NegotiationGenerationOrchestrator.validateProposePromptAndDataFilling";
            case "validate_accept" -> "NegotiationGenerationOrchestrator.validateAcceptPromptAndDataFilling";
            case "validate_reject" -> "NegotiationGenerationOrchestrator.validateRejectPromptAndDataFilling";
            default -> api;
        };
    }

    private record ScriptedAbnormalCase(
            String id,
            String api,
            String input,
            String payload,
            boolean missingTemplate,
            String expectedException,
            String expectedCode) {}

    private static final class ScriptedLlmClient implements LLMClient {

        private final String payload;

        private ScriptedLlmClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            if ("throw".equals(payload)) {
                throw new IllegalStateException("scripted LLM infrastructure failure");
            }
            return new LLMResponse(
                    payload, "sample-scripted-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }

    private static final class MissingTemplateLoader implements NegotiationTemplateLoader {

        @Override
        public PromptTemplate load(NegotiationReference reference) {
            throw new ResourceNotFoundException("Negotiation template does not exist.", reference.uri());
        }
    }

    private static String gitRevision() {
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
}
