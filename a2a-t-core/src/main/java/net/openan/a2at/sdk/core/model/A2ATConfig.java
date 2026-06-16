package net.openan.a2at.sdk.core.model;

import java.nio.file.Path;
import java.util.Map;

/**
 * Unified SDK configuration entry point loaded from one caller-supplied `.env` file.
 * Users are expected to copy the repository `env.example` into their own application and pass that file path in.
 *
 * @since 2026-05
 */
public record A2ATConfig(
        PromptRuntimeConfig prompt,
        LlmConfig llm,
        NegotiationConfig negotiation,
        PromptComplianceConfig promptCompliance) {

    /**
     * Loads one unified SDK config from one `.env` file path.
     *
     * @param envPath caller-supplied `.env` file path
     * @return unified SDK config
     */
    public static A2ATConfig load(Path envPath) {
        Map<String, String> values = DotEnvConfigSource.load(envPath);
        return new A2ATConfig(
                PromptRuntimeConfig.fromMap(values),
                LlmConfig.fromMap(values),
                NegotiationConfig.fromMap(values),
                PromptComplianceConfig.fromMap(values));
    }
}
