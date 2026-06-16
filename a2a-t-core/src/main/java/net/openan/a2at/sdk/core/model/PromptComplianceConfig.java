package net.openan.a2at.sdk.core.model;

import java.util.Map;

/**
 * Prompt compliance configuration resolved from unified SDK config.
 *
 * @since 2026-06
 */
public record PromptComplianceConfig(boolean enabled, GuardrailProviderConfig guardrail) {

    /**
     * Builds one prompt compliance config from raw `.env` values.
     *
     * @param values raw config values
     * @return resolved prompt compliance config
     */
    public static PromptComplianceConfig fromMap(Map<String, String> values) {
        return new PromptComplianceConfig(parseBoolean(values.get("A2AT_PROMPT_COMPLIANCE_ENABLED")), GuardrailProviderConfig.fromMap(values));
    }

    private static boolean parseBoolean(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return false;
        }
        String normalized = rawValue.trim().toLowerCase();
        return normalized.equals("1")
                || normalized.equals("true")
                || normalized.equals("yes")
                || normalized.equals("on");
    }
}
