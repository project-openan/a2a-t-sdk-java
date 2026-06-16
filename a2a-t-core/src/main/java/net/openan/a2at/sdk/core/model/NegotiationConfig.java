package net.openan.a2at.sdk.core.model;

import java.util.Map;

/**
 * Negotiation runtime configuration resolved from unified SDK config.
 *
 * @since 2026-06
 */
public record NegotiationConfig(String stateStoreType) {

    private static final String DEFAULT_STATE_STORE_TYPE = "in_memory";

    /**
     * Builds one negotiation config from raw `.env` values.
     *
     * @param values raw config values
     * @return resolved negotiation config
     */
    public static NegotiationConfig fromMap(Map<String, String> values) {
        String rawValue = values.get("A2AT_NEGOTIATION_STATE_STORE_TYPE");
        return new NegotiationConfig(rawValue == null || rawValue.isBlank() ? DEFAULT_STATE_STORE_TYPE : rawValue);
    }
}
