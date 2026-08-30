package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads the checked-in, manually labelled Qwen evaluation corpus. */
public final class NegotiationEvaluationCaseLoader {

    public static final String RESOURCE_PATH = "sample/private-line-complaint-negotiation/evaluation/cases.json";
    /**
     * A representative 20-case set for routine smoke validation. It includes every generation and validation/filling
     * phase, and spans common, short, contextual, mixed-language, noisy, and business-oriented expressions.
     */
    public static final List<String> SMOKE_CASE_IDS = List.of(
            "P01", "P05", "P11", "P14", "P16", "P21", "P27", "A01", "A05", "A11", "A16", "A21", "A28", "A33", "R01",
            "R05", "R11", "R16", "R21", "R27");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private NegotiationEvaluationCaseLoader() {}

    public static List<NegotiationEvaluationCase> load() {
        try (InputStream input =
                NegotiationEvaluationCaseLoader.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Negotiation evaluation corpus not found: " + RESOURCE_PATH);
            }
            List<NegotiationEvaluationCase> cases = OBJECT_MAPPER.readValue(input, new TypeReference<>() {});
            if (cases.size() != 100) {
                throw new IllegalStateException("Negotiation evaluation corpus must contain exactly 100 cases");
            }
            return List.copyOf(cases);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read negotiation evaluation corpus", exception);
        }
    }

    /**
     * Loads a named subset from the checked-in corpus for focused problem reproduction.
     *
     * @param caseIds case identifiers, in desired execution order
     * @return selected cases
     */
    public static List<NegotiationEvaluationCase> loadSelected(List<String> caseIds) {
        if (caseIds.isEmpty()) {
            throw new IllegalArgumentException("At least one evaluation case ID is required");
        }
        Set<String> requested = new LinkedHashSet<>(caseIds);
        if (requested.size() != caseIds.size()) {
            throw new IllegalArgumentException("Duplicate negotiation evaluation case IDs are not allowed: " + caseIds);
        }
        var casesById = load().stream()
                .collect(java.util.stream.Collectors.toMap(NegotiationEvaluationCase::id, testCase -> testCase));
        Set<String> unknown = new LinkedHashSet<>(requested);
        unknown.removeAll(casesById.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown negotiation evaluation case IDs: " + unknown);
        }
        return caseIds.stream().map(casesById::get).toList();
    }

    /** Loads the checked-in 20-case smoke set. */
    public static List<NegotiationEvaluationCase> loadSmoke() {
        return loadSelected(SMOKE_CASE_IDS);
    }

    /**
     * Assembles every labelled input into a complete propose-to-decision flow. A propose-labelled input drives the
     * first generation step and is paired with a deterministic ending case; an accept/reject-labelled input drives the
     * ending step and is paired with a deterministic propose case. This preserves the original 100 IDs while exercising
     * two matching API pairs in every run.
     */
    public static List<NegotiationEvaluationFlowCase> loadFlows() {
        List<NegotiationEvaluationCase> cases = load();
        List<NegotiationEvaluationCase> proposeCases = byPhase(cases, "propose");
        List<NegotiationEvaluationCase> acceptCases = byPhase(cases, "accept");
        List<NegotiationEvaluationCase> rejectCases = byPhase(cases, "reject");
        return java.util.stream.IntStream.range(0, cases.size())
                .mapToObj(index -> toFlow(cases.get(index), index, proposeCases, acceptCases, rejectCases))
                .toList();
    }

    public static List<NegotiationEvaluationFlowCase> loadSelectedFlows(List<String> caseIds) {
        if (caseIds.isEmpty()) {
            throw new IllegalArgumentException("At least one evaluation case ID is required");
        }
        Map<String, NegotiationEvaluationFlowCase> flowsById =
                loadFlows().stream().collect(Collectors.toMap(NegotiationEvaluationFlowCase::id, Function.identity()));
        Set<String> requested = new LinkedHashSet<>(caseIds);
        if (requested.size() != caseIds.size()) {
            throw new IllegalArgumentException("Duplicate negotiation evaluation case IDs are not allowed: " + caseIds);
        }
        Set<String> unknown = new LinkedHashSet<>(requested);
        unknown.removeAll(flowsById.keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown negotiation evaluation case IDs: " + unknown);
        }
        return caseIds.stream().map(flowsById::get).toList();
    }

    public static List<NegotiationEvaluationFlowCase> loadSmokeFlows() {
        return loadSelectedFlows(SMOKE_CASE_IDS);
    }

    private static List<NegotiationEvaluationCase> byPhase(List<NegotiationEvaluationCase> cases, String phase) {
        return cases.stream().filter(testCase -> phase.equals(testCase.phase())).toList();
    }

    private static NegotiationEvaluationFlowCase toFlow(
            NegotiationEvaluationCase source,
            int index,
            List<NegotiationEvaluationCase> proposeCases,
            List<NegotiationEvaluationCase> acceptCases,
            List<NegotiationEvaluationCase> rejectCases) {
        NegotiationEvaluationCase propose =
                "propose".equals(source.phase()) ? source : proposeCases.get(index % proposeCases.size());
        boolean accept = "accept".equals(source.phase()) || ("propose".equals(source.phase()) && index % 2 == 0);
        NegotiationEvaluationCase ending = "propose".equals(source.phase())
                ? (accept ? acceptCases : rejectCases).get(index % (accept ? acceptCases.size() : rejectCases.size()))
                : source;
        return new NegotiationEvaluationFlowCase(
                source.id(), source.category(), accept ? "accept" : "reject", propose, ending);
    }
}
