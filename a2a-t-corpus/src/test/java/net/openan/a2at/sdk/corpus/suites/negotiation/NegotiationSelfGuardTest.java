package net.openan.a2at.sdk.corpus.suites.negotiation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.openan.a2at.sdk.corpus.engine.CorpusWorkFlowSuite;
import net.openan.a2at.sdk.corpus.engine.discover.ScenarioScanner;
import net.openan.a2at.sdk.corpus.engine.loader.CaseFileLoader;
import net.openan.a2at.sdk.corpus.engine.loader.ExpectedStep;
import net.openan.a2at.sdk.corpus.engine.loader.InputCase;
import org.junit.jupiter.api.Test;

/**
 * Negotiation-T corpus self-guard: validates every Negotiation-T scenario case file structurally without any LLM call.
 * Unknown keys, misaligned expectations, unknown api names, out-of-catalog error codes and category/expectation
 * contradictions all fail here.
 */
public final class NegotiationSelfGuardTest {

    private static final String CATEGORY_NORMAL = "正常";
    private static final String CATEGORY_EXCEPTION = "异常";

    @Test
    void negotiationScenarioCaseFilesAreStructurallyValid() {
        List<String> problems = new ArrayList<>();
        for (String flowFile : List.of(
                CorpusWorkFlowSuite.INPUT_CASE_FROM_TEXT, CorpusWorkFlowSuite.INPUT_CASE_FROM_DATA)) {
            for (ScenarioScanner.Scenario scenario : ScenarioScanner.discover("negotiation", flowFile)) {
                List<InputCase> cases = new CaseFileLoader()
                        .load(scenario.inputFile(flowFile), CorpusWorkFlowSuite.negotiationApiNames());
                for (InputCase inputCase : cases) {
                    checkCategoryContract(inputCase, problems);
                }
            }
        }
        assertTrue(problems.isEmpty(), "Negotiation self-guard failures:\n- " + String.join("\n- ", problems));
    }

    private static void checkCategoryContract(InputCase inputCase, List<String> problems) {
        long errorExpected = 0;
        for (ExpectedStep expectedStep : inputCase.expected()) {
            if ("error".equals(expectedStep.result())) {
                errorExpected++;
                if (expectedStep.errCode().isBlank()) {
                    problems.add(inputCase.id() + ": error expectations must declare an errCode from the SDK error catalog");
                }
            }
        }
        if (CATEGORY_EXCEPTION.equals(inputCase.caseCategory()) && errorExpected == 0) {
            problems.add(inputCase.id() + ": categorized as " + CATEGORY_EXCEPTION + " but no step expects an error");
        }
        if (CATEGORY_NORMAL.equals(inputCase.caseCategory()) && errorExpected > 0) {
            problems.add(inputCase.id() + ": categorized as " + CATEGORY_NORMAL + " but a step expects an error; use "
                    + CATEGORY_EXCEPTION + " or \"boundary\"");
        }
    }
}