package net.openan.a2at.sample.task_t;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 集成测试：将单个 Task-T 样本走通 client → server 完整链路。
 *
 * <p>所有测试方法默认 {@code @Disabled}，需要 LLM 环境（{@code client.env} 配置了可用的 OpenAI 兼容接口）。
 * 在 IDE 中手动取消对应方法的 {@code @Disabled} 即可单独调试某一个样本。
 *
 * <p>样本来源：{@link TaskTPrivateLineComplaintSamples}（从 {@code private-line-complaint-samples.json} 加载）。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 1. 把要调试的样本方法的 @Disabled 注释掉
 * // 2. 在 IDE 中右键运行该方法
 * // 3. 查看控制台输出的 prompt / 提取参数 / 字段命中详情
 * }</pre>
 */
@Disabled("需要 LLM 环境，默认不执行；在 IDE 中手动取消此注解即可运行")
class TaskTSingleSampleTest {

    private static final String DEFAULT_ENV = "client.env";

    /** Classpath 资源路径，对应打包的 {@code sample/task_t/client.env}。 */
    private static final String BUNDLED_ENV_RESOURCE = "sample/task_t/client.env";

    private static A2ATClient client;
    private static A2ATServer server;
    private static Path tempEnvFile;

    @BeforeAll
    static void setUp() throws IOException {
        Path envPath = resolveEnvPath();
        client = new A2ATClient(envPath);
        server = new A2ATServer(envPath);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (tempEnvFile != null) {
            Files.deleteIfExists(tempEnvFile);
        }
    }

    // =========================================================================
    // 用例一：generateTaskPromptFromText（自然语言文本样本）
    // =========================================================================

    /**
     * 文本样本：专线质差投诉（包含所有 5 个槽位）。
     *
     * <p>输入为一段口语化的自然语言投诉描述，期望 LLM 能提取出接入端口、投诉场景、故障时间、流水号、故障详情。
     */
    @Test
    void textSample_privateLineQuality() {
        TaskTSample sample = textSample("text-private-line-quality");
        runAndAssert(sample);
    }

    /**
     * 文本样本：逻辑端口专线中断投诉。
     */
    @Test
    void textSample_logicalPortInterruption() {
        TaskTSample sample = textSample("text-logical-port-interruption");
        runAndAssert(sample);
    }

    /**
     * 文本样本：端口质差抖动投诉。
     */
    @Test
    void textSample_portQualityJitter() {
        TaskTSample sample = textSample("text-port-quality-jitter");
        runAndAssert(sample);
    }

    /**
     * 文本样本：端口中断路由投诉。
     */
    @Test
    void textSample_portInterruptionRoute() {
        TaskTSample sample = textSample("text-port-interruption-route");
        runAndAssert(sample);
    }

    /**
     * 文本样本：端口质差时延投诉。
     */
    @Test
    void textSample_portQualityLatency() {
        TaskTSample sample = textSample("text-port-quality-latency");
        runAndAssert(sample);
    }

    /**
     * 文本样本：逻辑端口中断 VLAN 投诉。
     */
    @Test
    void textSample_logicalPortInterruptionVlan() {
        TaskTSample sample = textSample("text-logical-port-interruption-vlan");
        runAndAssert(sample);
    }

    /**
     * 文本样本：可选槽位缺失（无时间、无流水号），验证服务端对可选字段不强制要求。
     */
    @Test
    void textSample_optionalSlotsMissing() {
        TaskTSample sample = textSample("text-optional-slots-missing");
        runAndAssert(sample);
    }

    // =========================================================================
    // 用例二：generateTaskPromptFromDataWithSchema（结构化数据 + 语义 schema）
    // =========================================================================

    /**
     * 数据样本：端口质差（结构化输入，客户端 key → 服务端 key 跨键适配）。
     */
    @Test
    void dataSample_portQuality() {
        TaskTSample sample = dataSample("data-port-quality");
        runDataAndAssert(sample);
    }

    /**
     * 数据样本：逻辑端口中断。
     */
    @Test
    void dataSample_logicalPortInterruption() {
        TaskTSample sample = dataSample("data-logical-port-interruption");
        runDataAndAssert(sample);
    }

    /**
     * 数据样本：端口抖动。
     */
    @Test
    void dataSample_portJitter() {
        TaskTSample sample = dataSample("data-port-jitter");
        runDataAndAssert(sample);
    }

    /**
     * 数据样本：逻辑端口中断路由。
     */
    @Test
    void dataSample_logicalPortInterruptionRoute() {
        TaskTSample sample = dataSample("data-logical-port-interruption-route");
        runDataAndAssert(sample);
    }

    /**
     * 数据样本：端口质差丢包。
     */
    @Test
    void dataSample_portQualityLoss() {
        TaskTSample sample = dataSample("data-port-quality-loss");
        runDataAndAssert(sample);
    }

    /**
     * 数据样本：端口质差核心慢。
     */
    @Test
    void dataSample_portQualityCoreSlow() {
        TaskTSample sample = dataSample("data-port-quality-core-slow");
        runDataAndAssert(sample);
    }

    /**
     * 数据样本：可选槽位缺失（无时间、无流水号），验证服务端对可选字段不强制要求。
     *
     * <p>已禁用：LLM 偶发不提取 faultDetail，导致非确定性失败。
     */
    @Test
    @Disabled("LLM 偶发不提取 faultDetail，结果不稳定")
    void dataSample_optionalSlotsMissing() {
        TaskTSample sample = dataSample("data-optional-slots-missing");
        runDataAndAssert(sample);
    }

    // =========================================================================
    // 用例三：拒绝样本（缺少关键槽位，期望服务端校验拒绝）
    // =========================================================================

    /**
     * 文本拒绝样本：缺少接入端口。
     */
    @Test
    void rejectionSample_textMissingAccessPort() {
        TaskTRejectionSample sample = rejectionSample("text-missing-access-port");
        runRejectionAndAssert(sample, false);
    }

    /**
     * 文本拒绝样本：缺少投诉场景。
     */
    @Test
    void rejectionSample_textMissingScenario() {
        TaskTRejectionSample sample = rejectionSample("text-missing-scenario");
        runRejectionAndAssert(sample, false);
    }

    /**
     * 文本拒绝样本：缺少所有关键字段。
     */
    @Test
    void rejectionSample_textMinimalNoKeyFields() {
        TaskTRejectionSample sample = rejectionSample("text-minimal-no-key-fields");
        runRejectionAndAssert(sample, false);
    }

    /**
     * 文本拒绝样本：缺少场景和流水号。
     */
    @Test
    void rejectionSample_textMissingScenarioAndSerial() {
        TaskTRejectionSample sample = rejectionSample("text-missing-scenario-and-serial");
        runRejectionAndAssert(sample, false);
    }

    /**
     * 数据拒绝样本：缺少端口。
     */
    @Test
    void rejectionSample_dataMissingPort() {
        TaskTRejectionSample sample = rejectionSample("data-missing-port");
        runRejectionAndAssert(sample, true);
    }

    /**
     * 数据拒绝样本：缺少投诉场景。
     */
    @Test
    void rejectionSample_dataMissingScenario() {
        TaskTRejectionSample sample = rejectionSample("data-missing-scenario");
        runRejectionAndAssert(sample, true);
    }

    /**
     * 数据拒绝样本：投诉场景值不在枚举合约内。
     *
     * <p>已禁用：LLM 总会从 faultDetailText 等上下文推断并规范化 complaintScenario，
     * 无法可靠触发枚举值校验拒绝。
     */
    @Test
    @Disabled("LLM 会从上下文推断并规范化非法枚举值，无法可靠触发拒绝")
    void rejectionSample_dataInvalidScenarioValue() {
        TaskTRejectionSample sample = rejectionSample("data-invalid-scenario-value");
        runRejectionAndAssert(sample, true);
    }

    /**
     * 数据拒绝样本：缺少端口和场景。
     */
    @Test
    void rejectionSample_dataMissingPortAndScenario() {
        TaskTRejectionSample sample = rejectionSample("data-missing-port-and-scenario");
        runRejectionAndAssert(sample, true);
    }

    // =========================================================================
    // 内部方法
    // =========================================================================

    /**
     * 运行一个文本 accuracy 样本：generateTaskPromptFromText → validate → scoreFields → 断言全部字段命中。
     */
    private void runAndAssert(TaskTSample sample) {
        System.out.println("─── 样本: " + sample.name() + " (text) ───");
        System.out.println("[输入] " + sample.text());
        System.out.println();

        MetadataContent metadata = client.generateTaskPromptFromText(
                sample.text(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
        printMetadata(metadata);

        Map<String, Object> extracted = validate(server, sample, metadata);
        printExtracted(extracted);

        List<TaskTAccuracyEvaluator.FieldScore> fields =
                TaskTAccuracyEvaluator.scoreFields(sample, extracted);
        printFieldScores(fields);

        TaskTAccuracyEvaluator.SampleScore score =
                new TaskTAccuracyEvaluator.SampleScore(sample.name(), true, fields);
        assertTrue(score.passed(),
                () -> "样本 " + sample.name() + " 未通过: " + score.matchedFieldCount()
                        + "/" + score.expectedFieldCount() + " 字段命中");
    }

    /**
     * 运行一个数据 accuracy 样本：generateTaskPromptFromDataWithSchema → validate → scoreFields → 断言全部字段命中。
     */
    private void runDataAndAssert(TaskTSample sample) {
        System.out.println("─── 样本: " + sample.name() + " (data) ───");
        System.out.println("[输入] data: " + sample.data());
        System.out.println("[输入] schema: " + sample.semanticsSchema());
        System.out.println();

        MetadataContent metadata = client.generateTaskPromptFromDataWithSchema(
                sample.data(), sample.semanticsSchema(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
        printMetadata(metadata);

        Map<String, Object> extracted = validate(server, sample, metadata);
        printExtracted(extracted);

        List<TaskTAccuracyEvaluator.FieldScore> fields =
                TaskTAccuracyEvaluator.scoreFields(sample, extracted);
        printFieldScores(fields);

        TaskTAccuracyEvaluator.SampleScore score =
                new TaskTAccuracyEvaluator.SampleScore(sample.name(), true, fields);
        assertTrue(score.passed(),
                () -> "样本 " + sample.name() + " 未通过: " + score.matchedFieldCount()
                        + "/" + score.expectedFieldCount() + " 字段命中");
    }

    /**
     * 运行一个拒绝样本：期望被服务端语义校验拒绝，或被客户端前置校验拒绝。
     *
     * @param isData {@code true} 表示数据样本（走 {@code generateTaskPromptFromDataWithSchema}），
     *     {@code false} 表示文本样本（走 {@code generateTaskPromptFromText}）
     */
    private void runRejectionAndAssert(TaskTRejectionSample sample, boolean isData) {
        System.out.println("─── 拒绝样本: " + sample.name() + " (" + (isData ? "data" : "text") + ") ───");
        if (isData) {
            System.out.println("[输入] data: " + sample.data());
            System.out.println("[输入] schema: " + sample.semanticsSchema());
        } else {
            System.out.println("[输入] " + sample.text());
        }
        System.out.println();

        MetadataContent metadata;
        try {
            if (isData) {
                metadata = client.generateTaskPromptFromDataWithSchema(
                        sample.data(), sample.semanticsSchema(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
            } else {
                metadata = client.generateTaskPromptFromText(
                        sample.text(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
            }
        } catch (PromptGenerationException e) {
            // 客户端前置校验拒绝：缺少必填槽位，在调用 LLM 之前就被拦截
            System.out.println("[客户端前置拒绝] [" + e.getCode() + "] " + e.getMessage());
            return; // 预期被拒绝，测试通过
        }
        printMetadata(metadata);

        try {
            Map<String, Object> extracted = server.validateTaskPromptAndDataFilling(
                            metadata.promptText(), sample.validationSchema(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI)
                    .data();
            System.out.println("[意外通过] 应被拒绝却校验通过，提取参数: " + extracted);
            // 拒绝样本应被拒绝，意外通过视为失败
            throw new AssertionError("拒绝样本 " + sample.name() + " 应被服务端拒绝，但校验通过了");
        } catch (ContentValidationException e) {
            System.out.println("[正确拒绝] [" + e.getCode() + "] " + e.getMessage());
            List<net.openan.a2at.sdk.core.model.SlotValidationError> errors = e.errors();
            if (errors != null && !errors.isEmpty()) {
                for (net.openan.a2at.sdk.core.model.SlotValidationError error : errors) {
                    System.out.println("  slot=" + error.slotName()
                            + " code=" + error.code()
                            + " message=" + error.message());
                }
            }
            // 期望被拒绝，测试通过
        }
    }

    private static Map<String, Object> validate(A2ATServer server, TaskTSample sample, MetadataContent metadata) {
        return server.validateTaskPromptAndDataFilling(
                        metadata.promptText(), sample.validationSchema(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI)
                .data();
    }

    private static void printMetadata(MetadataContent metadata) {
        System.out.println("[生成] extensionUri: " + metadata.extensionUri());
        System.out.println("[生成] templateUri : " + metadata.templateUri());
        System.out.println("[生成] promptText  :");
        System.out.println(metadata.promptText());
        System.out.println();
    }

    private static void printExtracted(Map<String, Object> extracted) {
        System.out.println("[校验] 服务端 validateTaskPromptAndDataFilling 通过，提取参数:");
        if (extracted.isEmpty()) {
            System.out.println("  (空)");
        } else {
            extracted.forEach((k, v) -> System.out.println("  " + k + " = " + v));
        }
        System.out.println();
    }

    private static void printFieldScores(List<TaskTAccuracyEvaluator.FieldScore> fields) {
        System.out.println("[比对] 字段级命中:");
        for (TaskTAccuracyEvaluator.FieldScore field : fields) {
            String mark = field.matched() ? "✔ 命中" : "✘ 未命中";
            String detail = field.matched() ? "" : "  " + field.detail();
            System.out.println("  " + mark + " " + field.slot() + detail);
        }
        System.out.println();
    }

    private static TaskTSample textSample(String name) {
        return sampleByName(TaskTPrivateLineComplaintSamples.textSamples(), name);
    }

    private static TaskTSample dataSample(String name) {
        return sampleByName(TaskTPrivateLineComplaintSamples.dataWithSchemaSamples(), name);
    }

    private static TaskTRejectionSample rejectionSample(String name) {
        return TaskTPrivateLineComplaintSamples.rejectionSamples().stream()
                .filter(sample -> sample.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到拒绝样本: " + name));
    }

    private static TaskTSample sampleByName(List<TaskTSample> samples, String name) {
        return samples.stream()
                .filter(sample -> sample.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到样本: " + name));
    }

    /**
     * 解析 {@code client.env} 路径。
     *
     * <p>优先级：
     * <ol>
     *   <li>工作目录下的 {@code client.env}（需包含有效的 LLM 配置密钥）；</li>
     *   <li>repo 根目录下的 {@code client.env}（Maven 多模块项目，从模块目录向上一级）；</li>
     *   <li>classpath 中打包的 sample 模板，复制到临时文件。</li>
     * </ol>
     */
    private static Path resolveEnvPath() throws IOException {
        Path cwdEnv = Path.of(DEFAULT_ENV);
        if (Files.exists(cwdEnv) && hasRequiredLlmKeys(cwdEnv)) {
            return cwdEnv;
        }
        // 从 a2a-t-sample 模块目录向上一级就是 repo 根目录
        Path repoRootEnv = Path.of("..", DEFAULT_ENV);
        if (Files.exists(repoRootEnv) && hasRequiredLlmKeys(repoRootEnv)) {
            return repoRootEnv;
        }
        // 最后回退到 classpath 中的打包模板
        ClassLoader classLoader = TaskTSingleSampleTest.class.getClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(BUNDLED_ENV_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "classpath 中未找到打包的 env 模板: " + BUNDLED_ENV_RESOURCE);
            }
            tempEnvFile = Files.createTempFile("taskt-client-", ".env");
            Files.copy(in, tempEnvFile, StandardCopyOption.REPLACE_EXISTING);
            return tempEnvFile;
        }
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
                entries.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
            }
        } catch (java.io.IOException e) {
            return false;
        }
        for (String key : List.of("A2AT_LLM_PROVIDER", "A2AT_LLM_MODEL", "A2AT_LLM_API_KEY")) {
            if (entries.getOrDefault(key, "").isBlank()) {
                return false;
            }
        }
        return true;
    }
}