package net.openan.a2at.sdk.core.model;

import java.util.Map;

/**
 * Guardrail provider configuration resolved from unified SDK config.
 *
 * @since 2026-06
 */
public record GuardrailProviderConfig(
        String provider, double timeout, String policyId, String endpoint, String region, String credentialsRef) {

    private static final String DEFAULT_PROVIDER = "noop";

    private static final double DEFAULT_TIMEOUT_SECONDS = 10.0d;

    /**
     * Builds one guardrail provider config from raw `.env` values.
     *
     * @param values raw config values
     * @return resolved guardrail provider config
     */
    public static GuardrailProviderConfig fromMap(Map<String, String> values) {
        return new GuardrailProviderConfig(
                valueOrDefault(values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_PROVIDER"), DEFAULT_PROVIDER),
                parseDouble(values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_TIMEOUT_SECONDS"), DEFAULT_TIMEOUT_SECONDS),
                valueOrDefault(values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_POLICY_ID"), ""),
                valueOrDefault(values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_ENDPOINT"), ""),
                valueOrDefault(values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_REGION"), ""),
                valueOrDefault(values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_CREDENTIALS_REF"), ""));
    }

    private static String valueOrDefault(String rawValue, String defaultValue) {
        return rawValue == null || rawValue.isBlank() ? defaultValue : rawValue;
    }

    private static double parseDouble(String rawValue, double defaultValue) {
        return rawValue == null || rawValue.isBlank() ? defaultValue : Double.parseDouble(rawValue);
    }
}
