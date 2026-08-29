package net.openan.a2at.sample.task_t;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * End-to-end accuracy demo for the {@code Task-T} extension.
 *
 * <p>The demo closes the loop between the client facade and the server facade over the
 * {@code Task-T/network-layer/private-line-complaint/v1} template (transfer / private-line business complaint
 * diagnosis) in two independent cases:
 *
 * <ol>
 *   <li>{@code A2ATClient#generateTaskPromptFromText} fed with natural language;
 *   <li>{@code A2ATClient#generateTaskPromptFromDataWithSchema} fed with a slot-value map plus a semantics schema;
 *   <li>{@code generateTaskPromptFromText} fed with key-slot-missing content, expected to be rejected by the
 *       server-side semantic validation.
 * </ol>
 *
 * <p>Each generated prompt is passed to {@code A2ATServer#validateTaskPromptAndDataFilling}; for cases one and two the
 * extracted parameters are compared against the sample ground truth and the results are aggregated into a per-case
 * field accuracy and sample pass rate, while case three reports a server-side validation rejection rate and an overall
 * interception rate (server rejection plus client-side generation block) over the negative samples.
 *
 * <p>Run with {@code java ... TaskTDemoMain [env-file-path] [case]}. The env file resolves as follows: an explicit
 * first argument wins; otherwise a {@code client.env} in the working directory (repo root) that carries the required
 * LLM keys; otherwise the bundled sample template {@code a2a-t-sample/src/main/resources/sample/task_t/client.env}. A
 * working-directory {@code client.env} is only honored when it defines non-blank {@code A2AT_LLM_PROVIDER},
 * {@code A2AT_LLM_MODEL} and {@code A2AT_LLM_API_KEY} (cf. the leftover {@code subscribe_incident} template in newer
 * checkouts would otherwise shadow the Task-T sample); otherwise the bundled template is used. The env must configure a
 * reachable OpenAI-compatible LLM ({@code A2AT_LLM_API_KEY}, {@code A2AT_LLM_BASE_URL}, {@code A2AT_LLM_MODEL},
 * {@code A2AT_LLM_PROVIDER=openai}) — the server-side semantic validation performs one LLM call per sample.
 *
 * <p>The second optional argument selects which case to run: {@code text} (case one), {@code data} (case two),
 * {@code rejection} (case three) or {@code all} (default); when a single case is selected only its summary is printed.
 */
public final class TaskTDemoMain {

    private static final String DEFAULT_ENV_FILE = "client.env";

    private static final String BUNDLED_ENV_FILE = Path.of(
                    "a2a-t-sample", "src", "main", "resources", "sample", "task_t", "client.env")
            .toString();

    private static final List<String> REQUIRED_LLM_KEYS =
            List.of("A2AT_LLM_PROVIDER", "A2AT_LLM_MODEL", "A2AT_LLM_API_KEY");

    private static final String CASE_TEXT = "text";

    private static final String CASE_DATA = "data";

    private static final String CASE_REJECTION = "rejection";

    private static final String CASE_ALL = "all";

    private TaskTDemoMain() {}

    /**
     * Runs one or all client-API cases against the built-in private-line complaint diagnosis samples.
     *
     * @param args first optional argument is the {@code .env} file path; second optional argument selects the case
     *     ({@code text}, {@code data}, {@code rejection} or {@code all})
     */
    public static void main(String[] args) {
        Path envPath = resolveEnvPath(args);
        String caseSelection = resolveCaseSelection(args);
        println("Task-T 准确率验证样例，env: " + envPath.toAbsolutePath() + (Files.exists(envPath) ? "" : "  (不存在，请先配置)"));
        println("模板: " + StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
        println("用例: " + caseLabel(caseSelection));
        println();

        A2ATClient client = new A2ATClient(envPath);
        A2ATServer server = new A2ATServer(envPath);

        if (CASE_TEXT.equals(caseSelection) || CASE_ALL.equals(caseSelection)) {
            List<TaskTAccuracyEvaluator.SampleScore> textScores = runTextCase(client, server);
            println();
            System.out.println("══════════════════════════════════════════════════════");
            printSummary(TaskTAccuracyEvaluator.summarize("generateTaskPromptFromText", textScores));
            println();
        }
        if (CASE_DATA.equals(caseSelection) || CASE_ALL.equals(caseSelection)) {
            List<TaskTAccuracyEvaluator.SampleScore> dataScores = runDataWithSchemaCase(client, server);
            println();
            System.out.println("══════════════════════════════════════════════════════");
            printSummary(TaskTAccuracyEvaluator.summarize("generateTaskPromptFromDataWithSchema", dataScores));
            println();
        }
        if (CASE_REJECTION.equals(caseSelection) || CASE_ALL.equals(caseSelection)) {
            RejectionSummary rejectionSummary = runRejectionCase(client, server);
            println();
            System.out.println("══════════════════════════════════════════════════════");
            printRejectionSummary(rejectionSummary);
            println();
        }
    }

    private static String resolveCaseSelection(String[] args) {
        if (args.length > 1) {
            String selection = args[1].toLowerCase(Locale.ROOT);
            if (CASE_TEXT.equals(selection)
                    || CASE_DATA.equals(selection)
                    || CASE_REJECTION.equals(selection)
                    || CASE_ALL.equals(selection)) {
                return selection;
            }
            System.err.println("未知用例参数: " + args[1] + "，可选值: text | data | rejection | all，将运行全部用例");
            return CASE_ALL;
        }
        return CASE_ALL;
    }

    private static String caseLabel(String caseSelection) {
        return switch (caseSelection) {
            case CASE_TEXT -> "用例一：generateTaskPromptFromText";
            case CASE_DATA -> "用例二：generateTaskPromptFromDataWithSchema";
            case CASE_REJECTION -> "用例三：缺少关键槽位拒绝用例";
            default -> "全部（用例一 + 用例二 + 用例三）";
        };
    }

    private static List<TaskTAccuracyEvaluator.SampleScore> runTextCase(A2ATClient client, A2ATServer server) {
        println("================ 用例一：generateTaskPromptFromText ================");
        List<TaskTSample> samples = TaskTPrivateLineComplaintSamples.textSamples();
        List<TaskTAccuracyEvaluator.SampleScore> scores = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            TaskTSample sample = samples.get(i);
            printSampleHeader("用例一", i, samples.size(), sample.name());
            println("[输入] 自然语言文本:");
            println(sample.text());
            println();
            try {
                MetadataContent metadata =
                        client.generateTaskPromptFromText(sample.text(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
                printGeneratedMetadata(metadata);
                TaskTAccuracyEvaluator.SampleScore score = validateAndScore(server, sample, metadata, "用例一");
                scores.add(score);
            } catch (PromptGenerationException exception) {
                printFailure("生成失败", exception);
                scores.add(new TaskTAccuracyEvaluator.SampleScore(sample.name(), false, List.of()));
            }
            println();
        }
        return scores;
    }

    private static List<TaskTAccuracyEvaluator.SampleScore> runDataWithSchemaCase(
            A2ATClient client, A2ATServer server) {
        println("========== 用例二：generateTaskPromptFromDataWithSchema ==========");
        List<TaskTSample> samples = TaskTPrivateLineComplaintSamples.dataWithSchemaSamples();
        List<TaskTAccuracyEvaluator.SampleScore> scores = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            TaskTSample sample = samples.get(i);
            printSampleHeader("用例二", i, samples.size(), sample.name());
            println("[输入] 数据(data):");
            println(pretty(sample.data()));
            println("[输入] 语义schema(schema):");
            println(pretty(sample.semanticsSchema()));
            println();
            try {
                MetadataContent metadata = client.generateTaskPromptFromDataWithSchema(
                        sample.data(), sample.semanticsSchema(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
                printGeneratedMetadata(metadata);
                TaskTAccuracyEvaluator.SampleScore score = validateAndScore(server, sample, metadata, "用例二");
                scores.add(score);
            } catch (PromptGenerationException exception) {
                printFailure("生成失败", exception);
                scores.add(new TaskTAccuracyEvaluator.SampleScore(sample.name(), false, List.of()));
            }
            println();
        }
        return scores;
    }

    /**
     * Runs the rejection case over {@link TaskTPrivateLineComplaintSamples#rejectionSamples()}: each sample
     * deliberately omits one or more key slots (or carries an invalid slot value), so the server-side semantic
     * validation is expected to fail with {@code negotiation.semantic_rejected}. The sample set mixes a text variant
     * (fed to {@code generateTaskPromptFromText}) and a data variant (fed to
     * {@code generateTaskPromptFromDataWithSchema}); a sample is counted as "server-rejected" only when the validation
     * throws {@link ContentValidationException} — the structured slot errors are printed per sample — while a failure
     * already at prompt generation counts as a client-side block. The run aggregates a server-side validation rejection
     * rate (server-rejected over total) side by side with an overall interception rate (server-rejected plus
     * client-blocked over total), so the validation point itself can be read independently of client-side blocks.
     */
    private static RejectionSummary runRejectionCase(A2ATClient client, A2ATServer server) {
        println("========== 用例三：缺少关键槽位拒绝用例（期望 rejected） ==========");
        List<TaskTRejectionSample> samples = TaskTPrivateLineComplaintSamples.rejectionSamples();
        int serverRejected = 0;
        int clientBlocked = 0;
        int unexpectedlyPassed = 0;
        for (int i = 0; i < samples.size(); i++) {
            TaskTRejectionSample sample = samples.get(i);
            printSampleHeader("用例三", i, samples.size(), sample.name());
            try {
                MetadataContent metadata;
                if (sample.data() != null) {
                    println("[输入] 数据(data，客户端 key，缺关键槽位):");
                    println(pretty(sample.data()));
                    println("[输入] 语义schema(schema):");
                    println(pretty(sample.semanticsSchema()));
                    println();
                    println("[生成] generateTaskPromptFromDataWithSchema:");
                    metadata = client.generateTaskPromptFromDataWithSchema(
                            sample.data(), sample.semanticsSchema(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
                } else {
                    println("[输入] 自然语言文本 (缺少关键槽位):");
                    println(sample.text());
                    println();
                    println("[生成] generateTaskPromptFromText:");
                    metadata =
                            client.generateTaskPromptFromText(sample.text(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
                }
                System.out.println("  templateUri : " + metadata.templateUri());
                System.out.println("  promptText : " + metadata.promptText());
                System.out.println("  extensionUri : " + metadata.extensionUri());
                println();
                Map<String, Object> extracted = server.validateTaskPromptAndDataFilling(
                                metadata.promptText(),
                                sample.validationSchema(),
                                StandardTemplates.PRIVATE_LINE_COMPLAINT_URI)
                        .data();
                unexpectedlyPassed++;
                println("[意外通过] 应被拒绝却校验通过，提取参数:");
                println(pretty(extracted));
            } catch (ContentValidationException exception) {
                serverRejected++;
                System.out.println("[正确拒绝] [" + exception.getCode() + "] " + exception.getMessage());
                printValidationErrors(exception);
            } catch (PromptGenerationException exception) {
                clientBlocked++;
                System.out.println("[生成阶段拦截] 客户端生成即失败，[" + exception.getCode() + "] " + exception.getMessage());
            }
            println();
        }
        return new RejectionSummary(samples.size(), serverRejected, clientBlocked, unexpectedlyPassed);
    }

    private static void printValidationErrors(ContentValidationException exception) {
        List<SlotValidationError> errors = exception.errors();
        if (errors == null || errors.isEmpty()) {
            println("  (无结构化错误明细)");
            return;
        }
        for (SlotValidationError error : errors) {
            println("    slot=" + error.slotName() + " code=" + error.code() + " message=" + error.message());
        }
    }

    private static TaskTAccuracyEvaluator.SampleScore validateAndScore(
            A2ATServer server, TaskTSample sample, MetadataContent metadata, String caseLabel) {
        try {
            Map<String, Object> extracted = server.validateTaskPromptAndDataFilling(
                            metadata.promptText(), sample.validationSchema(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI)
                    .data();
            println("[服务端] " + caseLabel + " validateTaskPromptAndDataFilling 通过，提取参数:");
            println(pretty(extracted));
            List<TaskTAccuracyEvaluator.FieldScore> fields = TaskTAccuracyEvaluator.scoreFields(sample, extracted);
            printFieldScores(fields);
            return new TaskTAccuracyEvaluator.SampleScore(sample.name(), true, fields);
        } catch (ContentValidationException exception) {
            printFailure("校验不通过", exception);
            return new TaskTAccuracyEvaluator.SampleScore(sample.name(), false, List.of());
        }
    }

    private static void printSampleHeader(String caseLabel, int index, int total, String name) {
        System.out.println(
                "──────────── " + caseLabel + " 样本[" + (index + 1) + "/" + total + "] " + name + " ────────────");
    }

    private static void printGeneratedMetadata(MetadataContent metadata) {
        println("[生成] MetadataContent:");
        println("  extensionUri: " + metadata.extensionUri());
        println("  templateUri : " + metadata.templateUri());
        println("  promptText  : ");
        println(metadata.promptText());
    }

    private static void printFieldScores(List<TaskTAccuracyEvaluator.FieldScore> fields) {
        println("[比对] 字段级命中 (命中规则: 归一化后相同或互相包含):");
        for (TaskTAccuracyEvaluator.FieldScore field : fields) {
            String mark = field.matched() ? "✔ 命中" : "✘ 未命中";
            String detail = field.matched() ? "" : "  " + field.detail();
            println("  " + mark + " " + field.slot() + detail);
        }
    }

    private static void printFailure(String stage, A2ATError error) {
        println("[失败] " + stage + ": [" + error.getCode() + "] " + error.getMessage());
        if (error instanceof ContentValidationException validationException) {
            List<SlotValidationError> errors = validationException.errors();
            if (!errors.isEmpty()) {
                println("  详细原因:");
                for (SlotValidationError slotError : errors) {
                    println("    slot: " + slotError.slotName()
                            + ", code: " + slotError.code()
                            + ", message: " + slotError.message());
                }
            }
            Map<String, Object> partialParams = validationException.params();
            if (!partialParams.isEmpty()) {
                println("  LLM部分提取参数:");
                println(pretty(partialParams));
            }
        }
        Throwable cause = error.getCause();
        if (cause != null) {
            println("  根因异常: " + cause.getClass().getName() + ": " + cause.getMessage());
        }
    }

    private static void printSummary(TaskTAccuracyEvaluator.Summary summary) {
        System.out.println("──────────── 汇总: " + summary.api() + " ────────────");
        println("  样本数: " + summary.sampleCount()
                + "  通过样本: " + summary.passedSamples()
                + "  字段命中: " + summary.matchedFields() + "/" + summary.expectedFields());
        println("  字段准确率: " + percent(summary.fieldAccuracyPercent()) + "  样本通过率: "
                + percent(summary.samplePassRatePercent()));
    }

    private static void printRejectionSummary(RejectionSummary summary) {
        System.out.println("──────────── 汇总: 缺少关键槽位拒绝用例 ────────────");
        println("  样本数: " + summary.total()
                + "  服务端拒绝: " + summary.serverRejected()
                + "  生成阶段拦截: " + summary.clientBlocked()
                + "  意外通过: " + summary.unexpectedlyPassed());
        println("  服务端校验点拒绝率: " + percent(rejectionRate(summary.serverRejected(), summary.total()))
                + "  总拦截率(服务端+客户端): "
                + percent(rejectionRate(summary.serverRejected() + summary.clientBlocked(), summary.total())));
    }

    /** Rate guarded against a zero sample set. */
    private static double rejectionRate(int numerator, int total) {
        return total == 0 ? 0d : numerator * 100.0 / total;
    }

    /**
     * Aggregated result of the rejection case over the key-slot-missing negative samples: how many were intercepted
     * (either server-side semantic rejection or client-side generation failure) vs unexpectedly passed.
     */
    private record RejectionSummary(int total, int serverRejected, int clientBlocked, int unexpectedlyPassed) {}

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static String pretty(Map<String, ?> map) {
        if (map == null) {
            return "  <null>";
        }
        String body = map.entrySet().stream()
                .map(entry -> entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining("\n  ", "  ", ""));
        return body;
    }

    private static void println(Object message) {
        System.out.println(message);
    }

    private static void println() {
        System.out.println();
    }

    private static Path resolveEnvPath(String[] args) {
        if (args.length > 0) {
            return Path.of(args[0]);
        }
        Path cwdEnv = Path.of(DEFAULT_ENV_FILE);
        if (Files.exists(cwdEnv) && hasRequiredLlmKeys(cwdEnv)) {
            return cwdEnv;
        }
        return Path.of(BUNDLED_ENV_FILE);
    }

    private static boolean hasRequiredLlmKeys(Path envFile) {
        Map<String, String> entries = new HashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                entries.put(
                        trimmed.substring(0, separator).trim(),
                        trimmed.substring(separator + 1).trim());
            }
        } catch (IOException exception) {
            return false;
        }
        for (String key : REQUIRED_LLM_KEYS) {
            if (entries.getOrDefault(key, "").isBlank()) {
                return false;
            }
        }
        return true;
    }
}
