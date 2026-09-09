package net.openan.a2at.sdk.prompt.resources.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.model.A2ATConfigKeys;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import net.openan.a2at.sdk.core.resources.PathSegments;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Directory-driven catalog over the prompt template tree of every A2A-T extension.
 *
 * <p>The catalog enumerates the {@code templates/} tree of the configured source by walking its directories, so every
 * extension directory that appears under {@code prompt_resources/templates/} — Task-T, Notification-T, Authorization-T,
 * Negotiation-T and any extension added later — is picked up without a hardcoded extension list. A template file lives
 * at {@code templates/<extensionName>/<pathSegments>/<templateVersion>/<language>/template.md} and is addressed by the
 * URI formed from the segments before the language, for example
 * {@code Negotiation-T/information-negotiation/propose/v1} or {@code Task-T/network-layer/ran-energy-saving/v1}.
 *
 * <p>The catalog builds the effective set at assembly time according to the configured {@code sourceType}: the
 * {@code classpath} mode enumerates the full built-in template tree; the {@code local_file} mode enumerates only the
 * local business content (Task-T, Notification-T and Authorization-T) plus the classpath-fixed Negotiation-T templates,
 * without unioning the built-in business content. Every template record carries its effective origin ({@code classpath}
 * or {@code local}). Negotiation-T templates are filtered to the closed set of seven shapes; a Negotiation-T template
 * outside the closed set is ignored with a warning. Both query methods never throw: a template or a root that cannot be
 * loaded is skipped or answered with an empty result and a warning log.
 *
 * @since 2026-08
 */
final class PromptTemplateCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptTemplateCatalog.class);

    private static final String CLASSPATH_ROOT = "prompt_resources/";

    private static final String CLASSPATH_TEMPLATE_ROOT = CLASSPATH_ROOT + "templates/";

    private static final String TEMPLATE_DIRECTORY = "templates";

    private static final String TEMPLATE_FILE_NAME = "template.md";

    private static final int MINIMUM_URI_SEGMENTS = 3;

    private static final String NEGOTIATION_EXTENSION = "Negotiation-T";

    private static final Set<String> NEGOTIATION_TYPES =
            Set.of("information-negotiation", "target-negotiation", "feasibility-negotiation");

    private static final Set<String> NEGOTIATION_PHASES = Set.of("propose", "accept-reject");

    private final String language;

    private final String sourceType;

    private final Path localRootDir;

    private final Map<String, String> classpathSnapshot;

    private final Map<String, String> localSnapshot;

    /**
     * Creates a catalog for one language, two source types and an optional local template root.
     *
     * <p>The constructor captures the classpath template tree and, in {@code local_file} mode, the local
     * {@code templates/} tree once as immutable snapshots: the catalog never re-reads the classpath or the local
     * filesystem after construction, so a template that is added or removed after assembly is not visible to
     * {@link #loadAll()} or {@link #load(TemplateUri)}.
     *
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @param sourceType resource source selector, {@code classpath} or {@code local_file}
     * @param localRootDir local prompt resource root containing the {@code templates/} tree; required in
     *     {@code local_file} mode and ignored otherwise
     * @throws IllegalArgumentException if the language is not a simple path segment, the source type is unsupported, or
     *     {@code local_file} mode is selected without a local root directory or with one that does not exist
     */
    PromptTemplateCatalog(@NonNull String language, @NonNull String sourceType, @Nullable String localRootDir) {
        if (!PathSegments.isSimpleSegment(language)) {
            throw new IllegalArgumentException(
                    "Prompt template catalog language must be a non-blank simple path segment but was " + language
                            + ".");
        }
        this.language = language;
        if (PromptRuntimeConfig.SOURCE_TYPE_CLASSPATH.equals(sourceType)) {
            this.sourceType = PromptRuntimeConfig.SOURCE_TYPE_CLASSPATH;
        } else if (PromptRuntimeConfig.SOURCE_TYPE_LOCAL_FILE.equals(sourceType)) {
            this.sourceType = PromptRuntimeConfig.SOURCE_TYPE_LOCAL_FILE;
        } else {
            throw new IllegalArgumentException("Unsupported prompt source type: " + sourceType);
        }
        if (PromptRuntimeConfig.SOURCE_TYPE_LOCAL_FILE.equals(this.sourceType)
                && (localRootDir == null || localRootDir.isBlank())) {
            throw new IllegalArgumentException("Prompt resource local root directory is required for sourceType '"
                    + PromptRuntimeConfig.SOURCE_TYPE_LOCAL_FILE
                    + "' but is not set; configure " + A2ATConfigKeys.PromptRuntime.LOCAL_ROOT_DIR
                    + " to the local prompt resource root.");
        }
        this.localRootDir = localRootDir == null || localRootDir.isBlank() ? null : Path.of(localRootDir);
        if (this.localRootDir != null && !Files.isDirectory(this.localRootDir)) {
            throw new IllegalArgumentException("Prompt resource local root directory configured via "
                    + A2ATConfigKeys.PromptRuntime.LOCAL_ROOT_DIR
                    + " does not exist or is not a directory: "
                    + this.localRootDir);
        }
        this.classpathSnapshot = captureClasspathTemplates();
        this.localSnapshot =
                PromptRuntimeConfig.SOURCE_TYPE_LOCAL_FILE.equals(this.sourceType) && this.localRootDir != null
                        ? Map.copyOf(localTemplates())
                        : Map.of();
    }

    private Map<String, String> captureClasspathTemplates() {
        try {
            return Map.copyOf(classpathTemplates());
        } catch (IOException | URISyntaxException exception) {
            LOGGER.atWarn().log("prompt_template_catalog_unavailable root=classpath reason={}", exception.getMessage());
            return Map.of();
        }
    }

    /**
     * Lists every loadable template of the configured language across all extensions of the effective set.
     *
     * <p>The effective set depends on the configured {@code sourceType}: {@code classpath} lists the full built-in
     * tree, {@code local_file} lists the local business content plus the classpath-fixed Negotiation-T templates.
     * Negotiation-T templates outside the closed set are ignored with a warning. The result is sorted by template URI.
     *
     * @return loadable templates of the configured language sorted by URI; empty when none can be loaded
     */
    public @NonNull List<PromptTemplate> loadAll() {
        Map<String, TemplateSource> entriesByPath = new LinkedHashMap<>();
        collectClasspathEntries(entriesByPath);
        collectLocalEntries(entriesByPath);
        List<PromptTemplate> templates = new ArrayList<>();
        String languageSuffix = "/" + language + "/" + TEMPLATE_FILE_NAME;
        for (Map.Entry<String, TemplateSource> entry : entriesByPath.entrySet()) {
            String path = entry.getKey();
            if (!path.endsWith(languageSuffix)) {
                continue;
            }
            String uri = path.substring(CLASSPATH_TEMPLATE_ROOT.length(), path.length() - languageSuffix.length());
            if (!isCatalogableUri(uri)) {
                LOGGER.atDebug().log("prompt_template_skipped path={} reason=not_a_template_uri", path);
                continue;
            }
            if (isNegotiationUri(uri) && !isClosedSetNegotiationUri(uri)) {
                LOGGER.atWarn().log("negotiation_template_outside_closed_set uri={}", uri);
                continue;
            }
            String content = entry.getValue().content();
            TemplateUri templateUri = TemplateUri.parse(uri)
                    .orElseThrow(() -> new IllegalStateException(
                            "Catalogable template URI could not be parsed: " + uri + " (path: " + path + ")"));
            templates.add(new PromptTemplate(
                    templateUri,
                    TemplateDescriptions.extract(content),
                    content,
                    entry.getValue().source()));
        }
        templates.sort(Comparator.comparing(template -> template.templateUri().uri()));
        LOGGER.atDebug().log("prompt_templates_listed count={} language={}", templates.size(), language);
        return List.copyOf(templates);
    }

    /**
     * Loads one template of the configured language by its URI, regardless of the extension.
     *
     * <p>A Negotiation-T template outside the closed set is answered with an empty result and a warning. Business
     * content follows the configured {@code sourceType}: {@code local_file} reads the addressed template from the local
     * root only (no classpath fallback), {@code classpath} reads it from the classpath.
     *
     * @param templateUri template URI such as {@code Negotiation-T/information-negotiation/propose/v1} or
     *     {@code Task-T/network-layer/ran-energy-saving/v1}
     * @return the addressed template, or an empty optional when no template exists for it in the configured language
     * @throws NullPointerException if the template URI is null
     */
    public Optional<PromptTemplate> load(@NonNull TemplateUri templateUri) {
        Objects.requireNonNull(templateUri, "templateUri");
        String uri = templateUri.uri();
        boolean negotiation = NEGOTIATION_EXTENSION.equals(templateUri.extensionName());
        if (negotiation && !isClosedSetNegotiationUri(uri)) {
            return Optional.empty();
        }
        String classpathPath = CLASSPATH_ROOT + String.join("/", TEMPLATE_DIRECTORY, uri, language, TEMPLATE_FILE_NAME);
        String source;
        String content;
        if (negotiation || PromptRuntimeConfig.SOURCE_TYPE_CLASSPATH.equals(sourceType)) {
            source = PromptTemplate.SOURCE_CLASSPATH;
            content = classpathSnapshot.get(classpathPath);
        } else {
            source = PromptTemplate.SOURCE_LOCAL;
            content = localSnapshot.get(classpathPath);
        }
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(new PromptTemplate(templateUri, TemplateDescriptions.extract(content), content, source));
    }

    private void collectClasspathEntries(Map<String, TemplateSource> entriesByPath) {
        for (Map.Entry<String, String> entry : classpathSnapshot.entrySet()) {
            if (PromptRuntimeConfig.SOURCE_TYPE_LOCAL_FILE.equals(sourceType) && !isNegotiationPath(entry.getKey())) {
                continue;
            }
            entriesByPath.put(entry.getKey(), new TemplateSource(entry.getValue(), PromptTemplate.SOURCE_CLASSPATH));
        }
    }

    private void collectLocalEntries(Map<String, TemplateSource> entriesByPath) {
        if (!PromptRuntimeConfig.SOURCE_TYPE_LOCAL_FILE.equals(sourceType) || localRootDir == null) {
            return;
        }
        for (Map.Entry<String, String> entry : localSnapshot.entrySet()) {
            if (isNegotiationPath(entry.getKey())) {
                continue;
            }
            entriesByPath.put(entry.getKey(), new TemplateSource(entry.getValue(), PromptTemplate.SOURCE_LOCAL));
        }
    }

    private Map<String, String> classpathTemplates() throws IOException, URISyntaxException {
        Map<String, String> templates = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = PromptTemplateCatalog.class.getClassLoader();
        }
        Enumeration<URL> roots = classLoader.getResources(CLASSPATH_TEMPLATE_ROOT);
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if ("file".equals(root.getProtocol())) {
                collectFilesystemTemplates(Path.of(root.toURI()), templates);
            } else if ("jar".equals(root.getProtocol())) {
                collectJarTemplates((JarURLConnection) root.openConnection(), templates);
            } else {
                LOGGER.atDebug().log("prompt_template_root_skipped url={} reason=unsupported_protocol", root);
            }
        }
        return templates;
    }

    private static void collectFilesystemTemplates(Path rootDirectory, Map<String, String> templates)
            throws IOException {
        try (Stream<Path> paths = Files.walk(rootDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(TEMPLATE_FILE_NAME))
                    .forEach(path -> collectTemplate(
                            templates,
                            CLASSPATH_TEMPLATE_ROOT
                                    + rootDirectory.relativize(path).toString().replace('\\', '/'),
                            path));
        }
    }

    private static void collectJarTemplates(JarURLConnection connection, Map<String, String> templates)
            throws IOException {
        try (java.util.jar.JarFile jarFile = connection.getJarFile()) {
            jarFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith(CLASSPATH_TEMPLATE_ROOT))
                    .filter(entry -> entry.getName().endsWith(TEMPLATE_FILE_NAME))
                    .forEach(entry -> collectTemplate(templates, entry.getName(), null));
        }
    }

    private static void collectTemplate(Map<String, String> templates, String classpathPath, Path filesystemPath) {
        try {
            String content;
            if (filesystemPath != null) {
                content = Files.readString(filesystemPath, StandardCharsets.UTF_8);
            } else {
                InputStream stream = ClasspathResourceStreams.open(classpathPath);
                if (stream == null) {
                    LOGGER.atDebug().log(
                            "prompt_template_skipped path={} reason=classpath_entry_unreadable", classpathPath);
                    return;
                }
                try (stream) {
                    content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            templates.put(classpathPath, content);
        } catch (IOException exception) {
            LOGGER.atDebug().log("prompt_template_skipped path={} reason=read_failed", classpathPath);
        }
    }

    private Map<String, String> localTemplates() {
        Map<String, String> templates = new LinkedHashMap<>();
        Path templateRoot = localRootDir.resolve(TEMPLATE_DIRECTORY);
        if (!Files.isDirectory(templateRoot)) {
            return templates;
        }
        try (Stream<Path> paths = Files.walk(templateRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(TEMPLATE_FILE_NAME))
                    .forEach(path -> collectTemplate(
                            templates,
                            CLASSPATH_TEMPLATE_ROOT
                                    + templateRoot.relativize(path).toString().replace('\\', '/'),
                            path));
        } catch (IOException exception) {
            LOGGER.atWarn()
                    .log("prompt_template_catalog_unavailable root={} reason={}", templateRoot, exception.getMessage());
        }
        return templates;
    }

    private static boolean isNegotiationPath(String path) {
        if (!path.startsWith(CLASSPATH_TEMPLATE_ROOT)) {
            return false;
        }
        String relative = path.substring(CLASSPATH_TEMPLATE_ROOT.length());
        int slash = relative.indexOf('/');
        return slash >= 0 && NEGOTIATION_EXTENSION.equals(relative.substring(0, slash));
    }

    private static boolean isNegotiationUri(String uri) {
        int slash = uri.indexOf('/');
        return slash >= 0 && NEGOTIATION_EXTENSION.equals(uri.substring(0, slash));
    }

    private static boolean isClosedSetNegotiationUri(String uri) {
        String[] segments = uri.split("/");
        if (segments.length != 4) {
            return false;
        }
        if (!NEGOTIATION_EXTENSION.equals(segments[0]) || !TemplateUri.DEFAULT_TEMPLATE_VERSION.equals(segments[3])) {
            return false;
        }
        String type = segments[1];
        String phase = segments[2];
        if ("common".equals(type)) {
            return "abort".equals(phase);
        }
        return NEGOTIATION_TYPES.contains(type) && NEGOTIATION_PHASES.contains(phase);
    }

    private static boolean isCatalogableUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String[] segments = uri.split("/");
        if (segments.length < MINIMUM_URI_SEGMENTS) {
            return false;
        }
        for (String segment : segments) {
            if (!PathSegments.isSimpleSegment(segment)) {
                return false;
            }
        }
        return true;
    }

    private record TemplateSource(String content, String source) {}
}
