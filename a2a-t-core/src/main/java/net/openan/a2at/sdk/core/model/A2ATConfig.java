package net.openan.a2at.sdk.core.model;

import java.nio.file.Path;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Unified SDK configuration entry point loaded from one caller-supplied `.env` file. Users are expected to copy the
 * repository `env.example` into their own application and pass that file path in.
 *
 * @since 2026-06
 */
public record A2ATConfig(
        PromptRuntimeConfig prompt,
        LlmConfig llm,
        InputLimitConfig inputLimits,
        NegotiationConfig negotiation,
        PromptComplianceConfig promptCompliance) {

    /**
     * Loads one unified SDK config from one `.env` file path.
     *
     * @param envPath caller-supplied `.env` file path
     * @return unified SDK config
     */
    public static @NonNull A2ATConfig load(@NonNull Path envPath) {
        Map<String, String> values = DotEnvConfigSource.load(envPath);
        return new A2ATConfig(
                PromptRuntimeConfig.fromMap(values),
                LlmConfig.fromMap(values),
                InputLimitConfig.fromMap(values),
                NegotiationConfig.fromMap(values),
                PromptComplianceConfig.fromMap(values));
    }

    /**
     * Resolves the relative prompt resource local root directory against the `.env` file location.
     *
     * <p>The returned config carries the local root as an absolute normalized path: a relative configured root is
     * resolved against the `.env` file parent, an absolute configured root is only normalized. An unset (null or blank)
     * local root is preserved as-is, since only {@code local_file} mode consumes the value (and enforces its presence
     * at assembly time).
     *
     * @param config unified SDK config as loaded from the `.env` file
     * @param envPath resolved `.env` file path the config was loaded from; a relative path is absolved against the
     *     current working directory before its parent is derived
     * @return config with the local root resolved to an absolute normalized path, or unchanged when the local root is
     *     unset (null or blank)
     */
    public static @NonNull A2ATConfig resolvePromptResourceLocalRootDir(
            @NonNull A2ATConfig config, @NonNull Path envPath) {
        String localRootDir = config.prompt().localRootDir();
        if (localRootDir == null || localRootDir.isBlank()) {
            return config;
        }
        Path localRootPath = Path.of(localRootDir);
        Path resolvedLocalRootPath = localRootPath.isAbsolute()
                ? localRootPath.normalize()
                : envBaseDir(envPath).resolve(localRootPath).toAbsolutePath().normalize();
        return new A2ATConfig(
                new PromptRuntimeConfig(
                        config.prompt().language(), config.prompt().sourceType(), resolvedLocalRootPath.toString()),
                config.llm(),
                config.inputLimits(),
                config.negotiation(),
                config.promptCompliance());
    }

    private static Path envBaseDir(Path envPath) {
        Path parent = envPath.toAbsolutePath().normalize().getParent();
        return parent != null ? parent : Path.of("").toAbsolutePath();
    }
}
