package net.openan.a2at.sdk.core.exception;

import java.nio.file.Path;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Thrown when one required configuration file path does not exist.
 *
 * @since 2026-06
 */
public final class ConfigFileNotFoundException extends A2ATError {

    /**
     * Creates one exception for one missing config file.
     *
     * @param path missing config file path
     */
    public ConfigFileNotFoundException(@NonNull Path path) {
        super(
                ErrorCatalog.INFRA_CONFIG_INVALID.getCode(),
                ErrorMessages.render(
                        ErrorCatalog.INFRA_CONFIG_INVALID,
                        null,
                        Map.of("key", String.valueOf(path), "reason", "config file does not exist")));
    }
}
