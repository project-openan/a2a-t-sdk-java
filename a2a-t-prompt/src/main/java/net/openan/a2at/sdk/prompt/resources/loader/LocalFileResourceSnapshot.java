package net.openan.a2at.sdk.prompt.resources.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.exception.A2ATError;

/**
 * Immutable in-memory snapshot of a local prompt resource root, captured once at assembly time.
 *
 * <p>The snapshot freezes the {@code templates/}, {@code slots/} and {@code scenarios/} subtrees read from the local
 * root once at assembly time and never re-read. Runtime lookups resolve against this map rather than the filesystem, so
 * local file changes only take effect after the SDK is restarted.
 */
final class LocalFileResourceSnapshot {

    private static final String SEPARATOR = "/";

    private LocalFileResourceSnapshot() {}

    /**
     * Captures the whole {@code templates/}, {@code slots/} and {@code scenarios/} subtrees under the local root into
     * an immutable map keyed by forward-slash relative path.
     *
     * @param localRootDir local prompt resource root
     * @return immutable snapshot mapping {@code templates/..., slots/..., scenarios/...} to file content
     * @throws A2ATError if a file cannot be read or the subtree cannot be enumerated
     */
    static Map<String, String> capture(Path localRootDir) {
        Map<String, String> content = new TreeMap<>();
        captureTree(localRootDir.resolve("templates"), "templates", content);
        captureTree(localRootDir.resolve("slots"), "slots", content);
        captureTree(localRootDir.resolve("scenarios"), "scenarios", content);
        return Map.copyOf(content);
    }

    /**
     * Returns the sorted first-level directory names found under one category prefix in the snapshot.
     *
     * @param snapshot snapshot content map
     * @param category category prefix (templates or slots)
     * @return sorted type directory names
     */
    static List<String> typeDirectories(Map<String, String> snapshot, String category) {
        Set<String> types = new TreeSet<>();
        String prefix = category + SEPARATOR;
        for (String key : snapshot.keySet()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String rest = key.substring(prefix.length());
            int separator = rest.indexOf('/');
            if (separator > 0) {
                types.add(rest.substring(0, separator));
            }
        }
        return List.copyOf(types);
    }

    /**
     * Resolves the snapshot key a scenario code addresses under one category, without reading the filesystem.
     *
     * <p>A path-form code that contains a slash is resolved as-is under {@code category/<code>/<language>/<fileName>}. A
     * bare code is searched across the type directories, preferring the {@code network-layer} domain layout over the
     * plain layout for each type. Returns {@code null} when a bare code matches no type directory.
     */
    static String resolveResourcePath(
            Map<String, String> snapshot,
            String category,
            List<String> typeDirectories,
            String scenarioCode,
            String language,
            String fileName) {
        if (scenarioCode.contains(SEPARATOR)) {
            return category + SEPARATOR + scenarioCode + SEPARATOR + language + SEPARATOR + fileName;
        }
        for (String type : typeDirectories) {
            String networkLayer = category
                    + SEPARATOR + type + SEPARATOR + "network-layer" + SEPARATOR + scenarioCode + SEPARATOR + "v1"
                    + SEPARATOR + language + SEPARATOR + fileName;
            if (snapshot.containsKey(networkLayer)) {
                return networkLayer;
            }
            String plain = category
                    + SEPARATOR + type + SEPARATOR + scenarioCode + SEPARATOR + "v1" + SEPARATOR + language
                    + SEPARATOR + fileName;
            if (snapshot.containsKey(plain)) {
                return plain;
            }
        }
        return null;
    }

    private static void captureTree(Path root, String category, Map<String, String> content) {
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile).toList();
        } catch (IOException exception) {
            throw new A2ATError("Failed to enumerate prompt resources under: " + root, exception);
        }
        for (Path file : files) {
            String relativeKey =
                    category + SEPARATOR + root.relativize(file).toString().replace('\\', '/');
            try {
                content.put(relativeKey, Files.readString(file));
            } catch (IOException exception) {
                throw new A2ATError("Failed to read prompt resource: " + file, exception);
            }
        }
    }
}
