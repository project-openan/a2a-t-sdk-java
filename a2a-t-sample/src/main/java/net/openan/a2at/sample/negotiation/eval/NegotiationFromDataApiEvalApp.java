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
import net.openan.a2at.sample.negotiation.shared.InformationNegotiationSchemas;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * Focused capability evaluator for the six Negotiation-T fromData interfaces:
 *
 * <ul>
 *   <li>generation - {@code generateNegotiationProposePromptFromData},
 *       {@code generateNegotiationAcceptPromptFromData}, {@code generateNegotiationRejectPromptFromData};
 *   <li>validation and parameter extraction - {@code validateProposePromptAndDataFilling},
 *       {@code validateAcceptPromptAndDataFilling}, {@code validateRejectPromptAndDataFilling}.
 * </ul>
 *
 * <p>Each case is one generate-validate round trip over pure structured data: every item is one key (the
 * Task-T template slot name from the private-line-complaint scenario) carrying an atomic value - no
 * natural-language passages, no template-error scenarios. The generated message is validated through the
 * matching validate interface (propose validated client-side, accept/reject server-side - the receiving
 * role of the protocol), and the extracted parameters must carry back exactly the input key-value pairs.
 *
 * <p>Generation is deterministic rendering (no LLM call); validation runs the SDK negotiation semantic
 * pipeline (one LLM call), so a real API key is needed and each validation is recorded as {@code llm_calls}
 * evidence in the report. The case matrix follows the Task-T field model (required:
 * 任务对象/投诉分类/OSS侧事件流水号; optional: 问题发生时间/投诉详情).
 *
 * <p>Usage:
 *
 * <pre>{@code
 * java @a2a-t-sample/target/fromdata-eval.javaargs.txt [--out fromdata-eval-report.json] /path/to/.env
 * }</pre>
 *
 * @since 2026-08
 */
public final class NegotiationFromDataApiEvalApp {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SUITE_RESOURCE = "sample/negotiation/eval/fromdata-api-suite.json";

    private static final Set<String> CONTEXT_KEYS = Set.of("id", "round", "maxRounds");

    private static Consumer<String> sink = System.out::println;

    private NegotiationFromDataApiEvalApp() {}

    /** Entry point. */
    public static void main(String[] args) {
        Path envPath = null;
        Path outPath = Path.of("fromdata-eval-report.json");
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--out".equals(arg) && i + 1 < args.length) {
                outPath = Path.of(args[++i]);
            } else if (!arg.startsWith("--")) {
                envPath = Path.of(arg);
            }
        }
        if (envPath == null) {
            System.err.println(
                    "Usage: java @a2a-t-sample/target/fromdata-eval.javaargs.txt [--out fromdata-eval-report.json]"
                            + " /path/to/.env");
            System.exit(1);
        }

        EvalLlmCaptureClient.install();
        Map<String, Object> suite = loadSuite();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", suite.get("suite"));
        report.put("description", suite.get("description"));
        report.put("scenario", suite.get("scenario"));
        report.put("generated_at", LocalDateTime.now().format(TIMESTAMP));

        List<Map<String, Object>> cases = new ArrayList<>();
        report.put("cases", cases);
        int index = 0;
        List<Map<String, Object>> suiteCases = asMapList(suite.get("cases"));
        for (Map<String, Object> testCase : suiteCases) {
            index++;
            emit("\n[fromdata-eval] [" + index + "/" + suiteCases.size() + "] case " + testCase.get("id") + ": "
                    + testCase.get("intent"));
            long startNanos = System.nanoTime();
            Map<String, Object> record = runCase(envPath, testCase);
            record.put("duration_seconds", Math.round((System.nanoTime() - startNanos) / 100_000_000.0) / 10.0);
            cases.add(record);
            writeReport(report, outPath);
        }
        report.put("metrics", metrics(cases));
        writeReport(report, outPath);
        emit("\n[fromdata-eval] report written to " + outPath.toAbsolutePath());
    }

    // -- one case: generate -> validate round trip with exact assertions --

    private static Map<String, Object> runCase(Path envPath, Map<String, Object> testCase) {
        String caseId = String.valueOf(testCase.get("id"));
        String api = String.valueOf(testCase.get("api"));
        Map<String, Object> input = asMap(testCase.get("input"));
        Map<String, Object> expect = asMap(testCase.get("expect"));
        boolean expectSuccess = Boolean.TRUE.equals(expect.get("succeeds"));

        Map<String, Object> itemsInput = asMap(input.get("items"));
        String relationship = input.containsKey("relationship")
                ? String.valueOf(input.get("relationship"))
                : null;
        String template = resolveTemplate(api, input);

        NegotiationPerformative performative = "propose".equals(api)
                ? NegotiationPerformative.PROPOSE
                : "accept".equals(api) ? NegotiationPerformative.ACCEPT : NegotiationPerformative.REJECT;
        NegotiationContext context =
                new NegotiationContext(UUID.randomUUID().toString(), 1, NegotiationContext.DEFAULT_MAX_ROUNDS, performative);
        List<NegotiationItem> items = new ArrayList<>();
        for (Map.Entry<String, Object> entry : itemsInput.entrySet()) {
            items.add(new NegotiationItem(entry.getKey(), String.valueOf(entry.getValue())));
        }

        String generateMethod = "propose".equals(api)
                ? "A2ATServer.generateNegotiationProposePromptFromData"
                : "A2ATClient.generateNegotiation"
                        + ("accept".equals(api) ? "Accept" : "Reject") + "PromptFromData";
        String validateMethod = "propose".equals(api)
                ? "A2ATClient.validateProposePromptAndDataFilling"
                : "A2ATServer.validate"
                        + ("accept".equals(api) ? "Accept" : "Reject") + "PromptAndDataFilling";
        // the receiving role validates: the client validates the server's propose, the server validates the
        // client's accept/reject - exactly the role assignment of the negotiation protocol
        String generateRole = "propose".equals(api) ? "server" : "client";
        String validateRole = "propose".equals(api) ? "client" : "server";

        Map<String, Object> generateInput = new LinkedHashMap<>();
        generateInput.put("context", contextJson(context));
        generateInput.put("items", itemsInput);
        if (input.containsKey("relationship")) {
            generateInput.put("relationship", relationship);
        }
        generateInput.put("template_uri", template);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("case", caseId);
        record.put("intent", testCase.get("intent"));
        record.put("steps", new ArrayList<Map<String, Object>>());
        record.put("input", generateInput);

        List<Map<String, Object>> checks = new ArrayList<>();
        record.put("checks", checks);

        // -- step 1: generation from structured data --
        EvalLlmCaptureClient.reset();
        Map<String, Object> generateStep = step("1. generate " + api + " (fromData)", generateRole);
        api(generateStep, apiCall(generateMethod, generateInput));
        String prompt = null;
        try {
            MetadataContent content = switch (api) {
                case "propose" -> new A2ATServer(envPath).generateNegotiationProposePromptFromData(
                        new NegotiationProposeData(
                                context, new InformationProposeContent(items, relationship)), template);
                case "accept" -> new A2ATClient(envPath).generateNegotiationAcceptPromptFromData(
                        new NegotiationEndingData(
                                context, new InformationEndingContent(NegotiationConclusion.ACCEPT, items)),
                        template);
                case "reject" -> new A2ATClient(envPath).generateNegotiationRejectPromptFromData(
                        new NegotiationEndingData(
                                context, new InformationEndingContent(NegotiationConclusion.REJECT, items)),
                        template);
                default -> throw new IllegalArgumentException("Unknown api: " + api);
            };
            prompt = content.promptText();
            generateStep.put("generated_prompt", prompt);
            generateStep.put("template_uri", content.templateUri());
            generateStep.put("extension_uri", content.extensionUri());
            NegotiationContext emitted = content.negotiationContext();
            if (emitted != null) {
                generateStep.put("emitted_context", contextJson(emitted));
            }
            if (expectSuccess) {
                String normalized = normalize(prompt);
                for (Map.Entry<String, Object> entry : itemsInput.entrySet()) {
                    check(checks, "prompt contains item name: " + entry.getKey(),
                            normalized.contains(normalize(entry.getKey())));
                    check(checks, "prompt contains item value: " + entry.getKey(),
                            normalized.contains(normalize(String.valueOf(entry.getValue()))));
                }
                if (input.containsKey("relationship") && !relationship.isBlank()) {
                    check(checks, "prompt contains relationship", normalized.contains(normalize(relationship)));
                }
                for (String marker : stringList(expect.get("contains"))) {
                    check(checks, "prompt contains marker: " + marker, normalized.contains(normalize(marker)));
                }
                if (expect.get("template") != null) {
                    check(checks, "template uri matches",
                            String.valueOf(expect.get("template")).equals(content.templateUri()));
                }
                // the wire context carries the performative of the emitted message and keeps the session identity
                check(checks, "emitted context performative matches the api",
                        emitted != null && performative.equals(emitted.performative()));
                check(checks, "emitted context keeps the session id",
                        emitted != null && context.id().equals(emitted.id()));
            } else {
                check(checks, "expected generation failure but succeeded", false);
            }
        } catch (RuntimeException error) {
            generateStep.put("error", errorJson(error));
            if (expectSuccess) {
                check(checks, "expected generation success but failed: " + error.getClass().getSimpleName(), false);
            } else {
                check(checks, "generation failed as expected", true);
            }
        }
        attachLlmCalls(generateStep);
        steps(record).add(generateStep);

        // -- step 2: validation and parameter extraction of the generated message --
        if (prompt != null && !itemsInput.isEmpty()) {
            // The caller schema describes the business facts to extract, not the SDK's internal
            // NegotiationItem wire model. It is the same shared contract the fromText samples use.
            Map<String, Object> extractionSchema = extractionSchema(api);
            Map<String, Object> validateInput = new LinkedHashMap<>();
            validateInput.put("prompt", prompt);
            validateInput.put("context", contextJson(context));
            validateInput.put("schema", extractionSchema);
            validateInput.put("template_uri", template);
            Map<String, Object> validateStep = step("2. validate + extract " + api + " (fromData)", validateRole);
            api(validateStep, apiCall(validateMethod, validateInput));
            try {
                FilledParamData params = switch (api) {
                    case "propose" -> new A2ATClient(envPath).validateProposePromptAndDataFilling(
                            prompt, context, extractionSchema, template);
                    case "accept" -> new A2ATServer(envPath).validateAcceptPromptAndDataFilling(
                            prompt, context, extractionSchema, template);
                    case "reject" -> new A2ATServer(envPath).validateRejectPromptAndDataFilling(
                            prompt, context, extractionSchema, template);
                    default -> throw new IllegalArgumentException("Unknown api: " + api);
                };
                Map<String, Object> extracted =
                        params.data() == null ? Map.of() : new LinkedHashMap<>(params.data());
                Map<String, Object> extractedJson = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : extracted.entrySet()) {
                    if (!CONTEXT_KEYS.contains(entry.getKey())) {
                        extractedJson.put(entry.getKey(), entry.getValue());
                    }
                }
                Map<String, Object> validation = new LinkedHashMap<>();
                validation.put("outcome", "passed");
                validation.put("extracted_params", extractedJson);
                validateStep.put("validation", validation);
                check(checks, "validation passed", true);
                checkExtractedFields(checks, api, extractedJson, itemsInput, input);
            } catch (RuntimeException error) {
                Map<String, Object> validation = new LinkedHashMap<>();
                validation.put("outcome", "rejected");
                validation.put("error", errorJson(error));
                validateStep.put("validation", validation);
                check(checks, "validation passed (got: " + error.getClass().getSimpleName() + ")", false);
            }
            attachLlmCalls(validateStep);
            steps(record).add(validateStep);
        }

        boolean pass = checks.stream().allMatch(check -> Boolean.TRUE.equals(check.get("pass")));
        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("pass", pass);
        List<String> failed = new ArrayList<>();
        for (Map<String, Object> check : checks) {
            if (!Boolean.TRUE.equals(check.get("pass"))) {
                failed.add(String.valueOf(check.get("name")));
            }
        }
        verdict.put("reason", failed.isEmpty() ? "" : "failed checks: " + String.join("; ", failed));
        record.put("verdict", verdict);
        emit("[fromdata-eval]   verdict: " + (pass ? "PASS" : "FAIL")
                + (failed.isEmpty() ? "" : " (" + verdict.get("reason") + ")"));
        return record;
    }

    /**
     * Caller-owned extraction schema, shared with the fromText samples: the same information-negotiation
     * contracts work for both generation paths because the validate*AndDataFilling interfaces and the rendered
     * wire format are identical.
     */
    static Map<String, Object> extractionSchema(String api) {
        return switch (api) {
            case "propose" -> InformationNegotiationSchemas.propose();
            case "accept" -> InformationNegotiationSchemas.accept();
            case "reject" -> InformationNegotiationSchemas.reject();
            default -> throw new IllegalArgumentException("Unknown api: " + api);
        };
    }

    /**
     * Asserts that semantic extraction returns exactly the negotiated business field names, and that
     * accept/reject payloads transport the input item text (supplied value / reason).
     */
    private static void checkExtractedFields(
            List<Map<String, Object>> checks,
            String api,
            Map<String, Object> extractedJson,
            Map<String, Object> itemsInput,
            Map<String, Object> input) {
        List<Map<String, Object>> extractedItems = asMapList(extractedJson.get("items"));
        Set<String> extractedNames = new TreeSet<>();
        for (Map<String, Object> item : extractedItems) {
            extractedNames.add(String.valueOf(item.get("name")));
        }
        check(checks, "extracted field names match the negotiated items",
                extractedNames.equals(new TreeSet<>(itemsInput.keySet())));
        if ("propose".equals(api) && input.containsKey("relationship")) {
            String relationship = String.valueOf(input.get("relationship"));
            check(checks, "extracted relationship matches input",
                    valueMatches(extractedJson.get("relationship"), relationship));
        } else {
            check(checks, "relationship is absent for a single field or ending message",
                    extractedJson.get("relationship") == null);
        }
        if ("accept".equals(api) || "reject".equals(api)) {
            String payloadKey = "accept".equals(api) ? "value" : "reason";
            for (Map<String, Object> item : extractedItems) {
                String name = String.valueOf(item.get("name"));
                check(checks, "extracted " + payloadKey + " matches input item: " + name,
                        valueMatches(item.get(payloadKey), String.valueOf(itemsInput.get(name))));
            }
        }
    }

    /** Bidirectional whitespace-free containment: the extracted value may carry the field label or the bare value. */
    private static boolean valueMatches(Object extracted, String input) {
        if (extracted == null) {
            return false;
        }
        String extractedText = normalize(String.valueOf(extracted));
        String inputText = normalize(input);
        return !extractedText.isEmpty()
                && (extractedText.contains(inputText) || inputText.contains(extractedText));
    }

    private static Map<String, Object> errorJson(RuntimeException error) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("type", error.getClass().getSimpleName());
        if (error instanceof A2ATError a2atError) {
            failure.put("code", a2atError.getCode());
        }
        failure.put("message", String.valueOf(error.getMessage()));
        return failure;
    }

    private static String resolveTemplate(String api, Map<String, Object> input) {
        if (input.containsKey("template")) {
            return String.valueOf(input.get("template"));
        }
        return "propose".equals(api)
                ? StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI
                : StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI;
    }

    private static void check(List<Map<String, Object>> checks, String name, boolean pass) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("name", name);
        check.put("pass", pass);
        checks.add(check);
    }

    private static void attachLlmCalls(Map<String, Object> step) {
        List<Map<String, Object>> calls = EvalLlmCaptureClient.drain();
        if (!calls.isEmpty()) {
            step.put("llm_calls", calls);
        }
    }

    // -- report --

    private static Map<String, Object> metrics(List<Map<String, Object>> cases) {
        int pass = 0;
        for (Map<String, Object> record : cases) {
            Map<?, ?> verdict = (Map<?, ?>) record.get("verdict");
            if (Boolean.TRUE.equals(verdict.get("pass"))) {
                pass++;
            }
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("cases", cases.size());
        metrics.put("passed", pass);
        metrics.put("pct", cases.isEmpty() ? 0 : Math.round(pass * 1000.0 / cases.size()) / 10.0);
        return metrics;
    }

    private static void writeReport(Map<String, Object> report, Path outPath) {
        try {
            if (outPath.toAbsolutePath().getParent() != null) {
                Files.createDirectories(outPath.toAbsolutePath().getParent());
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), report);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write the evaluation report: " + outPath, exception);
        }
    }

    // -- suite loading and small helpers --

    private static Map<String, Object> loadSuite() {
        try (InputStream in = NegotiationFromDataApiEvalApp.class.getResourceAsStream("/" + SUITE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Eval suite resource not found: " + SUITE_RESOURCE);
            }
            return MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {});
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load the eval suite: " + SUITE_RESOURCE, exception);
        }
    }

    private static Map<String, Object> step(String name, String role) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("step", name);
        step.put("role", role);
        return step;
    }

    private static void api(Map<String, Object> step, Map<String, Object> call) {
        step.put("api_calls", List.of(call));
    }

    /** Negotiation context as report JSON, including the performative of the message it travels with. */
    private static Map<String, Object> contextJson(NegotiationContext context) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", context.id());
        json.put("round", context.round());
        json.put("maxRounds", context.maxRounds());
        json.put("performative", context.performative().name());
        return json;
    }

    private static Map<String, Object> apiCall(String method, Map<String, Object> input) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("method", method);
        call.put("input", input);
        return call;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> record) {
        return (List<Map<String, Object>>) record.get("steps");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    private static void emit(String line) {
        sink.accept(line);
    }
}
