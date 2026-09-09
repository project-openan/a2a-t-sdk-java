package net.openan.a2at.sdk.corpus.engine.discover;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * Discovers corpus scenarios by directory convention: a scenario is any directory under the extension's
 * {@code resources/} folder that carries the requested case file ({@code input_case_from_text.json} or
 * {@code input_case_from_data.json}). New scenarios therefore require zero Java changes.
 *
 * <p>Discovery prefers the source tree ({@code src/test/java/.../<extension>/resources/}), which is where Maven
 * runs surefire ({@code user.dir} is the module base directory); it falls back to the test classpath for IDE runs.
 */
public final class ScenarioScanner {

    /** One discovered scenario: directory name plus writable source directory carrying the case files. */
    public record Scenario(String name, Path sourceDir) {

        public Path inputFile(String flowFileName) {
            return sourceDir.resolve(flowFileName);
        }
    }

    private static final String RESOURCE_PATH_PREFIX =
            "net/openan/a2at/sdk/corpus/";

    private ScenarioScanner() {}

    public static List<Scenario> discover(String extensionFolder, String flowFileName) {
        List<Scenario> scenarios = new ArrayList<>();
        Path sourceTree = Path.of(
                        System.getProperty("user.dir", "."),
                        "src",
                        "test",
                        "java",
                        "net",
                        "openan",
                        "a2at",
                        "sdk",
                        "corpus",
                        extensionFolder,
                        "resources")
                .toAbsolutePath()
                .normalize();
        if (Files.isDirectory(sourceTree)) {
            collectFromDirectory(sourceTree, scenarios, flowFileName);
        }
        if (scenarios.isEmpty()) {
            collectFromClasspath(extensionFolder, scenarios, flowFileName);
        }
        scenarios.sort(Comparator.comparing(Scenario::name));
        if (scenarios.isEmpty()) {
            throw new IllegalStateException(
                    "No corpus scenario directories found: expected " + extensionFolder + "/resources/<scenario>/" + flowFileName
                            + " (neither the source tree " + sourceTree + " nor the test classpath matches)");
        }
        return scenarios;
    }

    public static List<Scenario> filterScenarios(List<Scenario> scenarios, String filter) {
        if (filter == null || filter.isBlank()) {
            return scenarios;
        }
        List<Scenario> filtered = new ArrayList<>();
        for (String pattern : filter.split(",")) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern.strip());
            for (Scenario scenario : scenarios) {
                if (matcher.matches(Path.of(scenario.name())) && !filtered.contains(scenario)) {
                    filtered.add(scenario);
                }
            }
        }
        return filtered;
    }

    public static boolean matchesCaseFilter(String caseFilter, String caseId) {
        if (caseFilter == null || caseFilter.isBlank()) {
            return true;
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + caseFilter.strip());
        return matcher.matches(Path.of(caseId));
    }

    private static void collectFromDirectory(Path sourceTree, List<Scenario> scenarios, String flowFileName) {
        try (var entries = Files.list(sourceTree)) {
            List<Path> directories = entries.filter(Files::isDirectory).toList();
            for (Path directory : directories) {
                if (Files.isRegularFile(directory.resolve(flowFileName))) {
                    scenarios.add(new Scenario(directory.getFileName().toString(), directory));
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to scan the scenario directory: " + sourceTree, error);
        }
    }

    private static void collectFromClasspath(
            String extensionFolder, List<Scenario> scenarios, String flowFileName) {
        String resourceRoot = RESOURCE_PATH_PREFIX + extensionFolder + "/resources";
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> roots = loader.getResources(resourceRoot);
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                if (!"file".equals(root.getProtocol())) {
                    continue;
                }
                try {
                    File rootFile = java.nio.file.Paths.get(root.toURI()).toFile();
                    File[] children = rootFile.listFiles(File::isDirectory);
                    if (children == null) {
                        continue;
                    }
                    for (File child : children) {
                        if (new File(child, flowFileName).isFile()) {
                            scenarios.add(new Scenario(child.getName(), child.toPath().toAbsolutePath()));
                        }
                    }
                } catch (Exception ignored) {
                    // unreadable root entry; ignore and continue scanning other roots
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to scan the test classpath for scenario directories: " + resourceRoot, error);
        }
    }
}