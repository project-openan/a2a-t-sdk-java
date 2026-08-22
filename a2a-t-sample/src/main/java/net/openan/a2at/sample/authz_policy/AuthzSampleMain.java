package net.openan.a2at.sample.authz_policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioResult;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.server.A2ATServer;

public final class AuthzSampleMain {

    private static final String DEFAULT_ENV_FILE = "authz.env";

    private static final String BUNDLED_ENV_FILE = Path.of(
                    "a2a-t-sample", "src", "main", "resources", "sample", "authz-policy", "authz.env")
            .toString();

    private static final String SCENARIOS_RESOURCE = "sample/authz-policy/scenarios.json";

    private static final String SLOT_SCHEMA_PATH_TEMPLATE =
            "/prompt_resources/slots/Authorization-T/authorization-policy-management/v1/%s/slot.json";

    private static final List<String> REQUIRED_LLM_KEYS =
            List.of("A2AT_LLM_PROVIDER", "A2AT_LLM_MODEL", "A2AT_LLM_API_KEY");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuthzSampleMain() {}

    public static void main(String[] args) {
        Path envPath = resolveEnvPath(args);
        if (!hasRequiredLlmKeys(envPath)) {
            System.err.println("Required LLM keys not configured in env file: " + envPath);
            System.exit(1);
        }

        A2ATClient client = new A2ATClient(envPath);
        A2ATServer server = new A2ATServer(envPath);

        String language = A2ATConfig.load(envPath).prompt().language();
        Map<String, Object> slotSchemaMap = loadSlotSchemaMap(language);

        List<AuthzScenario> scenarios = AuthzScenarioLoader.load(SCENARIOS_RESOURCE);
        TemplateUri templateUri = StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT;

        AuthzPromptGenerator generator = scenario -> {
            if (AuthzScenario.FROM_TEXT.equals(scenario.entry())) {
                String text = (String) scenario.input().get("text");
                return client.generateAuthPromptFromText(text, templateUri);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) scenario.input().get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) scenario.input().get("schema");
            return client.generateAuthPromptFromDataWithSchema(data, schema, templateUri);
        };

        AuthzPromptValidator validator = server::validateAuthPromptAndDataFilling;
        AuthzScenarioRunner runner = new AuthzScenarioRunner(generator, validator);

        List<ScenarioResult> results = new ArrayList<>();
        for (AuthzScenario scenario : scenarios) {
            ScenarioOutcome outcome = runner.run(scenario, slotSchemaMap, templateUri);
            printScenarioReport(scenario, outcome);
            results.add(outcome.result());
        }

        printSummary(scenarios, results);
        System.exit(exitCode(results));
    }

    static Path resolveEnvPath(String[] args) {
        if (args.length > 0) {
            return Path.of(args[0]);
        }
        Path cwdEnv = Path.of(DEFAULT_ENV_FILE);
        if (Files.exists(cwdEnv) && hasRequiredLlmKeys(cwdEnv)) {
            return cwdEnv;
        }
        return Path.of(BUNDLED_ENV_FILE);
    }

    static boolean hasRequiredLlmKeys(Path envFile) {
        Map<String, String> entries = new HashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                entries.put(
                        trimmed.substring(0, separator).trim(),
                        trimmed.substring(separator + 1).trim());
            }
        } catch (IOException exception) {
            return false;
        }
        for (String key : REQUIRED_LLM_KEYS) {
            if (entries.getOrDefault(key, "").isBlank()) {
                return false;
            }
        }
        return true;
    }

    static Map<String, Object> loadSlotSchemaMap(String language) {
        String resourcePath = String.format(SLOT_SCHEMA_PATH_TEMPLATE, language);
        try (InputStream stream = AuthzSampleMain.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Slot schema resource not found: " + resourcePath);
            }
            return MAPPER.readValue(stream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load slot schema: " + resourcePath, e);
        }
    }

    static void printScenarioReport(AuthzScenario scenario, ScenarioOutcome outcome) {
        System.out.println("--- Scenario: " + scenario.label() + " ---");
        System.out.println("  Entry: " + scenario.entry() + ", Expected: " + scenario.expected());
        MetadataContent metadata = outcome.metadata();
        if (metadata == null) {
            System.out.println("  Prompt: <生成失败>");
        } else {
            System.out.println("  Prompt: " + metadata.promptText());
            System.out.println("  TemplateUri: " + metadata.templateUri());
            System.out.println("  ExtensionUri: " + metadata.extensionUri());
        }
        System.out.println("  Verdict: " + outcome.result().name());
        FilledParamData filled = outcome.filled();
        if (filled == null) {
            System.out.println("  Extracted Params: <未提取参数>");
        } else {
            System.out.println("  Extracted Params: " + filled.data());
        }
        if (outcome.result().error() != null) {
            System.out.println("  Error: [" + outcome.result().error().getCode() + "] "
                    + outcome.result().error().getMessage());
        }
        System.out.println();
    }

    static void printSummary(List<AuthzScenario> scenarios, List<ScenarioResult> results) {
        long passCount = results.stream().filter(r -> AuthzScenarioRunner.VERDICT_PASS.equals(r.name())).count();
        long failCount = results.stream().filter(r -> AuthzScenarioRunner.VERDICT_FAIL.equals(r.name())).count();
        long errorCount = results.stream().filter(r -> AuthzScenarioRunner.VERDICT_ERROR.equals(r.name())).count();
        System.out.println("========== Summary ==========");
        System.out.println("  Total: " + results.size() + ", PASS: " + passCount + ", FAIL: " + failCount + ", ERROR: "
                + errorCount);
        System.out.println("  Exit Code: " + exitCode(results));
        System.out.println();
    }

    static int exitCode(List<ScenarioResult> results) {
        boolean allPass = results.stream().allMatch(r -> AuthzScenarioRunner.VERDICT_PASS.equals(r.name()));
        return allPass ? 0 : 1;
    }
}
