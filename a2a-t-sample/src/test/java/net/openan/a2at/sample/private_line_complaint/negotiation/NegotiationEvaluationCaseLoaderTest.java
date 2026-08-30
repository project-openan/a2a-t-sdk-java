package net.openan.a2at.sample.private_line_complaint.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.private_line_complaint.negotiation.evaluation.NegotiationEvaluationCase;
import net.openan.a2at.sample.private_line_complaint.negotiation.evaluation.NegotiationEvaluationCaseLoader;
import org.junit.jupiter.api.Test;

class NegotiationEvaluationCaseLoaderTest {

    @Test
    void loadsOneHundredManuallyLabelledCasesAcrossAllGenerationPhases() {
        var cases = NegotiationEvaluationCaseLoader.load();

        assertEquals(100, cases.size());
        assertEquals(
                34,
                cases.stream()
                        .filter(testCase -> testCase.phase().equals("propose"))
                        .count());
        assertEquals(
                33,
                cases.stream()
                        .filter(testCase -> testCase.phase().equals("accept"))
                        .count());
        assertEquals(
                33,
                cases.stream()
                        .filter(testCase -> testCase.phase().equals("reject"))
                        .count());
        assertTrue(cases.stream().allMatch(NegotiationEvaluationCaseLoaderTest::isComplete));
    }

    @Test
    void loadsFocusedReproductionCasesFromTheCanonicalCorpus() {
        var cases = NegotiationEvaluationCaseLoader.loadSelected(List.of("R21", "P01", "A28"));

        assertEquals(
                List.of("R21", "P01", "A28"),
                cases.stream().map(NegotiationEvaluationCase::id).toList());
    }

    @Test
    void loadsTwentyRepresentativeSmokeCasesFromTheCanonicalCorpus() {
        var cases = NegotiationEvaluationCaseLoader.loadSmoke();

        assertEquals(20, cases.size());
        assertEquals(
                NegotiationEvaluationCaseLoader.SMOKE_CASE_IDS,
                cases.stream().map(NegotiationEvaluationCase::id).toList());
        assertEquals(
                7,
                cases.stream()
                        .filter(testCase -> testCase.phase().equals("propose"))
                        .count());
        assertEquals(
                7,
                cases.stream()
                        .filter(testCase -> testCase.phase().equals("accept"))
                        .count());
        assertEquals(
                6,
                cases.stream()
                        .filter(testCase -> testCase.phase().equals("reject"))
                        .count());
    }

    @Test
    void assemblesEveryCorpusEntryIntoAnEndToEndFlow() {
        var flows = NegotiationEvaluationCaseLoader.loadFlows();

        assertEquals(100, flows.size());
        assertEquals(
                50,
                flows.stream().filter(flow -> flow.decision().equals("accept")).count());
        assertEquals(
                50,
                flows.stream().filter(flow -> flow.decision().equals("reject")).count());
        assertTrue(flows.stream().allMatch(flow -> flow.proposeCase().phase().equals("propose")));
        assertTrue(flows.stream().allMatch(flow -> flow.endingCase().phase().equals(flow.decision())));
        assertTrue(
                flows.stream().filter(flow -> flow.decision().equals("reject")).allMatch(flow -> flow.expectedEnding()
                        .keySet()
                        .equals(Map.of("接入端口名称", "", "投诉分类", "").keySet())));
    }

    @Test
    void assemblesTwentySmokeFlowsUsingTheExistingSelectorIds() {
        var flows = NegotiationEvaluationCaseLoader.loadSmokeFlows();

        assertEquals(20, flows.size());
        assertEquals(
                NegotiationEvaluationCaseLoader.SMOKE_CASE_IDS,
                flows.stream().map(flow -> flow.id()).toList());
        assertTrue(flows.stream().anyMatch(flow -> flow.decision().equals("accept")));
        assertTrue(flows.stream().anyMatch(flow -> flow.decision().equals("reject")));
    }

    @Test
    void everyCaseCarriesStructuredDataParallelToItsText() {
        var cases = NegotiationEvaluationCaseLoader.load();

        for (NegotiationEvaluationCase testCase : cases) {
            assertTrue(testCase.data() != null, testCase.id());
            assertTrue(testCase.dataItems().size() >= 1, testCase.id());
            assertTrue(testCase.dataRelationship() == null, testCase.id());
            // the data items reproduce the completedPrompt item block verbatim
            StringBuilder rebuilt = new StringBuilder();
            for (int index = 0; index < testCase.dataItems().size(); index++) {
                if (index > 0) {
                    rebuilt.append('\n');
                }
                rebuilt.append(index + 1)
                        .append(". ")
                        .append(testCase.dataItems().get(index).name())
                        .append('：')
                        .append(testCase.dataItems().get(index).value());
            }
            assertTrue(testCase.completedPrompt().contains(rebuilt.toString()), testCase.id());
            // data item names match the expected keys (both use the display names)
            assertEquals(
                    testCase.expected().keySet(),
                    testCase.dataItems().stream().map(item -> item.name()).collect(java.util.stream.Collectors.toSet()),
                    testCase.id());
        }
    }

    @Test
    void completedPromptsCarryNoNegotiationContextSection() {
        var cases = NegotiationEvaluationCaseLoader.load();

        for (NegotiationEvaluationCase testCase : cases) {
            String prompt = testCase.completedPrompt();
            assertTrue(!prompt.contains("协商上下文"), testCase.id());
            assertTrue(!prompt.contains("{{"), testCase.id());
            assertTrue(
                    prompt.contains(testCase.phase().equals("propose") ? "## 信息协商\n" : "## 信息协商结果\n"), testCase.id());
        }
    }

    private static boolean isComplete(NegotiationEvaluationCase testCase) {
        return !testCase.id().isBlank()
                && !testCase.category().isBlank()
                && !testCase.text().isBlank()
                && testCase.completedPrompt() != null
                && !testCase.completedPrompt().isBlank()
                && testCase.expected() != null
                && !testCase.expected().isEmpty()
                && expectedKeys().equals(testCase.expected().keySet());
    }

    private static java.util.Set<String> expectedKeys() {
        return Map.of("接入端口名称", "", "投诉分类", "").keySet();
    }
}
