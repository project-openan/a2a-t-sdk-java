package net.openan.a2at.sdk.corpus.engine.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;

/**
 * Strict loader for one {@code input_case_*.json} file.
 *
 * <p>Every structural violation is collected and reported in a single fail-fast message naming the file, the case id
 * and the offending construct: unknown keys, missing or blank fields, mismatched input/expected lengths, unknown api
 * names, out-of-catalog error codes and malformed {@code $fromStep} references all fail at load time.
 */
public final class CaseFileLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> CASE_KEYS =
            Set.of("id", "caseDimension", "caseDesc", "caseCategory", "input", "expected");

    private static final Set<String> STEP_KEYS = Set.of("api", "args");

    private static final Set<String> EXPECTED_KEYS = Set.of("result", "data", "dataExact", "error");

    private static final Set<String> ERROR_KEYS = Set.of("errCode", "errMessage", "errMessageContains");

    private static final Set<String> KNOWN_RESULTS = Set.of("success", "error");

    private static final Set<String> CATALOG_CODES = Arrays.stream(ErrorCatalog.values())
            .map(ErrorCatalog::getCode)
            .collect(Collectors.toUnmodifiableSet());

    public List<InputCase> load(Path file, Set<String> registeredApis) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException error) {
            throw new CorpusLoadException("Failed to read corpus file: " + file + " - " + error.getMessage());
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new CorpusLoadException(file + " is not valid JSON: " + error.getMessage());
        }
        if (root == null || !root.isArray()) {
            throw new CorpusLoadException(file + " top level must be a case array");
        }

        List<String> problems = new ArrayList<>();
        List<InputCase> cases = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();

        for (int i = 0; i < root.size(); i++) {
            JsonNode node = root.get(i);
            String caseLabel = "case[" + i + "]";
            if (!node.isObject()) {
                problems.add(caseLabel + " must be an object");
                continue;
            }
            List<String> unknownKeys = unknownKeys(node, CASE_KEYS);
            if (!unknownKeys.isEmpty()) {
                problems.add(caseLabel + " unknown keys: " + String.join(", ", unknownKeys));
                continue;
            }
            String id = requiredText(node, "id", caseLabel, problems);
            if (id != null) {
                if (ids.contains(id)) {
                    problems.add(caseLabel + " duplicate id within the scenario: " + id);
                }
                ids.add(id);
            }
            requiredText(node, "caseDimension", caseLabel, problems);
            requiredText(node, "caseDesc", caseLabel, problems);
            requiredText(node, "caseCategory", caseLabel, problems);

            List<InputStep> input = parseInputSteps(node.get("input"), caseLabel, problems, registeredApis);
            List<ExpectedStep> expected =
                    parseExpectedSteps(node.get("expected"), caseLabel, problems, input.size());

            if (id != null && !problems.isEmpty()) {
                appendCaseContext(problems, id, node, caseLabel);
            }
            cases.add(new InputCase(
                    safeText(node.get("id")),
                    safeText(node.get("caseDimension")),
                    safeText(node.get("caseDesc")),
                    safeText(node.get("caseCategory")),
                    input,
                    expected));
        }

        if (!problems.isEmpty()) {
            throw new CorpusLoadException("Corpus file validation failed " + file + ":\n- " + String.join("\n- ", problems));
        }
        return cases;
    }

    /** Returns the closed set of SDK error catalog codes; used when an expected error code is asserted. */
    public static Set<String> catalogCodes() {
        return CATALOG_CODES;
    }

    private static List<InputStep> parseInputSteps(
            JsonNode inputNode, String caseLabel, List<String> problems, Set<String> registeredApis) {
        List<InputStep> steps = new ArrayList<>();
        if (inputNode == null || !inputNode.isArray() || inputNode.isEmpty()) {
            problems.add(caseLabel + " input must be a non-empty step array");
            return steps;
        }
        for (int i = 0; i < inputNode.size(); i++) {
            JsonNode stepNode = inputNode.get(i);
            String label = caseLabel + ".input[" + i + "]";
            if (!stepNode.isObject()) {
                problems.add(label + " must be an object {api, args}");
                continue;
            }
            List<String> unknownKeys = unknownKeys(stepNode, STEP_KEYS);
            if (!unknownKeys.isEmpty()) {
                problems.add(label + " unknown keys: " + String.join(", ", unknownKeys));
                continue;
            }
            String api = requiredText(stepNode, "api", label, problems);
            if (api != null && !registeredApis.contains(api)) {
                problems.add(label + " api not registered: " + api + " (available: " + String.join(", ", registeredApis) + ")");
            }
            Map<String, Object> args = new LinkedHashMap<>();
            if (stepNode.has("args") && stepNode.get("args").isObject()) {
                args = MAPPER.convertValue(stepNode.get("args"), new TypeReference<Map<String, Object>>() {});
                validateFromStepRefs(args, label, problems, i + 1);
            } else {
                problems.add(label + " args must be an object");
            }
            steps.add(new InputStep(safeText(stepNode.get("api")), args));
        }
        return steps;
    }

    private static List<ExpectedStep> parseExpectedSteps(
            JsonNode expectedNode, String caseLabel, List<String> problems, int inputSize) {
        List<ExpectedStep> expected = new ArrayList<>();
        if (expectedNode == null || !expectedNode.isArray() || expectedNode.isEmpty()) {
            problems.add(caseLabel + " expected must be a non-empty array");
            return expected;
        }
        if (inputSize > 0 && expectedNode.size() != inputSize) {
            problems.add(caseLabel + " input/expected length mismatch: " + inputSize + " != " + expectedNode.size());
        }
        for (int i = 0; i < expectedNode.size(); i++) {
            JsonNode stepNode = expectedNode.get(i);
            String label = caseLabel + ".expected[" + i + "]";
            if (!stepNode.isObject()) {
                problems.add(label + " must be an object {result, data?, dataExact?, error?}");
                continue;
            }
            List<String> unknownKeys = unknownKeys(stepNode, EXPECTED_KEYS);
            if (!unknownKeys.isEmpty()) {
                problems.add(label + " unknown keys: " + String.join(", ", unknownKeys));
                continue;
            }
            String result = requiredText(stepNode, "result", label, problems);
            if (result != null && !KNOWN_RESULTS.contains(result)) {
                problems.add(label + " result must be success or error: " + result);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            if (stepNode.has("data")) {
                if (stepNode.get("data").isObject()) {
                    data = MAPPER.convertValue(stepNode.get("data"), new TypeReference<Map<String, Object>>() {});
                } else {
                    problems.add(label + " data must be an object");
                }
            }
            boolean dataExact = false;
            if (stepNode.has("dataExact")) {
                if (stepNode.get("dataExact").isBoolean()) {
                    dataExact = stepNode.get("dataExact").asBoolean();
                } else {
                    problems.add(label + " dataExact must be a boolean");
                }
            }
            String errCode = "";
            String errMessage = "";
            String errMessageContains = "";
            if (stepNode.has("error")) {
                if (!stepNode.get("error").isObject()) {
                    problems.add(label + " error must be an object {errCode?, errMessage?, errMessageContains?}");
                } else {
                    JsonNode errorNode = stepNode.get("error");
                    List<String> unknownErrorKeys = unknownKeys(errorNode, ERROR_KEYS);
                    if (!unknownErrorKeys.isEmpty()) {
                        problems.add(label + ".error unknown keys: " + String.join(", ", unknownErrorKeys));
                    }
                    errCode = optionalText(errorNode, "errCode", label + ".error", problems);
                    errMessage = optionalText(errorNode, "errMessage", label + ".error", problems);
                    errMessageContains =
                            optionalText(errorNode, "errMessageContains", label + ".error", problems);
                    if (!errCode.isEmpty() && !CATALOG_CODES.contains(errCode)) {
                        problems.add(label + ".error errCode not in the SDK error catalog: " + errCode);
                    }
                }
            }
            expected.add(new ExpectedStep(safeResult(result), data, dataExact, errCode, errMessage, errMessageContains));
        }
        return expected;
    }

    private static void validateFromStepRefs(
            Map<String, Object> args, String label, List<String> problems, int stepNumber) {
        validateFromStepValue(args, label, problems, stepNumber);
    }

    private static void validateFromStepValue(Object value, String label, List<String> problems, int stepNumber) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("$fromStep") || map.containsKey("$field")) {
                Object fromStep = map.get("$fromStep");
                Object field = map.get("$field");
                boolean referenceOnly =
                        map.size() == 2 && map.containsKey("$fromStep") && map.containsKey("$field");
                if (!referenceOnly) {
                    problems.add(label + " a $fromStep reference must contain exactly the two keys $fromStep and $field");
                    return;
                }
                if (!(fromStep instanceof Number number) || number.intValue() <= 0) {
                    problems.add(label + " $fromStep must be a positive integer: " + fromStep);
                } else if (number.intValue() >= stepNumber) {
                    problems.add(label + " $fromStep=" + number.intValue() + " must reference an earlier step (current step "
                            + stepNumber + ")");
                }
                if (field == null || !(field instanceof String text) || text.isBlank()) {
                    problems.add(label + " $field must be a non-blank string: " + field);
                }
                return;
            }
            for (Object entry : map.values()) {
                validateFromStepValue(entry, label, problems, stepNumber);
            }
        } else if (value instanceof List<?> list) {
            for (Object entry : list) {
                validateFromStepValue(entry, label, problems, stepNumber);
            }
        }
    }

    private static List<String> unknownKeys(JsonNode node, Set<String> allowed) {
        List<String> unknown = new ArrayList<>();
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                unknown.add(name);
            }
        });
        return unknown;
    }

    private static String requiredText(JsonNode node, String key, String label, List<String> problems) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            problems.add(label + " missing non-blank field: " + key);
            return null;
        }
        return value.asText();
    }

    private static String optionalText(JsonNode node, String key, String label, List<String> problems) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            problems.add(label + " field " + key + " must be a string");
            return "";
        }
        return value.asText();
    }

    private static String safeText(JsonNode node) {
        return node == null || !node.isTextual() ? "" : node.asText();
    }

    private static String safeResult(String result) {
        return result == null ? "" : result;
    }

    /** Appends the case id to every problem raised while parsing this case, for grep-friendly diagnostics. */
    private static void appendCaseContext(List<String> problems, String id, JsonNode node, String caseLabel) {
        for (int i = 0; i < problems.size(); i++) {
            String problem = problems.get(i);
            if (problem.startsWith(caseLabel) && !problem.contains("id=")) {
                problems.set(i, problem + " (id=" + id + ")");
            }
        }
    }
}