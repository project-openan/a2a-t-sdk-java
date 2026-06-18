package net.openan.a2at.sample.client.prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts sample business payloads into prompt slot inputs expected by prompt resources.
 *
 * @since 2026-05
 */
public final class SamplePromptInputAdapter {

    private static final String SLOT_TOPIC = "通知主题";

    private static final String SLOT_CONDITION = "订阅条件";

    private static final String SLOT_REPORT_FORMAT = "上报通知数据格式";

    private SamplePromptInputAdapter() {
    }

    public static Map<String, Object> adapt(Map<String, Object> scenarioPayload) {
        Map<String, Object> adapted = new LinkedHashMap<>(scenarioPayload);
        adapted.put(SLOT_TOPIC, stringOrDefault(scenarioPayload.get(SLOT_TOPIC), "Incident"));
        adapted.put(SLOT_CONDITION, stringOrDefault(scenarioPayload.get(SLOT_CONDITION), buildConditionText(scenarioPayload)));
        adapted.put(SLOT_REPORT_FORMAT, stringOrDefault(
                scenarioPayload.get(SLOT_REPORT_FORMAT),
                "通过DataPart上报Incident数据"));
        return adapted;
    }

    private static String buildConditionText(Map<String, Object> scenarioPayload) {
        List<String> parts = new ArrayList<>();
        Object subscriptionFilterValue = scenarioPayload.get("subscription_filter");
        if (subscriptionFilterValue instanceof Map<?, ?> filterMap) {
            String severity = severityLabel(filterMap.get("severity"));
            if (!severity.isBlank()) {
                parts.add("故障优先级为：" + severity);
            }
            String alarmType = stringOrDefault(filterMap.get("alarm_type"), "");
            if (!alarmType.isBlank()) {
                parts.add("告警类型为：" + alarmType);
            }
            String province = stringOrDefault(filterMap.get("province"), "");
            if (!province.isBlank()) {
                parts.add("省份为：" + province);
            }
        }

        Object diagnosisContextValue = scenarioPayload.get("diagnosis_context");
        if (diagnosisContextValue instanceof Map<?, ?> contextMap) {
            String domain = stringOrDefault(contextMap.get("domain"), "");
            if (!domain.isBlank()) {
                parts.add("网络域为：" + domain);
            }
            String source = stringOrDefault(contextMap.get("source"), "");
            if (!source.isBlank()) {
                parts.add("来源为：" + source);
            }
        }
        return String.join("；", parts);
    }

    private static String severityLabel(Object value) {
        String severity = stringOrDefault(value, "").toLowerCase();
        return switch (severity) {
            case "critical" -> "严重";
            case "high" -> "高";
            case "medium" -> "中";
            case "low" -> "低";
            default -> stringOrDefault(value, "");
        };
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String resolved = String.valueOf(value).trim();
        return resolved.isEmpty() ? defaultValue : resolved;
    }
}
