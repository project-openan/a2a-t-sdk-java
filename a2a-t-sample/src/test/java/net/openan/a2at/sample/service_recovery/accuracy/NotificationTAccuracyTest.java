package net.openan.a2at.sample.service_recovery.accuracy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class NotificationTAccuracyTest {

    private static final ObjectMapper MAPPER =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private static final String TEMPLATE_URI_STR = "Notification-T/network-layer/service-recovery/v1";
    private static final String DATA_SCHEMA_RESOURCE = "sample/service-recovery/client/schema.json";
    private static final String VALIDATION_SCHEMA_RESOURCE = "sample/service-recovery/server/schema.json";

    private static A2ATClient client;
    private static A2ATServer server;
    private static String templateUri;
    private static Map<String, Object> dataSchema;
    private static Map<String, Object> validationSchema;
    private static boolean apiKeyConfigured;
    private static Map<String, String> envInfo;

    private static final List<Map<String, Object>> fromTextResults = new ArrayList<>();
    private static final List<Map<String, Object>> fromDataResults = new ArrayList<>();
    private static final List<Map<String, Object>> validateResults = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        templateUri = TEMPLATE_URI_STR;

        dataSchema = loadJsonResource(DATA_SCHEMA_RESOURCE);
        validationSchema = loadJsonResource(VALIDATION_SCHEMA_RESOURCE);

        Path env = resolveEnvFile("service-recovery-accuracy-test/client.env");
        envInfo = readEnvInfo(env);

        String apiKey = envInfo.get("A2AT_LLM_API_KEY");
        apiKeyConfigured = apiKey != null && !apiKey.isBlank() && !apiKey.contains("${");

        if (apiKeyConfigured) {
            client = new A2ATClient(env);
            server = new A2ATServer(env);
        }
    }

    @AfterAll
    static void writeReport() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();

        Map<String, Object> testRun = new LinkedHashMap<>();
        testRun.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        testRun.put("env", envInfo);
        testRun.put("apiKeyConfigured", apiKeyConfigured);
        report.put("testRun", testRun);

        report.put("generateNotificationPromptFromText", Map.of("testCases", fromTextResults));
        report.put("generateNotificationPromptFromDataWithSchema", Map.of("testCases", fromDataResults));
        report.put("validateNotificationPromptAndDataFilling", Map.of("testCases", validateResults));

        Path reportDir = Path.of("target");
        Files.createDirectories(reportDir);
        Path reportFile = reportDir.resolve("service-recovery-accuracy-report.json");
        MAPPER.writeValue(reportFile.toFile(), report);
        System.out.println("Report written to: " + reportFile.toAbsolutePath().normalize());
    }

    @Test
    void generateNotificationPromptFromText() throws Exception {
        if (!apiKeyConfigured) {
            System.out.println("SKIP: generateNotificationPromptFromText - API key not configured");
            return;
        }
        List<TextCase> cases = loadTextCases("service-recovery-accuracy-test/test-cases-generate-from-text.json");
        System.out.println("== generateNotificationPromptFromText ==");
        boolean first = true;
        for (TextCase tc : cases) {
            if (!first) System.out.println("---");
            first = false;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tc.name);
            entry.put("input", Map.of("text", tc.text));

            try {
                MetadataContent result = client.generateNotificationPromptFromText(tc.text, templateUri);
                entry.put("output", metadataOutput(result));
                System.out.println("  " + tc.name + ": " + compact(result.promptText()));
            } catch (Exception e) {
                entry.put("output", Map.of("error", e.getClass().getName() + ": " + e.getMessage()));
                System.out.println("  " + tc.name + ": ERROR - " + e.getMessage());
            }
            fromTextResults.add(entry);
        }
    }

    @Test
    void generateNotificationPromptFromDataWithSchema() throws Exception {
        if (!apiKeyConfigured) {
            System.out.println("SKIP: generateNotificationPromptFromDataWithSchema - API key not configured");
            return;
        }
        List<DataCase> cases = loadDataCases("service-recovery-accuracy-test/test-cases-generate-from-data.json");
        System.out.println("== generateNotificationPromptFromDataWithSchema ==");
        boolean first = true;
        for (DataCase dc : cases) {
            if (!first) System.out.println("---");
            first = false;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", dc.name);
            entry.put("input", Map.of("data", dc.data));

            try {
                MetadataContent result =
                        client.generateNotificationPromptFromDataWithSchema(dc.data, dataSchema, templateUri);
                entry.put("output", metadataOutput(result));
                System.out.println("  " + dc.name + ": " + compact(result.promptText()));
            } catch (Exception e) {
                entry.put("output", Map.of("error", e.getClass().getName() + ": " + e.getMessage()));
                System.out.println("  " + dc.name + ": ERROR - " + e.getMessage());
            }
            fromDataResults.add(entry);
        }
    }

    private static Map<String, Object> validate(String promptText) {
        try {
            FilledParamData result =
                    server.validateNotificationPromptAndDataFilling(promptText, validationSchema, templateUri);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("semantic_verdict", true);
            output.put("params", result.data());
            return output;
        } catch (ContentValidationException e) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("semantic_verdict", false);
            output.put("code", e.getCode());
            output.put("message", e.getMessage());
            if (!e.errors().isEmpty()) {
                output.put(
                        "errors",
                        e.errors().stream()
                                .map(err -> {
                                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                                    item.put("slot_name", err.slotName());
                                    item.put("code", err.code());
                                    item.put("message", err.message());
                                    item.put("facts", err.facts());
                                    return item;
                                })
                                .toList());
            }
            return output;
        } catch (Exception e) {
            return Map.of("error", e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    void validateNotificationPromptAndDataFilling() throws Exception {
        if (!apiKeyConfigured) {
            System.out.println("SKIP: validateNotificationPromptAndDataFilling - API key not configured");
            return;
        }
        List<ValidateCase> cases =
                loadValidateCases("service-recovery-accuracy-test/test-cases-validate-and-fill.json");
        System.out.println("== validateNotificationPromptAndDataFilling ==");
        boolean first = true;
        for (ValidateCase vc : cases) {
            if (!first) System.out.println("---");
            first = false;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", vc.name);
            entry.put("input", Map.of("promptText", vc.promptText));
            Map<String, Object> output = validate(vc.promptText);
            entry.put("output", output);
            System.out.println("  " + vc.name + ": " + MAPPER.writeValueAsString(output));
            validateResults.add(entry);
        }
    }

    private static Map<String, Object> metadataOutput(MetadataContent result) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("templateUri", result.templateUri());
        output.put("extensionUri", result.extensionUri());
        output.put("promptText", result.promptText());
        return output;
    }

    private static String compact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceAll("(?m)^[ \t]*\\r?\\n", "");
    }

    private static Path resolveEnvFile(String resourcePath) throws IOException, URISyntaxException {
        URL url = NotificationTAccuracyTest.class.getClassLoader().getResource(resourcePath);
        Objects.requireNonNull(url, "Resource not found: " + resourcePath);
        Path envPath = Path.of(url.toURI());
        Path projectRoot = findProjectRoot(envPath.getParent());

        String content = Files.readString(envPath, StandardCharsets.UTF_8);
        String resolvedContent = resolveRootDir(content, projectRoot);

        Path tempFile = Files.createTempFile("notification-t-test-", ".env");
        Files.writeString(tempFile, resolvedContent, StandardCharsets.UTF_8);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    private static Path findProjectRoot(Path start) {
        Path dir = start;
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("a2a-t-resources"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Project root (containing a2a-t-resources) not found above " + start);
    }

    private static String resolveRootDir(String content, Path projectRoot) {
        String prefix = "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=";
        Path promptRoot = projectRoot
                .resolve("a2a-t-resources/src/main/resources/prompt_resources")
                .normalize();
        String absolutePath = promptRoot.toString();
        for (String line : content.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith(prefix)) {
                return content.replace(trimmed, prefix + absolutePath);
            }
        }
        return content;
    }

    private static Map<String, String> readEnvInfo(Path envPath) throws IOException {
        Map<String, String> info = new LinkedHashMap<>();
        for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx > 0) {
                String key = trimmed.substring(0, eqIdx).strip();
                String value = trimmed.substring(eqIdx + 1).strip();
                if (key.equals("A2AT_LLM_API_KEY")) {
                    value = value.isBlank() ? "" : "***";
                }
                info.put(key, value);
            }
        }
        return info;
    }

    private static Map<String, Object> loadJsonResource(String resourcePath) throws IOException {
        try (InputStream is = NotificationTAccuracyTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(is, "Resource not found: " + resourcePath);
            return MAPPER.readValue(is, new TypeReference<Map<String, Object>>() {});
        }
    }

    private static List<TextCase> loadTextCases(String resourcePath) throws IOException {
        try (InputStream is = NotificationTAccuracyTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(is, "Resource not found: " + resourcePath);
            return MAPPER.readValue(is, new TypeReference<List<TextCase>>() {});
        }
    }

    private static List<DataCase> loadDataCases(String resourcePath) throws IOException {
        try (InputStream is = NotificationTAccuracyTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(is, "Resource not found: " + resourcePath);
            return MAPPER.readValue(is, new TypeReference<List<DataCase>>() {});
        }
    }

    private static List<ValidateCase> loadValidateCases(String resourcePath) throws IOException {
        try (InputStream is = NotificationTAccuracyTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(is, "Resource not found: " + resourcePath);
            return MAPPER.readValue(is, new TypeReference<List<ValidateCase>>() {});
        }
    }

    private record TextCase(String name, String text) {}

    private record DataCase(String name, Map<String, Object> data) {}

    private record ValidateCase(String name, String promptText) {}
}
