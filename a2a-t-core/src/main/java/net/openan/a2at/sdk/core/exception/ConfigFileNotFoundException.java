package net.openan.a2at.sdk.core.exception;

import java.nio.file.Path;

/**
 * Thrown when one required configuration file path does not exist.
 *
 * @since 2026-06
 */
public final class ConfigFileNotFoundException extends SdkException {

    /**
     * Creates one exception for one missing config file.
     *
     * @param path missing config file path
     */
    public ConfigFileNotFoundException(Path path) {
        super("Config file does not exist: " + path);
    }
}
