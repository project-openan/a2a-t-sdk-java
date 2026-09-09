package net.openan.a2at.sdk.prompt.resources.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.core.model.TemplateUri;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class TemplateQueryServiceTest {

    private static final String LANGUAGE = "en-US";

    private static final String CLASSPATH = "classpath";

    private static final String LOCAL = "local";

    private static final List<String> NEGOTIATION_CLOSED_SET = List.of(
            "Negotiation-T/common/abort/v1",
            "Negotiation-T/feasibility-negotiation/accept-reject/v1",
            "Negotiation-T/feasibility-negotiation/propose/v1",
            "Negotiation-T/information-negotiation/accept-reject/v1",
            "Negotiation-T/information-negotiation/propose/v1",
            "Negotiation-T/target-negotiation/accept-reject/v1",
            "Negotiation-T/target-negotiation/propose/v1");

    @TempDir
    Path localRootDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void attachAppender() {
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void classpathSourceTypeListsBuiltInTemplatesWithClasspathSource() {
        TemplateQueryService service = new TemplateQueryService(LANGUAGE, "classpath", null);

        List<PromptTemplate> templates = service.getPrompts();

        assertFalse(templates.isEmpty());
        assertTrue(templates.stream().allMatch(t -> CLASSPATH.equals(t.source())));
        assertSortedByUri(templates);
        Set<String> uris = templates.stream().map(t -> t.templateUri().uri()).collect(Collectors.toSet());
        assertTrue(uris.containsAll(NEGOTIATION_CLOSED_SET));
    }

    @Test
    void localFileSourceTypeListsLocalBusinessContentUnionClasspathNegotiationOnly() throws IOException {
        writeTemplate(localRootDir, "Task-T/network-layer/custom-planning/v1", "<!-- Custom task -->\nbody");

        TemplateQueryService service = new TemplateQueryService(LANGUAGE, "local_file", localRootDir.toString());

        List<PromptTemplate> templates = service.getPrompts();
        Set<String> uris = templates.stream().map(t -> t.templateUri().uri()).collect(Collectors.toSet());

        assertTrue(uris.contains("Task-T/network-layer/custom-planning/v1"));
        assertFalse(uris.contains("Task-T/network-layer/ran-energy-saving/v1"));
        assertFalse(uris.contains("Notification-T/network-layer/subscribe-incident/v1"));
        assertFalse(uris.contains("Authorization-T/authorization-policy-management/v1"));
        for (String negotiationUri : NEGOTIATION_CLOSED_SET) {
            assertTrue(uris.contains(negotiationUri));
        }
    }

    @Test
    void localFileSourceTypeAnnotatesLocalAndClasspathSources() throws IOException {
        writeTemplate(localRootDir, "Task-T/network-layer/custom-planning/v1", "<!-- Custom task -->\nbody");

        TemplateQueryService service = new TemplateQueryService(LANGUAGE, "local_file", localRootDir.toString());

        List<PromptTemplate> templates = service.getPrompts();
        PromptTemplate localTask = templates.stream()
                .filter(t -> t.templateUri().uri().equals("Task-T/network-layer/custom-planning/v1"))
                .findFirst()
                .orElseThrow();
        assertEquals(LOCAL, localTask.source());

        assertTrue(
                templates.stream()
                        .filter(t -> t.templateUri().extensionName().equals("Negotiation-T"))
                        .allMatch(t -> CLASSPATH.equals(t.source())),
                "all negotiation templates must be sourced from the classpath");
    }

    @Test
    void closedSetFilterExcludesOutOfSetNegotiationTemplates() throws IOException {
        writeTemplate(localRootDir, "Negotiation-T/feasibility-negotiation/revise/v1", "<!-- outside -->\nbody");
        writeTemplate(
                localRootDir, "Negotiation-T/information-negotiation/propose/v1", "<!-- local override -->\nbody");

        TemplateQueryService service = new TemplateQueryService(LANGUAGE, "local_file", localRootDir.toString());

        Set<String> uris =
                service.getPrompts().stream().map(t -> t.templateUri().uri()).collect(Collectors.toSet());

        assertFalse(uris.contains("Negotiation-T/feasibility-negotiation/revise/v1"));
        assertTrue(uris.contains("Negotiation-T/information-negotiation/propose/v1"));
        assertTrue(
                service.getPrompts().stream()
                        .filter(t -> t.templateUri().uri().equals("Negotiation-T/information-negotiation/propose/v1"))
                        .allMatch(t -> CLASSPATH.equals(t.source())),
                "the in-closed-set negotiation template must come from the classpath, not the local override");

        assertTrue(service.getPrompt(TemplateUri.of("Negotiation-T", "feasibility-negotiation", "revise"))
                .isEmpty());
    }

    @Test
    void localFileSourceTypeFreezesCatalogSnapshotIgnoringRuntimeFileChanges() throws IOException {
        writeTemplate(localRootDir, "Task-T/network-layer/custom-planning/v1", "<!-- Custom -->\nbody");

        TemplateQueryService service = new TemplateQueryService(LANGUAGE, "local_file", localRootDir.toString());
        Set<String> before =
                service.getPrompts().stream().map(t -> t.templateUri().uri()).collect(Collectors.toSet());

        writeTemplate(localRootDir, "Notification-T/network-layer/added-after-construction/v1", "<!-- new -->\nbody");
        Files.delete(localRootDir
                .resolve("templates")
                .resolve("Task-T/network-layer/custom-planning/v1")
                .resolve(LANGUAGE)
                .resolve("template.md"));

        Set<String> after =
                service.getPrompts().stream().map(t -> t.templateUri().uri()).collect(Collectors.toSet());

        assertEquals(before, after);
    }

    @Test
    void closedSetMissLogsSinglePromptTemplateNotFoundWarning() {
        new TemplateQueryService(LANGUAGE, "classpath", null)
                .getPrompt(TemplateUri.of("Negotiation-T", "feasibility-negotiation", "revise"));

        List<String> warnings = warningEvents();
        assertEquals(1, warnings.size(), "expected exactly one WARN for querying a closed-set-outside negotiation URI");
        assertTrue(
                warnings.get(0).startsWith("prompt_template_not_found"),
                "expected a prompt_template_not_found WARN, not a negotiation_template_outside_closed_set WARN");
    }

    @Test
    void unsupportedSourceTypeFailsFast() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TemplateQueryService(LANGUAGE, "database", localRootDir.toString()));

        assertEquals("Unsupported prompt source type: database", exception.getMessage());
    }

    @Test
    void localFileSourceTypeWithoutRootFailsFast() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> new TemplateQueryService(LANGUAGE, "local_file", null));

        assertTrue(exception.getMessage().contains("A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR"));
    }

    @Test
    void localFileSourceTypeWithBlankRootFailsFast() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> new TemplateQueryService(LANGUAGE, "local_file", "   "));

        assertTrue(exception.getMessage().contains("A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR"));
    }

    @Test
    void localFileSourceTypeWithNonexistentRootFailsFast() {
        Path missing = localRootDir.resolve("missing");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TemplateQueryService(LANGUAGE, "local_file", missing.toString()));

        assertTrue(exception.getMessage().contains("A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR"));
    }

    @Test
    void singleQueryMissLogsPromptTemplateNotFoundWarning() {
        new TemplateQueryService(LANGUAGE, "classpath", null)
                .getPrompt(TemplateUri.of("Task-T", "network-layer", "does-not-exist"));

        assertTrue(
                warningEvents().stream().anyMatch(message -> message.startsWith("prompt_template_not_found")),
                "expected a WARN for a missing single template query");
    }

    private List<String> warningEvents() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static void writeTemplate(Path root, String uri, String content) throws IOException {
        Path file = root.resolve("templates").resolve(uri).resolve(LANGUAGE).resolve("template.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void assertSortedByUri(List<PromptTemplate> templates) {
        for (int i = 1; i < templates.size(); i++) {
            assertTrue(
                    templates
                                    .get(i - 1)
                                    .templateUri()
                                    .uri()
                                    .compareTo(templates.get(i).templateUri().uri())
                            <= 0,
                    "templates must be sorted by URI");
        }
    }
}
