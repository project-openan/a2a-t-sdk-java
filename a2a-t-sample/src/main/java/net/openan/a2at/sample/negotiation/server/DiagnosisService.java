package net.openan.a2at.sample.negotiation.server;

import java.util.Map;
import net.openan.a2at.sample.negotiation.shared.ScenarioData;
import net.openan.a2at.sdk.core.model.FilledParamData;

/**
 * Produces a diagnosis result from the validated Task-T parameters.
 *
 * <p>The text layout comes from the {@code diagnosis} templates of the scenario configuration ({@code result_line},
 * {@code detail_line} with a {@code {params}} placeholder, {@code advice_line}); the parameter values themselves are
 * taken from what {@code validateTaskPromptAndDataFilling} extracted, so the result adapts to any Task-T input. In a
 * real deployment this service would call the EMS/NMS north-bound API instead of rendering text.
 *
 * @since 2026-08
 */
public final class DiagnosisService {

    /** Parameter keys the SDK injects as negotiation context (not business slots). */
    private static final String[] CONTEXT_PARAM_KEYS = {"id", "round", "maxRounds"};

    private DiagnosisService() {}

    /**
     * Builds a diagnosis result from the extracted Task-T parameters and the scenario templates.
     *
     * @param params validated parameters from {@code validateTaskPromptAndDataFilling}
     * @return diagnosis result text
     */
    public static String diagnose(FilledParamData params) {
        Map<String, String> templates = ScenarioData.diagnosisTemplates();
        StringBuilder detail = new StringBuilder();
        if (params != null && params.data() != null) {
            for (Map.Entry<String, Object> entry : params.data().entrySet()) {
                if (isContextParam(entry.getKey())) {
                    continue;
                }
                Object value = entry.getValue();
                if (value != null && (!(value instanceof String s) || !s.isBlank())) {
                    detail.append(entry.getKey()).append("=").append(value).append("；");
                }
            }
        }
        return templates.getOrDefault("result_line", "")
                + "\n"
                + templates.getOrDefault("detail_line", "{params}").replace("{params}", detail)
                + "\n"
                + templates.getOrDefault("advice_line", "");
    }

    private static boolean isContextParam(String key) {
        for (String contextKey : CONTEXT_PARAM_KEYS) {
            if (contextKey.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
