package net.openan.a2at.sdk.core.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Map;

/**
 * Guardrail provider configuration resolved from unified SDK config.
 *
 * @since 2026-05
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
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.PromptCompliance.GUARDRAIL_PROVIDER), DEFAULT_PROVIDER),
                NumberUtils.toDouble(values.get(A2ATConfigKeys.PromptCompliance.GUARDRAIL_TIMEOUT_SECONDS), DEFAULT_TIMEOUT_SECONDS),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.PromptCompliance.GUARDRAIL_POLICY_ID), ""),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.PromptCompliance.GUARDRAIL_ENDPOINT), ""),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.PromptCompliance.GUARDRAIL_REGION), ""),
                StringUtils.defaultIfBlank(values.get(A2ATConfigKeys.PromptCompliance.GUARDRAIL_CREDENTIALS_REF), ""));
    }
}
