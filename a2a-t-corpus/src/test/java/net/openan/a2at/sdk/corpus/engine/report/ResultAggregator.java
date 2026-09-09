package net.openan.a2at.sdk.corpus.engine.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.corpus.engine.engine.WorkflowEngine;

/**
 * Aggregates one run into accuracy / latency / token statistics and writes {@code summary_report_<flow>.json} plus a
 * console table. Accuracy rows group by overall, scenario, case dimension, case category and (for step-level health)
 * API method; the 95% target line is marked per row.
 */
public final class ResultAggregator {

    /** 95%+ accuracy target required by the accuracy verification directive. */
    public static final double ACCURACY_TARGET = 0.95;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResultAggregator() {}

    /** Computes and persists the run summary; returns the written file. */
    public static Path writeSummary(String flowType, List<WorkflowEngine.CaseResult> results) {
        Map<String, Object> summary = summarize(results);
        Path dir = summaryDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to create the summary directory: " + dir, error);
        }
        Path file = dir.resolve("summary_report_" + flowType + ".json");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), summary);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to write the summary: " + file, error);
        }
        printTable(flowType, summary);
        return file;
    }

    /**
     * Prints one case result as it completes so every executed case - including failures - is visible in the console
     * log of the run. Failed or crashed cases additionally print their full interaction trace.
     */
    public static void printCaseLine(WorkflowEngine.CaseResult result) {
        String fail = result.failReason().isEmpty()
                ? ""
                : " | fail: " + result.failReason();
        System.out.printf(
                "[case] %s/%s => %s | %dms | tokens %d%s%n",
                result.scenario(),
                result.inputCase().id(),
                result.verdict(),
                result.totalDurationMs(),
                result.totalInputToken() + result.totalOutputToken(),
                fail);
        if ("success".equals(result.verdict())) {
            return;
        }
        for (WorkflowEngine.Interaction interaction : result.interactions()) {
            String detail = "";
            Map<String, Object> response = interaction.response();
            if (response != null) {
                Object code = response.get("errorCode");
                Object message = response.get("message");
                if (code != null || message != null) {
                    detail = " | " + (code == null ? "" : code) + " " + abbreviate(
                            String.valueOf(message), 160);
                }
            }
            System.out.printf(
                    "    s%-4d %-30s %-8s %6dms in=%d out=%d%s%n",
                    interaction.step(),
                    interaction.type(),
                    interaction.result(),
                    interaction.durationMs(),
                    interaction.inputToken(),
                    interaction.outputToken(),
                    detail);
        }
    }

    static Map<String, Object> summarize(List<WorkflowEngine.CaseResult> results) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseTotal", results.size());
        summary.put("accuracyTarget", ACCURACY_TARGET);
        summary.put("rows", caseRows(results));
        summary.put("apiStepRows", apiStepRows(results));
        summary.put("durationMs", percentileStats(stepDurations(results)));
        summary.put("tokens", tokenStats(results));
        return summary;
    }

    private static List<Map<String, Object>> caseRows(List<WorkflowEngine.CaseResult> results) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (WorkflowEngine.CaseResult result : results) {
            bucket(buckets, "overall", "overall").count(result);
            bucket(buckets, "scenario", result.scenario()).count(result);
            bucket(buckets, "dimension", result.inputCase().caseDimension()).count(result);
            bucket(buckets, "category", result.inputCase().caseCategory()).count(result);
        }
        return bucketRows(buckets);
    }

    private static List<Map<String, Object>> apiStepRows(List<WorkflowEngine.CaseResult> results) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (WorkflowEngine.CaseResult result : results) {
            for (WorkflowEngine.Interaction interaction : result.interactions()) {
                if ("LLM".equals(interaction.type())) {
                    continue;
                }
                if ("skipped".equals(interaction.result())) {
                    continue;
                }
                bucket(buckets, "api", interaction.type()).count("success".equals(interaction.result()));
            }
        }
        return bucketRows(buckets);
    }

    private static final class Bucket {

        final String scope;

        final String key;

        long total;

        long success;

        Bucket(String scope, String key) {
            this.scope = scope;
            this.key = key;
        }

        void count(WorkflowEngine.CaseResult result) {
            total++;
            if ("success".equals(result.verdict())) {
                success++;
            }
        }

        void count(boolean passed) {
            total++;
            if (passed) {
                success++;
            }
        }
    }

    private static Bucket bucket(Map<String, Bucket> buckets, String scope, String key) {
        return buckets.computeIfAbsent(scope + "/" + key, ignored -> new Bucket(scope, key));
    }

    private static List<Map<String, Object>> bucketRows(Map<String, Bucket> buckets) {
        List<Map<String, Object>> rows = new ArrayList<>();
        buckets.values().stream()
                .sorted(Comparator.comparing((Bucket b) -> b.scope).thenComparing(b -> b.key))
                .forEach(bucket -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("scope", bucket.scope);
                    row.put("key", bucket.key);
                    row.put("total", bucket.total);
                    row.put("success", bucket.success);
                    row.put("failure", bucket.total - bucket.success);
                    row.put("accuracy", percentage(bucket.success, bucket.total).doubleValue());
                    row.put("meetsTarget", bucket.total > 0 && percentage(bucket.success, bucket.total)
                            .doubleValue() >= ACCURACY_TARGET);
                    rows.add(row);
                });
        return rows;
    }

    private static List<Long> stepDurations(List<WorkflowEngine.CaseResult> results) {
        List<Long> durations = new ArrayList<>();
        for (WorkflowEngine.CaseResult result : results) {
            for (WorkflowEngine.Interaction interaction : result.interactions()) {
                if (!"LLM".equals(interaction.type())) {
                    durations.add(interaction.durationMs());
                }
            }
        }
        return durations;
    }

    private static Map<String, Object> percentileStats(List<Long> durations) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", durations.size());
        if (!durations.isEmpty()) {
            List<Long> sorted = durations.stream().sorted().toList();
            stats.put("p50", sorted.get(Math.min(sorted.size() - 1, sorted.size() * 50 / 100)));
            stats.put("p95", sorted.get(Math.min(sorted.size() - 1, sorted.size() * 95 / 100)));
            stats.put("max", sorted.get(sorted.size() - 1));
        }
        return stats;
    }

    private static Map<String, Object> tokenStats(List<WorkflowEngine.CaseResult> results) {
        long input = 0;
        long output = 0;
        for (WorkflowEngine.CaseResult result : results) {
            input += result.totalInputToken();
            output += result.totalOutputToken();
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalInputToken", input);
        stats.put("totalOutputToken", output);
        stats.put("totalToken", input + output);
        stats.put("meanInputTokenPerCase", results.isEmpty() ? 0 : Math.round((double) input / results.size()));
        stats.put("meanOutputTokenPerCase", results.isEmpty() ? 0 : Math.round((double) output / results.size()));
        return stats;
    }

    private static BigDecimal percentage(long success, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(success)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static Path summaryDir() {
        String override = System.getProperty("corpus.output.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir", "."), "target", "corpus").toAbsolutePath().normalize();
    }

    @SuppressWarnings("unchecked")
    private static void printTable(String flowType, Map<String, Object> summary) {
        System.out.println();
        System.out.println("===== a2a-t-corpus run summary (" + flowType + ", target "
                + ACCURACY_TARGET * 100 + "%)=====");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) summary.get("rows");
        System.out.printf("%-12s %-30s %6s %8s %9s %8s%n", "scope", "key", "total", "success", "accuracy", "meets");
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                System.out.printf("%-12s %-30s %6d %8d %9.2f%% %8s%n",
                        row.get("scope"),
                        abbreviate(String.valueOf(row.get("key")), 30),
                        ((Number) row.get("total")).longValue(),
                        ((Number) row.get("success")).longValue(),
                        ((Number) row.get("accuracy")).doubleValue(),
                        Boolean.TRUE.equals(row.get("meetsTarget")) ? "YES" : "NO");
            }
        }
        Map<String, Object> tokens = (Map<String, Object>) summary.get("tokens");
        if (tokens != null) {
            System.out.printf("Token totals: input=%d output=%d total=%d%n",
                    ((Number) tokens.get("totalInputToken")).longValue(),
                    ((Number) tokens.get("totalOutputToken")).longValue(),
                    ((Number) tokens.get("totalToken")).longValue());
        }
    }

    private static String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}