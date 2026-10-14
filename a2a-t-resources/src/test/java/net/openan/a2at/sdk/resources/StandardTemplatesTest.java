package net.openan.a2at.sdk.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import org.junit.jupiter.api.Test;

/**
 * Locks the {@link StandardTemplates} constants to the template tree bundled in this module: adding or removing a
 * {@code template.md} under {@code prompt_resources/templates} without updating the constants (or vice versa) turns
 * this test red.
 *
 * <p>This test lives in a2a-t-resources because downstream modules ship their own {@code prompt_resources} test
 * fixtures that would shadow this module's tree on the classpath.
 */
class StandardTemplatesTest {

    @Test
    void constantsMatchBundledTemplateTree() throws Exception {
        Set<String> constantUris = new TreeSet<>();
        Stream.of(StandardTemplates.TASK, StandardTemplates.NOTIFICATION, StandardTemplates.AUTHORIZATION,
                        StandardTemplates.NEGOTIATION)
                .flatMap(List::stream)
                .map(TemplateUri::uri)
                .forEach(constantUris::add);

        assertEquals(
                constantUris,
                scanTemplateTree(),
                "StandardTemplates constants and the bundled prompt_resources/templates tree have drifted");
    }

    @Test
    void groupsPartitionAllConstants() {
        List<TemplateUri> grouped = new ArrayList<>();
        grouped.addAll(StandardTemplates.TASK);
        grouped.addAll(StandardTemplates.NOTIFICATION);
        grouped.addAll(StandardTemplates.AUTHORIZATION);
        grouped.addAll(StandardTemplates.NEGOTIATION);
        assertEquals(13, grouped.size(), "expected the 13 built-in templates");
        assertEquals(3, StandardTemplates.TASK.size());
        assertEquals(2, StandardTemplates.NOTIFICATION.size());
        assertEquals(1, StandardTemplates.AUTHORIZATION.size());
        assertEquals(7, StandardTemplates.NEGOTIATION.size());
    }

    /**
     * Collects the template URIs present under {@code prompt_resources/templates} by stripping the trailing
     * {@code <language>/template.md} segments from every {@code template.md} file.
     */
    private static Set<String> scanTemplateTree() throws IOException, java.net.URISyntaxException {
        URL root = StandardTemplatesTest.class.getClassLoader().getResource("prompt_resources/templates");
        if (root == null || !"file".equals(root.getProtocol())) {
            // Directory walking only works on exploded classpaths; Maven test runs always satisfy this.
            throw new IllegalStateException("prompt_resources/templates not reachable as a file URL: " + root);
        }
        Path rootPath = Path.of(root.toURI());
        try (Stream<Path> files = Files.walk(rootPath)) {
            Set<String> uris = new TreeSet<>();
            files.filter(Files::isRegularFile)
                    .filter(file -> "template.md".equals(file.getFileName().toString()))
                    .forEach(file -> {
                        Path relative = rootPath.relativize(file);
                        int segmentCount = relative.getNameCount();
                        // relative = <uri segments...>/<language>/template.md
                        uris.add(relative.subpath(0, segmentCount - 2).toString().replace('\\', '/'));
                    });
            return uris;
        }
    }
}
