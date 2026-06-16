package net.openan.a2at.sdk.core.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.openan.a2at.sdk.core.exception.ConfigFileNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;

/**
 * Reads SDK configuration from one caller-supplied `.env` file.
 * The SDK does not scan default locations automatically.
 *
 * @since 2026-06
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DotEnvConfigSource {

    /**
     * Loads non-empty key/value pairs from one `.env` file.
     *
     * @param path caller-supplied `.env` file path
     * @return parsed non-empty key/value pairs
     */
    public static Map<String, String> load(Path path) {
        if (!Files.exists(path)) {
            throw new ConfigFileNotFoundException(path);
        }

        try {
            return loadLines(Files.readAllLines(path));
        } catch (IOException exception) {
            throw new SdkException("Failed to read config file: " + path, exception);
        }
    }

    private static Map<String, String> loadLines(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            values.put(key, value);
        }
        return Map.copyOf(values);
    }
}
