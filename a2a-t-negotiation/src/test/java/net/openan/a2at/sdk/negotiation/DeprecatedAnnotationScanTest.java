package net.openan.a2at.sdk.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Guards the compatibility promise of the negotiation content layer: no API of the pre-existing negotiation state
 * machine may gain a {@code @Deprecated} marker now that the content layer exists. The scan pins the exact set of
 * pre-existing {@code @Deprecated} annotations of the module main sources, so any newly added annotation fails the
 * test.
 */
class DeprecatedAnnotationScanTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /** The only @Deprecated markers that existed before the negotiation content layer was added. */
    private static final List<String> PINNED_DEPRECATED_ANNOTATIONS =
            List.of("net/openan/a2at/sdk/negotiation/runtime/NegotiationHandler.java");

    @Test
    void noNewDeprecatedAnnotationsWereAddedToTheModuleMainSources() throws IOException {
        Assumptions.assumeTrue(
                Files.isDirectory(MAIN_SOURCES), "module main sources are not visible from the test working directory");

        List<String> annotatedFiles;
        try (Stream<Path> sources = Files.walk(MAIN_SOURCES)) {
            annotatedFiles = sources.filter(Files::isRegularFile)
                    .filter(DeprecatedAnnotationScanTest::containsDeprecatedAnnotation)
                    .map(path -> MAIN_SOURCES.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }

        assertEquals(
                PINNED_DEPRECATED_ANNOTATIONS,
                annotatedFiles,
                "the negotiation module must not gain new @Deprecated annotations; the pinned set is the"
                        + " pre-content-layer baseline");
        assertTrue(
                annotatedFiles.stream()
                        .noneMatch(file -> file.startsWith("content")
                                || file.startsWith("generation")
                                || file.startsWith("validation")
                                || file.startsWith("resources")),
                "none of the negotiation content layer packages may carry @Deprecated annotations");
    }

    private static boolean containsDeprecatedAnnotation(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains("@Deprecated");
        } catch (IOException exception) {
            return false;
        }
    }
}
