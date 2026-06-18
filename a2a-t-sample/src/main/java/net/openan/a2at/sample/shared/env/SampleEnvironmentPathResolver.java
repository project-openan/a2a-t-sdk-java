package net.openan.a2at.sample.shared.env;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves sample environment files using the same primary-then-fallback behavior as the Python samples.
 *
 * @since 2026-05
 */
public final class SampleEnvironmentPathResolver {

    private SampleEnvironmentPathResolver() {
    }

    public static Path resolve(Path directory, String primaryFileName, String fallbackFileName) {
        Path primary = directory.resolve(primaryFileName);
        if (Files.exists(primary)) {
            return primary;
        }

        Path fallback = directory.resolve(fallbackFileName);
        if (Files.exists(fallback)) {
            return fallback;
        }
        return primary;
    }
}


