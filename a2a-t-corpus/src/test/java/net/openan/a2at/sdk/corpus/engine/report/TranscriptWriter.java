package net.openan.a2at.sdk.corpus.engine.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.corpus.engine.engine.WorkflowEngine;

/**
 * Writes the execution transcript of one scenario to {@code output_result_<flow>.json}.
 *
 * <p>Each run fully overwrites the file: it reflects exactly the cases executed in that run (a filtered run therefore
 * contains only the matching cases). The default destination is the scenario directory itself (input and output live
 * side by side for review and the CSV tooling); {@code -Dcorpus.output.dir=<dir>} redirects every scenario output
 * into one shared directory while keeping the per-scenario layout.
 */
public final class TranscriptWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TranscriptWriter() {}

    public static Path writeScenarioOutput(
            String flowType, String scenarioName, Path defaultScenarioDir, List<WorkflowEngine.CaseResult> results) {
        Path outputDir = outputDir(scenarioName, defaultScenarioDir);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to create the output directory: " + outputDir, error);
        }
        Path file = outputDir.resolve("output_result_" + flowType + ".json");
        List<Map<String, Object>> records = new ArrayList<>();
        for (WorkflowEngine.CaseResult result : results) {
            records.add(toRecord(result));
        }
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), records);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to write the run results: " + file, error);
        }
        return file;
    }

    private static Map<String, Object> toRecord(WorkflowEngine.CaseResult result) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", result.inputCase().id());
        record.put("caseDimension", result.inputCase().caseDimension());
        record.put("caseDesc", result.inputCase().caseDesc());
        record.put("caseCategory", result.inputCase().caseCategory());
        record.put("totalTime", result.totalDurationMs());
        record.put("totalInputToken", result.totalInputToken());
        record.put("totalOutputToken", result.totalOutputToken());
        record.put("totalToken", result.totalInputToken() + result.totalOutputToken());
        record.put("result", result.verdict());
        if (!result.failReason().isEmpty()) {
            record.put("failReason", result.failReason());
        }
        Map<String, Object> output = new LinkedHashMap<>();
        List<Map<String, Object>> interactions = new ArrayList<>();
        for (WorkflowEngine.Interaction interaction : result.interactions()) {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("step", String.valueOf(interaction.step()));
            serialized.put("type", interaction.type());
            serialized.put("result", interaction.result());
            serialized.put("duration_ms", interaction.durationMs());
            serialized.put("request", interaction.request());
            serialized.put("response", interaction.response());
            serialized.put("inputToken", interaction.inputToken());
            serialized.put("outputToken", interaction.outputToken());
            interactions.add(serialized);
        }
        output.put("interactions", interactions);
        record.put("output", output);
        return record;
    }

    static Path outputDir(String scenarioName, Path defaultScenarioDir) {
        String override = System.getProperty("corpus.output.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize().resolve(scenarioName);
        }
        return defaultScenarioDir;
    }
}