package net.openan.a2at.sample.task_t;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in evaluation samples for the {@code Task-T/network-layer/private-line-complaint/v1} template (transfer /
 * private-line business complaint diagnosis), loaded from the packaged JSON file
 * {@code a2a-t-sample/src/main/resources/sample/task_t/private-line-complaint-samples.json}.
 *
 * <p>The sample set is split into two groups matching the two client APIs under test:
 *
 * <ul>
 *   <li>{@link #textSamples()} feed {@code A2ATClient#generateTaskPromptFromText} with concise colloquial natural
 *       language — deliberately <b>not</b> field-listed, so a run exercises the SDK natural-language parsing
 *       capability;
 *   <li>{@link #dataWithSchemaSamples()} feed {@code A2ATClient#generateTaskPromptFromDataWithSchema} with a structured
 *       input whose {@code data} carries <b>English business fields</b> — not the template slot names.
 *   <li>{@link #rejectionSamples()} feed {@code generateTaskPromptFromText} with content that deliberately omits key
 *       slots; server-side semantic validation is expected to reject them — scored separately from the accuracy
 *       samples.
 * </ul>
 *
 * <p>Per design, the client-side keys and the server-side keys intentionally <b>diverge</b>: the client submits its
 * business fields under its own key names, the SDK renders them into the template prompt, and the server re-extracts
 * them from the rendered prompt under the <b>server-side key names</b>. This cross-key round trip exercises the a2a-t
 * SDK adaptation capability — neither side may assume the other's key layout.
 *
 * <table border="1">
 *   <caption>near semantics, distinct keys</caption>
 *   <tr><th>client key (data / semantics schema)</th><th>server key (validation schema / extracted params)</th><th>meaning</th></tr>
 *   <tr><td>{@code portName}</td><td>{@code accessPort}</td><td>接入端口名称</td></tr>
 *   <tr><td>{@code complaintScenario}</td><td>{@code bizScenario}</td><td>投诉分类（专线中断/专线质差）</td></tr>
 *   <tr><td>{@code faultStartTime}</td><td>{@code faultTime}</td><td>问题发生时间</td></tr>
 *   <tr><td>{@code ticketNo}</td><td>{@code eventSerialNo}</td><td>OSS 侧事件流水号</td></tr>
 *   <tr><td>{@code faultDetailText}</td><td>{@code faultDetail}</td><td>投诉/故障详情</td></tr>
 * </table>
 *
 * <p>Expected values ({@link TaskTSample#expectedParams()}) are keyed by the <b>server</b> field names, the keys the
 * accuracy evaluator actually reads out of {@code validateTaskPromptAndDataFilling}; they hold canonical key facts that
 * always appear in the input. Structured fields (port, scenario, time, serial) hit only when equal after
 * whitespace-and-case normalization; the free-text detail field hits on containment.
 */
public final class TaskTPrivateLineComplaintSamples {

    /** Client key: the addressed access port name, e.g. {@code P781-珠江新城-PTN7900-23-TPA1EG24-17}. */
    public static final String CLIENT_PORT = "portName";

    /** Client key: complaint category scenario, {@code 专线中断} or {@code 专线质差}. */
    public static final String CLIENT_SCENARIO = "complaintScenario";

    /** Client key: problem occurrence time (ISO-8601). */
    public static final String CLIENT_TIME = "faultStartTime";

    /** Client key: OSS-side complaint/event ticket number. */
    public static final String CLIENT_TICKET = "ticketNo";

    /** Client key: free-text complaint detail. */
    public static final String CLIENT_DETAIL = "faultDetailText";

    /** Server key (extracted parameter): access port name; near-semantics twin of {@link #CLIENT_PORT}. */
    public static final String SERVER_PORT = "accessPort";

    /** Server key (extracted parameter): complaint category; near-semantics twin of {@link #CLIENT_SCENARIO}. */
    public static final String SERVER_SCENARIO = "bizScenario";

    /** Server key (extracted parameter): problem occurrence time; near-semantics twin of {@link #CLIENT_TIME}. */
    public static final String SERVER_TIME = "faultTime";

    /** Server key (extracted parameter): event serial number; near-semantics twin of {@link #CLIENT_TICKET}. */
    public static final String SERVER_TICKET = "eventSerialNo";

    /** Server key (extracted parameter): complaint detail; near-semantics twin of {@link #CLIENT_DETAIL}. */
    public static final String SERVER_DETAIL = "faultDetail";

    private static final String SAMPLE_RESOURCE = "sample/task_t/private-line-complaint-samples.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Loaded LOADED = load();

    private TaskTPrivateLineComplaintSamples() {}

    /**
     * Natural-language samples for {@code generateTaskPromptFromText}.
     *
     * @return immutable text sample list
     */
    public static List<TaskTSample> textSamples() {
        return LOADED.samples().stream().filter(sample -> sample.text() != null).toList();
    }

    /**
     * Data-plus-schema samples for {@code generateTaskPromptFromDataWithSchema}.
     *
     * <p>Each sample passes business fields under the <b>client</b> keys in {@code data} plus a client-side semantics
     * {@code schema}; the server-side validation schema re-extracts the parameters under the <b>server</b> keys, so a
     * run exercises the cross-key adaptation of the SDK.
     *
     * @return immutable data sample list
     */
    public static List<TaskTSample> dataWithSchemaSamples() {
        return LOADED.samples().stream().filter(sample -> sample.data() != null).toList();
    }

    /**
     * Rejection samples that deliberately omit one or more key slots.
     *
     * <p>Two variants mirror the two client APIs under test: {@code text} samples are colloquial natural-language
     * complaints missing a required fact (access port, complaint scenario), while {@code data} samples are structured
     * inputs under the client field names that omit a required field or carry a complaint category outside the contract
     * {@code enum}. Time and serial are <em>optional</em> slots and are deliberately not used as rejection criteria —
     * their absence is exercised by the positive samples {@code text-optional-slots-missing} /
     * {@code data-optional-slots-missing} instead. The server-side semantic validation is expected to reject these
     * samples with {@code negotiation.semantic_rejected}; they are scored separately from the accuracy samples and are
     * never fed into the field-accuracy evaluation.
     *
     * @return immutable rejection sample list
     */
    public static List<TaskTRejectionSample> rejectionSamples() {
        return LOADED.rejections();
    }

    private static Loaded load() {
        try (InputStream in = TaskTPrivateLineComplaintSamples.class.getResourceAsStream("/" + SAMPLE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("sample resource not found on classpath: " + SAMPLE_RESOURCE);
            }
            JsonNode root = OBJECT_MAPPER.readTree(in);
            Map<String, Object> semanticsSchema = toObjectMap(root.get("semanticsSchema"));
            Map<String, Object> validationSchema = toObjectMap(root.get("validationSchema"));
            List<TaskTSample> samples = new ArrayList<>();
            for (JsonNode node : root.get("samples")) {
                String name = node.get("name").asText();
                JsonNode text = node.get("text");
                JsonNode data = node.get("data");
                Map<String, String> expected = toStringMap(node.get("expected"));
                if (data == null) {
                    samples.add(new TaskTSample(name, text.asText(), null, null, expected, validationSchema));
                } else {
                    samples.add(new TaskTSample(
                            name, null, toObjectMap(data), semanticsSchema, expected, validationSchema));
                }
            }
            List<TaskTRejectionSample> rejections = new ArrayList<>();
            JsonNode rejectionNodes = root.get("rejectionSamples");
            if (rejectionNodes != null) {
                for (JsonNode node : rejectionNodes) {
                    String name = node.get("name").asText();
                    JsonNode data = node.get("data");
                    if (data == null) {
                        rejections.add(new TaskTRejectionSample(
                                name, node.get("text").asText(), null, null, validationSchema));
                    } else {
                        rejections.add(new TaskTRejectionSample(
                                name, null, toObjectMap(data), semanticsSchema, validationSchema));
                    }
                }
            }
            return new Loaded(List.copyOf(samples), List.copyOf(rejections));
        } catch (IOException error) {
            throw new IllegalStateException("failed to load task-t samples from " + SAMPLE_RESOURCE, error);
        }
    }

    /** Loaded evaluation sample set split into accuracy samples and rejection samples. */
    private record Loaded(List<TaskTSample> samples, List<TaskTRejectionSample> rejections) {}

    private static Map<String, Object> toObjectMap(JsonNode node) {
        return OBJECT_MAPPER.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private static Map<String, String> toStringMap(JsonNode node) {
        return OBJECT_MAPPER.convertValue(node, new TypeReference<LinkedHashMap<String, String>>() {});
    }
}

/**
 * One private-line complaint diagnosis evaluation sample.
 *
 * @param name sample identifier shown in the accuracy report
 * @param text natural-language input for the FromText case; {@code null} when the sample only targets the data case
 * @param data structured business-field input for the FromDataWithSchema case, keyed by the <b>client</b> field names;
 *     {@code null} when the sample only targets the text case
 * @param semanticsSchema client-side field-semantics schema, keyed by the client field names; may be {@code null} when
 *     {@code data} is {@code null}
 * @param expectedParams ground truth business values, keyed by the <b>server</b> field names (the keys read out of
 *     {@code validateTaskPromptAndDataFilling}); these are the fields actually scored
 * @param validationSchema caller-provided JSON parameter schema passed to {@code validateTaskPromptAndDataFilling},
 *     keyed by the server field names
 * @since 2026-08
 */
record TaskTSample(
        String name,
        String text,
        Map<String, Object> data,
        Map<String, Object> semanticsSchema,
        Map<String, String> expectedParams,
        Map<String, Object> validationSchema) {}

/**
 * One negative sample for the rejection check: content that deliberately omits one or more key slots (or carries an
 * invalid slot value) and is expected to be rejected by the server-side semantic validation. Two variants exist,
 * mirroring the two client APIs under test:
 *
 * <ul>
 *   <li>{@code text} variant feeds {@code A2ATClient#generateTaskPromptFromText} with colloquial natural language;
 *   <li>{@code data} variant feeds {@code A2ATClient#generateTaskPromptFromDataWithSchema} with structured input keyed
 *       by the client field names plus the client-side semantics schema.
 * </ul>
 *
 * @param name sample identifier shown in the rejection report
 * @param text natural-language input for the FromText variant; {@code null} when the sample only targets the data case
 * @param data structured business-field input for the FromDataWithSchema variant, keyed by the <b>client</b> field
 *     names; {@code null} when the sample only targets the text case
 * @param semanticsSchema client-side field-semantics schema, keyed by the client field names; may be {@code null} when
 *     {@code data} is {@code null}
 * @param validationSchema caller-provided JSON parameter schema passed to {@code validateTaskPromptAndDataFilling}
 * @since 2026-08
 */
record TaskTRejectionSample(
        String name,
        String text,
        Map<String, Object> data,
        Map<String, Object> semanticsSchema,
        Map<String, Object> validationSchema) {}
