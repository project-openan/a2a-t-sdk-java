package net.openan.a2at.sdk.corpus.engine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import net.openan.a2at.sdk.corpus.engine.discover.ScenarioScanner;
import net.openan.a2at.sdk.corpus.engine.engine.WorkflowEngine;
import net.openan.a2at.sdk.corpus.engine.loader.CaseFileLoader;
import net.openan.a2at.sdk.corpus.engine.loader.InputCase;
import net.openan.a2at.sdk.corpus.engine.registry.ApiRegistry;
import net.openan.a2at.sdk.corpus.engine.registry.ClientApis;
import net.openan.a2at.sdk.corpus.engine.registry.SdkRuntimeAssembler;
import net.openan.a2at.sdk.corpus.engine.registry.ServerApis;
import net.openan.a2at.sdk.corpus.engine.report.ResultAggregator;
import net.openan.a2at.sdk.corpus.engine.report.TranscriptWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base class of every {@code -T} workflow suite. A concrete suite declares only its extension folder, its workflow
 * type (from_text / from_data) and the consumed case file; scenario discovery, case loading, execution, expectation
 * comparison and transcript/summary writing are all inherited.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class CorpusWorkFlowSuite {

    public static final String INPUT_CASE_FROM_TEXT = "input_case_from_text.json";

    public static final String INPUT_CASE_FROM_DATA = "input_case_from_data.json";

    public static final String FLOW_FROM_TEXT = "from_text";

    public static final String FLOW_FROM_DATA = "from_data";

    public static final String API_GENERATE_TASK_FROM_TEXT = "generateTaskPromptFromText";

    public static final String API_GENERATE_TASK_FROM_DATA = "generateTaskPromptFromDataWithSchema";

    public static final String API_VALIDATE_TASK = "validateTaskPromptAndDataFilling";

    private static volatile SdkRuntimeAssembler.Runtime runtime;

    private final List<WorkflowEngine.CaseResult> results = new ArrayList<>();

    private final Map<String, Path> scenarioDirs = new LinkedHashMap<>();

    /** Extension resource folder under the corpus package, e.g. {@code task}. */
    protected abstract String extensionFolder();

    /** Consumed case file, e.g. {@code input_case_from_text.json}. */
    protected abstract String inputFileName();

    /** Flow type used for the transcript and summary file names: {@code from_text} or {@code from_data}. */
    protected abstract String flowType();

    @TestFactory
    Stream<DynamicTest> corpusCases() {
        SdkRuntimeAssembler.Runtime runtime = runtime();
        ApiRegistry registry = taskRegistry(runtime);
        WorkflowEngine engine = new WorkflowEngine(registry, runtime.recorder());

        List<ScenarioScanner.Scenario> scenarios = ScenarioScanner.filterScenarios(
                ScenarioScanner.discover(extensionFolder(), inputFileName()),
                System.getProperty("corpus.scenario"));
        String caseFilter = System.getProperty("case.filter");

        List<DynamicTest> tests = new ArrayList<>();
        for (ScenarioScanner.Scenario scenario : scenarios) {
            List<InputCase> cases =
                    new CaseFileLoader().load(scenario.inputFile(inputFileName()), registry.apiNames());
            scenarioDirs.put(scenario.name(), scenario.sourceDir());
            for (InputCase inputCase : cases) {
                if (!ScenarioScanner.matchesCaseFilter(caseFilter, inputCase.id())) {
                    continue;
                }
                tests.add(DynamicTest.dynamicTest(scenario.name() + "/" + inputCase.id(), () -> {
                    WorkflowEngine.CaseResult result = engine.run(scenario.name(), inputCase);
                    results.add(result);
                    ResultAggregator.printCaseLine(result);
                    assertEquals(
                            "success",
                            result.verdict(),
                            () -> result.failReason().isEmpty()
                                    ? "case verdict=" + result.verdict() + ", see the output JSON"
                                    : result.failReason());
                }));
            }
        }
        return tests.stream();
    }

    @AfterAll
    void writeTranscripts() {
        for (Map.Entry<String, List<WorkflowEngine.CaseResult>> entry : groupByScenario(results).entrySet()) {
            Path resultFile = TranscriptWriter.writeScenarioOutput(
                    flowType(), entry.getKey(), scenarioDirs.get(entry.getKey()), entry.getValue());
            System.out.println("Output: " + resultFile);
        }
        Path summaryFile = ResultAggregator.writeSummary(flowType(), results);
        System.out.println("Summary: " + summaryFile);
    }

    private static synchronized SdkRuntimeAssembler.Runtime runtime() {
        if (runtime == null) {
            runtime = SdkRuntimeAssembler.taskRuntime();
        }
        return runtime;
    }

    private static Map<String, List<WorkflowEngine.CaseResult>> groupByScenario(
            List<WorkflowEngine.CaseResult> allResults) {
        Map<String, List<WorkflowEngine.CaseResult>> grouped = new LinkedHashMap<>();
        for (WorkflowEngine.CaseResult result : allResults) {
            grouped.computeIfAbsent(result.scenario(), ignored -> new ArrayList<>()).add(result);
        }
        return grouped;
    }

    /** Registers the Task-T phase-1 registry entries. */
    public static ApiRegistry taskRegistry(SdkRuntimeAssembler.Runtime runtime) {
        ApiRegistry registry = new ApiRegistry();
        ClientApis.registerTaskApis(registry, runtime.clientGeneration());
        ServerApis.registerTaskApis(registry, runtime.taskValidator());
        return registry;
    }

    /** Task-T phase-1 API names, used for load-time membership checks without touching the LLM runtime. */
    public static Set<String> taskApiNames() {
        return Set.of(API_GENERATE_TASK_FROM_TEXT, API_GENERATE_TASK_FROM_DATA, API_VALIDATE_TASK);
    }
}