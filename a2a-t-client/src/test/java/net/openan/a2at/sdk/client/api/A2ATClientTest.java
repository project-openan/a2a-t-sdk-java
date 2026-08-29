package net.openan.a2at.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class A2ATClientTest {

    private static final String TEST_MOCK_PROVIDER = "test-a2at-mock";

    @BeforeAll
    static void registerMockProvider() {
        if (!LLMClientFactory.availableProviders().contains(TEST_MOCK_PROVIDER)) {
            LLMClientFactory.register(TEST_MOCK_PROVIDER, RecordingClient.class);
        }
    }

    @BeforeEach
    void resetRecordingClientState() {
        RecordingClient.SLOT_OVERRIDES.clear();
        RecordingClient.REQUEST_COUNT.set(0);
    }

    @Test
    void onlyExposesPathBasedPublicConstructor() throws NoSuchMethodException {
        assertNotNull(A2ATClient.class.getConstructor(Path.class));
        assertEquals(1, A2ATClient.class.getConstructors().length);
        assertEquals(1, A2ATClient.class.getDeclaredConstructors().length);
    }

    @Test
    void promptGenerationResultDoesNotExposeScenarioValidationOrSlotsFields() {
        assertThrows(NoSuchMethodException.class, () -> PromptGenerationResult.class.getMethod("scenarioCode"));
        assertThrows(NoSuchMethodException.class, () -> PromptGenerationResult.class.getMethod("validation"));
        assertThrows(NoSuchMethodException.class, () -> PromptGenerationResult.class.getMethod("slots"));
    }

    @Test
    void noLongerExposesLowLevelPromptAndNegotiationConstructor() {
        assertThrows(
                NoSuchMethodException.class,
                () -> A2ATClient.class.getDeclaredConstructor(
                        ClientPromptGenerationOrchestrator.class,
                        net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator.class));
    }

    @Test
    void noLongerExposesLowLevelPromptGenerationAssemblyConstructors() {
        assertThrows(
                NoSuchMethodException.class,
                () -> A2ATClient.class.getDeclaredConstructor(
                        net.openan.a2at.sdk.llm.LLMClient.class,
                        net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator.class,
                        java.util.List.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class));
        assertThrows(
                NoSuchMethodException.class,
                () -> A2ATClient.class.getDeclaredConstructor(
                        net.openan.a2at.sdk.llm.LLMClient.class,
                        net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator.class,
                        java.util.List.class,
                        net.openan.a2at.sdk.core.model.PromptGenerationConfig.class));
    }

    @Test
    void doesNotKeepStaticAssemblyHelpersInsideFacade() {
        long staticMethodCount = Arrays.stream(A2ATClient.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                .filter(method -> !java.lang.reflect.Modifier.isPrivate(method.getModifiers()))
                .count();

        assertEquals(0, staticMethodCount);
    }

    @Test
    void pathBasedConstructorLoadsLocalPromptResources() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        RecordingClient.SLOT_OVERRIDES.put("site", "Site A");
        RecordingClient.SLOT_OVERRIDES.put("target", "Reduce power by 10%");
        A2ATClient client = new A2ATClient(envFile);

        PromptGenerationResult result =
                client.generateTaskPrompt(Map.of("site", "Site A", "target", "Reduce power by 10%"));

        assertTrue(result.success());
        assertEquals("Site: Site A\\nTarget: Reduce power by 10%", result.promptText());
        assertTrue(RecordingClient.REQUEST_COUNT.get() >= 1, "Map input must trigger LLM extraction");
    }

    @Test
    void scenarioRecognitionShortCircuitsWhenSingleScenarioIsConfigured() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        RecordingClient.SLOT_OVERRIDES.put("site", "Site A");
        RecordingClient.SLOT_OVERRIDES.put("target", "Reduce power by 10%");
        A2ATClient client = new A2ATClient(envFile);

        PromptGenerationResult result =
                client.generateTaskPrompt(Map.of("site", "Site A", "target", "Reduce power by 10%"));

        assertTrue(result.success());
        assertEquals(
                1,
                RecordingClient.REQUEST_COUNT.get(),
                "Single scenario must short-circuit recognition — only extraction LLM call expected");
    }

    @Test
    void scenarioRecognitionDelegatesToWrappedRecognizerWhenMultipleScenariosConfigured() throws IOException {
        Path envFile = writeMinimalMultiScenarioClientEnv();
        RecordingClient.SLOT_OVERRIDES.put("site", "Site A");
        RecordingClient.SLOT_OVERRIDES.put("target", "Reduce power by 10%");
        A2ATClient client = new A2ATClient(envFile);

        PromptGenerationResult result =
                client.generateTaskPrompt(Map.of("site", "Site A", "target", "Reduce power by 10%"));

        assertTrue(result.success());
        assertEquals(
                2,
                RecordingClient.REQUEST_COUNT.get(),
                "Multiple scenarios must delegate to wrapped recognizer — recognition + extraction calls expected");
    }

    @Test
    void pathBasedConstructorAcceptsRelativeEnvPath() throws IOException {
        Path targetDir = Files.createDirectories(Path.of("target"));
        Path tempDir = Files.createTempDirectory(targetDir, "a2at-client-relative-env");
        writeMinimalClientEnv(tempDir, TEST_MOCK_PROVIDER);
        Path relativeEnvPath =
                targetDir.getFileName().resolve(tempDir.getFileName()).resolve("client.env");

        RecordingClient.SLOT_OVERRIDES.put("site", "Site A");
        RecordingClient.SLOT_OVERRIDES.put("target", "Reduce power by 10%");
        A2ATClient client = new A2ATClient(relativeEnvPath);
        PromptGenerationResult result =
                client.generateTaskPrompt(Map.of("site", "Site A", "target", "Reduce power by 10%"));

        assertTrue(result.success());
        assertEquals("Site: Site A\\nTarget: Reduce power by 10%", result.promptText());
        assertTrue(RecordingClient.REQUEST_COUNT.get() >= 1, "Map input must trigger LLM extraction");
    }

    @Test
    void pathBasedConstructorLoadsClasspathPromptResources() throws IOException {
        Path envFile = writeMinimalClasspathClientEnv();

        A2ATClient client = new A2ATClient(envFile);

        assertNotNull(client);
    }

    @Test
    void pathBasedConstructorAcceptsOpenAiProvider() throws IOException {
        Path envFile = writeMinimalClientEnv(TEST_MOCK_PROVIDER);

        RecordingClient.SLOT_OVERRIDES.put("site", "Site A");
        RecordingClient.SLOT_OVERRIDES.put("target", "Reduce power by 10%");
        A2ATClient client = new A2ATClient(envFile);
        PromptGenerationResult result =
                client.generateTaskPrompt(Map.of("site", "Site A", "target", "Reduce power by 10%"));

        assertTrue(result.success());
        assertEquals("Site: Site A\\nTarget: Reduce power by 10%", result.promptText());
        assertTrue(RecordingClient.REQUEST_COUNT.get() >= 1, "Map input must trigger LLM extraction");
    }

    @Test
    void pathBasedConstructorBuildsDefaultNegotiationRuntime() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);

        Map<String, Object> startResult =
                client.startNegotiation(NegotiationType.TARGET, "Please clarify the target.", Map.of("site", "A"));
        @SuppressWarnings("unchecked")
        Map<String, Object> startData = (Map<String, Object>)
                startResult.get(net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler.NEGOTIATION_T_URI_NL);

        Map<String, Object> continueResult = client.continueNegotiation(
                new NegotiationContext(
                        NegotiationType.TARGET,
                        String.valueOf(startData.get("negotiationId")),
                        1,
                        NegotiationStatus.IN_PROGRESS),
                NegotiationStatus.IN_PROGRESS,
                "Site A");
        @SuppressWarnings("unchecked")
        Map<String, Object> continueData = (Map<String, Object>)
                continueResult.get(net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler.NEGOTIATION_T_URI_NL);

        assertEquals("Please clarify the target.", startData.get("message"));
        assertEquals("Site A", continueData.get("message"));
    }

    @Test
    void pathBasedConstructorSupportsNonEnergySavingLocalScenarioCatalog() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-client-private-line");
        Path promptRoot = tempDir.resolve("prompt_resources");
        Path scenariosDir = promptRoot.resolve("scenarios").resolve("zh-CN");
        Path templatesDir = promptRoot
                .resolve("templates")
                .resolve("Task-T")
                .resolve("network-layer")
                .resolve("private-line-complaint")
                .resolve("v1")
                .resolve("zh-CN");
        Path slotsDir = promptRoot
                .resolve("slots")
                .resolve("Task-T")
                .resolve("network-layer")
                .resolve("private-line-complaint")
                .resolve("v1")
                .resolve("zh-CN");
        Files.createDirectories(scenariosDir);
        Files.createDirectories(templatesDir);
        Files.createDirectories(slotsDir);

        Path scenarioPromptDir =
                promptRoot.resolve("prompts").resolve("scenario_recognition").resolve("zh-CN");
        Path slotPromptDir =
                promptRoot.resolve("prompts").resolve("slot_extraction").resolve("zh-CN");
        Files.createDirectories(scenarioPromptDir);
        Files.createDirectories(slotPromptDir);
        Files.writeString(scenarioPromptDir.resolve("system.md"), "Identify the best matching scenario.");
        Files.writeString(scenarioPromptDir.resolve("user.md"), "Choose from the provided scenario list.");
        Files.writeString(slotPromptDir.resolve("system.md"), "Extract slots from the input.");
        Files.writeString(slotPromptDir.resolve("user.md"), "Return slots as JSON.");

        Files.writeString(
                scenariosDir.resolve("scenarios.json"),
                """
                {
                  "scenarios": [
                    {
                      "scenario_code": "private-line-complaint",
                      "scenario_name": "Private Line Complaint",
                      "description": "Complaint analysis",
                      "example": "Analyze private line fault"
                    }
                  ]
                }
                """);
        Files.writeString(templatesDir.resolve("template.md"), "Line: {line_id}\\nFault: {fault_id}");
        Files.writeString(
                slotsDir.resolve("slot.json"),
                """
                {
                  "required": ["line_id", "fault_id"],
                  "properties": {
                    "line_id": {
                      "type": "string"
                    },
                    "fault_id": {
                      "type": "string"
                    }
                  }
                }
                """);

        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=local_file
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=prompt_resources
A2AT_LLM_PROVIDER=%s
A2AT_LLM_MODEL=example-model
A2AT_LLM_BASE_URL=https://llm.example.test/v1
A2AT_LLM_API_KEY=test-key
A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(TEST_MOCK_PROVIDER));

        RecordingClient.SLOT_OVERRIDES.put("line_id", "line-1");
        RecordingClient.SLOT_OVERRIDES.put("fault_id", "fault-9");
        A2ATClient client = new A2ATClient(envFile);
        PromptGenerationResult result = client.generateTaskPrompt(Map.of("line_id", "line-1", "fault_id", "fault-9"));

        assertTrue(result.success());
        assertEquals("Line: line-1\\nFault: fault-9", result.promptText());
        assertTrue(RecordingClient.REQUEST_COUNT.get() >= 1, "Map input must trigger LLM extraction");
    }

    @Test
    void fromTextEntryPointsReturnMetadataContent() throws IOException {
        Path envFile = writeMinimalClientEnvWithoutRequiredSlots(TEST_MOCK_PROVIDER);
        A2ATClient client = new A2ATClient(envFile);
        String unconstrained = "Task-T/network-layer/unconstrained/v1";

        MetadataContent taskResult = client.generateTaskPromptFromText("Please analyze Site A.", unconstrained);
        MetadataContent authResult = client.generateAuthPromptFromText("Authorize access.", unconstrained);
        MetadataContent notificationResult =
                client.generateNotificationPromptFromText("Report finished.", unconstrained);

        assertNotNull(taskResult);
        assertEquals("Task-T/network-layer/unconstrained/v1", taskResult.templateUri());
        assertNotNull(taskResult.promptText());
        assertNotNull(taskResult.extensionUri());

        assertNotNull(authResult);
        assertEquals("Task-T/network-layer/unconstrained/v1", authResult.templateUri());
        assertNotNull(authResult.promptText());
        assertNotNull(authResult.extensionUri());

        assertNotNull(notificationResult);
        assertEquals("Task-T/network-layer/unconstrained/v1", notificationResult.templateUri());
        assertNotNull(notificationResult.promptText());
        assertNotNull(notificationResult.extensionUri());
    }

    @Test
    void fromDataWithSchemaEntryPointsReturnMetadataContent() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);
        Map<String, Object> data = Map.of("site", "Site A", "target", "Reduce power by 10%");
        Map<String, Object> schema = Map.of("type", "object");

        MetadataContent taskResult =
                client.generateTaskPromptFromDataWithSchema(data, schema, StandardTemplates.ENERGY_SAVING_URI);
        MetadataContent authResult =
                client.generateAuthPromptFromDataWithSchema(data, schema, StandardTemplates.ENERGY_SAVING_URI);
        MetadataContent notificationResult =
                client.generateNotificationPromptFromDataWithSchema(data, schema, StandardTemplates.ENERGY_SAVING_URI);

        assertNotNull(taskResult);
        assertEquals(StandardTemplates.ENERGY_SAVING.uri(), taskResult.templateUri());
        assertNotNull(taskResult.promptText());
        assertNotNull(taskResult.extensionUri());

        assertNotNull(authResult);
        assertEquals(StandardTemplates.ENERGY_SAVING.uri(), authResult.templateUri());
        assertNotNull(authResult.promptText());
        assertNotNull(authResult.extensionUri());

        assertNotNull(notificationResult);
        assertEquals(StandardTemplates.ENERGY_SAVING.uri(), notificationResult.templateUri());
        assertNotNull(notificationResult.promptText());
        assertNotNull(notificationResult.extensionUri());
    }

    @Test
    void fromTextReturnsCorrectExtensionUriPerContentType() throws IOException {
        Path envFile = writeMinimalClientEnvWithoutRequiredSlots(TEST_MOCK_PROVIDER);
        A2ATClient client = new A2ATClient(envFile);
        String unconstrained = "Task-T/network-layer/unconstrained/v1";

        MetadataContent taskResult = client.generateTaskPromptFromText("Please analyze Site A.", unconstrained);
        MetadataContent authResult = client.generateAuthPromptFromText("Authorize access.", unconstrained);
        MetadataContent notificationResult =
                client.generateNotificationPromptFromText("Report finished.", unconstrained);

        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, taskResult.extensionUri());
        assertEquals(ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI, authResult.extensionUri());
        assertEquals(ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI, notificationResult.extensionUri());
    }

    @Test
    void fromDataWithSchemaReturnsCorrectExtensionUriPerContentType() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);
        Map<String, Object> data = Map.of("site", "Site A", "target", "Reduce power by 10%");
        Map<String, Object> schema = Map.of("type", "object");

        MetadataContent taskResult =
                client.generateTaskPromptFromDataWithSchema(data, schema, StandardTemplates.ENERGY_SAVING_URI);
        MetadataContent authResult =
                client.generateAuthPromptFromDataWithSchema(data, schema, StandardTemplates.ENERGY_SAVING_URI);
        MetadataContent notificationResult =
                client.generateNotificationPromptFromDataWithSchema(data, schema, StandardTemplates.ENERGY_SAVING_URI);

        assertEquals(ExtensionUriConstants.TASK_T_EXTENSION_URI, taskResult.extensionUri());
        assertEquals(ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI, authResult.extensionUri());
        assertEquals(ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI, notificationResult.extensionUri());
    }

    @Test
    void fromTextThrowsOnNullTemplateUri() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);

        NullPointerException taskEx = assertThrows(
                NullPointerException.class, () -> client.generateTaskPromptFromText("Please analyze Site A.", null));
        assertFalse(A2ATError.class.isInstance(taskEx), "null template URI must stay outside the A2ATError tree");
        NullPointerException authEx = assertThrows(
                NullPointerException.class, () -> client.generateAuthPromptFromText("Authorize access.", null));
        assertFalse(A2ATError.class.isInstance(authEx), "null template URI must stay outside the A2ATError tree");
        NullPointerException notifEx = assertThrows(
                NullPointerException.class, () -> client.generateNotificationPromptFromText("Report finished.", null));
        assertFalse(A2ATError.class.isInstance(notifEx), "null template URI must stay outside the A2ATError tree");
    }

    @Test
    void fromDataWithSchemaThrowsOnNullTemplateUri() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);
        Map<String, Object> data = Map.of("site", "Site A");
        Map<String, Object> schema = Map.of("type", "object");

        NullPointerException taskEx = assertThrows(
                NullPointerException.class, () -> client.generateTaskPromptFromDataWithSchema(data, schema, null));
        assertFalse(A2ATError.class.isInstance(taskEx), "null template URI must stay outside the A2ATError tree");
        NullPointerException authEx = assertThrows(
                NullPointerException.class, () -> client.generateAuthPromptFromDataWithSchema(data, schema, null));
        assertFalse(A2ATError.class.isInstance(authEx), "null template URI must stay outside the A2ATError tree");
        NullPointerException notifEx = assertThrows(
                NullPointerException.class,
                () -> client.generateNotificationPromptFromDataWithSchema(data, schema, null));
        assertFalse(A2ATError.class.isInstance(notifEx), "null template URI must stay outside the A2ATError tree");
    }

    @Test
    void fromTextThrowsOnMalformedTemplateUriString() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);

        for (String malformed : new String[] {"not-a-uri", "Task-T/only-one-segment", "Task-T/../v1"}) {
            IllegalArgumentException taskEx = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.generateTaskPromptFromText("Please analyze Site A.", malformed));
            assertTrue(taskEx.getMessage().contains("Unparseable template URI"), "message was: " + taskEx.getMessage());
            IllegalArgumentException authEx = assertThrows(
                    IllegalArgumentException.class, () -> client.generateAuthPromptFromText("Authorize access.", malformed));
            assertTrue(authEx.getMessage().contains("Unparseable template URI"), "message was: " + authEx.getMessage());
            IllegalArgumentException notifEx = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.generateNotificationPromptFromText("Report finished.", malformed));
            assertTrue(notifEx.getMessage().contains("Unparseable template URI"), "message was: " + notifEx.getMessage());
        }
    }

    @Test
    void fromTextThrowsOnBlankTemplateUriString() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);

        IllegalArgumentException taskEx = assertThrows(
                IllegalArgumentException.class, () -> client.generateTaskPromptFromText("Please analyze Site A.", "  "));
        assertTrue(taskEx.getMessage().contains("Unparseable template URI"), "message was: " + taskEx.getMessage());
    }

    @Test
    void getPromptThrowsOnMalformedAndBlankTemplateUriString() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);

        IllegalArgumentException malformedEx =
                assertThrows(IllegalArgumentException.class, () -> client.getPrompt("not-a-uri"));
        assertTrue(malformedEx.getMessage().contains("Unparseable template URI"), "message was: " + malformedEx.getMessage());
        IllegalArgumentException blankEx = assertThrows(IllegalArgumentException.class, () -> client.getPrompt("  "));
        assertTrue(blankEx.getMessage().contains("Unparseable template URI"), "message was: " + blankEx.getMessage());
    }

    @Test
    void fromTextRejectsOverLimitTextWhenConfiguredWithSmallLimit() throws IOException {
        Path envFile = writeMinimalClientEnvWithInputLimit(TEST_MOCK_PROVIDER, 100);
        A2ATClient client = new A2ATClient(envFile);
        String overLimitText = "a".repeat(101);

        PromptGenerationException taskEx = assertThrows(
                PromptGenerationException.class,
                () -> client.generateTaskPromptFromText(overLimitText, StandardTemplates.ENERGY_SAVING_URI));
        PromptGenerationException authEx = assertThrows(
                PromptGenerationException.class,
                () -> client.generateAuthPromptFromText(overLimitText, StandardTemplates.ENERGY_SAVING_URI));
        PromptGenerationException notificationEx = assertThrows(
                PromptGenerationException.class,
                () -> client.generateNotificationPromptFromText(overLimitText, StandardTemplates.ENERGY_SAVING_URI));

        assertEquals("input.text_too_long", taskEx.getCode());
        assertEquals("input.text_too_long", authEx.getCode());
        assertEquals("input.text_too_long", notificationEx.getCode());
        assertEquals(0, RecordingClient.REQUEST_COUNT.get(), "over-limit input must fail before any LLM call");
    }

    @Test
    void generateTaskPromptRejectsOverLimitStringInputWithoutLlmCall() throws IOException {
        Path envFile = writeMinimalClientEnvWithInputLimit(TEST_MOCK_PROVIDER, 100);
        A2ATClient client = new A2ATClient(envFile);

        PromptGenerationResult result = client.generateTaskPrompt("a".repeat(101));

        assertFalse(result.success());
        assertEquals("input.text_too_long", result.failure().code());
        assertEquals(0, RecordingClient.REQUEST_COUNT.get(), "over-limit input must fail before any LLM call");
    }

    private static Path writeMinimalClientEnvWithInputLimit(String provider, int maxTextChars) throws IOException {
        Path envFile = writeMinimalClientEnv(provider);
        Files.writeString(
                envFile, "A2AT_INPUT_TEXT_MAX_CHARS=" + maxTextChars + "\n", java.nio.file.StandardOpenOption.APPEND);
        return envFile;
    }

    private static Path writeMinimalClientEnvWithoutRequiredSlots(String provider) throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-client-env-no-required");
        Path promptRoot = tempDir.resolve("prompt_resources");
        Path scenarioPromptDir =
                promptRoot.resolve("prompts").resolve("scenario_recognition").resolve("zh-CN");
        Path slotPromptDir =
                promptRoot.resolve("prompts").resolve("slot_extraction").resolve("zh-CN");
        Path scenariosDir = promptRoot.resolve("scenarios").resolve("zh-CN");
        Path templatesDir = promptRoot
                .resolve("templates")
                .resolve("Task-T")
                .resolve("network-layer")
                .resolve("unconstrained")
                .resolve("v1")
                .resolve("zh-CN");
        Path slotsDir = promptRoot
                .resolve("slots")
                .resolve("Task-T")
                .resolve("network-layer")
                .resolve("unconstrained")
                .resolve("v1")
                .resolve("zh-CN");
        Files.createDirectories(scenarioPromptDir);
        Files.createDirectories(slotPromptDir);
        Files.createDirectories(scenariosDir);
        Files.createDirectories(templatesDir);
        Files.createDirectories(slotsDir);

        Files.writeString(
                scenariosDir.resolve("scenarios.json"),
                """
                {
                  "scenarios": [
                    {
                      "scenario_code": "unconstrained",
                      "scenario_name": "Unconstrained",
                      "description": "No required slots",
                      "example": "Analyze unconstrained"
                    }
                  ]
                }
                """);
        Files.writeString(templatesDir.resolve("template.md"), "Notes: {notes}\\nFault: {fault_description}");
        Files.writeString(
                slotsDir.resolve("slot.json"),
                """
                {
                  "required": [],
                  "properties": {
                    "notes": {
                      "type": "string"
                    },
                    "fault_description": {
                      "type": "string"
                    }
                  }
                }
                """);
        Files.writeString(scenarioPromptDir.resolve("system.md"), "Identify the best matching scenario.");
        Files.writeString(scenarioPromptDir.resolve("user.md"), "Choose from the provided scenario list.");
        Files.writeString(slotPromptDir.resolve("system.md"), "Extract slots from the input.");
        Files.writeString(slotPromptDir.resolve("user.md"), "Return slots as JSON.");

        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=local_file
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=prompt_resources
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(provider));
        return envFile;
    }

    private static Path writeMinimalLocalClientEnv() throws IOException {
        return writeMinimalClientEnv(TEST_MOCK_PROVIDER);
    }

    private static Path writeMinimalClasspathClientEnv() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-client-classpath-env");
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=classpath
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
                A2AT_LLM_PROVIDER=openai
A2AT_LLM_MODEL=example-model
A2AT_LLM_BASE_URL=https://llm.example.test/v1
A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """);
        return envFile;
    }

    private static Path writeMinimalClientEnv(String provider) throws IOException {
        return writeMinimalClientEnv(Files.createTempDirectory("a2at-client-env"), provider);
    }

    private static Path writeMinimalClientEnv(Path tempDir, String provider) throws IOException {
        Path promptRoot = tempDir.resolve("prompt_resources");
        Path scenarioPromptDir =
                promptRoot.resolve("prompts").resolve("scenario_recognition").resolve("zh-CN");
        Path slotPromptDir =
                promptRoot.resolve("prompts").resolve("slot_extraction").resolve("zh-CN");
        Path scenariosDir = promptRoot.resolve("scenarios").resolve("zh-CN");
        Path templatesDir = promptRoot
                .resolve("templates")
                .resolve("Task-T")
                .resolve("network-layer")
                .resolve("ran-energy-saving")
                .resolve("v1")
                .resolve("zh-CN");
        Path slotsDir = promptRoot
                .resolve("slots")
                .resolve("Task-T")
                .resolve("network-layer")
                .resolve("ran-energy-saving")
                .resolve("v1")
                .resolve("zh-CN");
        Files.createDirectories(scenarioPromptDir);
        Files.createDirectories(slotPromptDir);
        Files.createDirectories(scenariosDir);
        Files.createDirectories(templatesDir);
        Files.createDirectories(slotsDir);

        Files.writeString(
                scenariosDir.resolve("scenarios.json"),
                """
                {
                  "scenarios": [
                    {
                      "scenario_code": "ran-energy-saving",
                      "scenario_name": "Energy Saving",
                      "description": "Energy analysis",
                      "example": "Analyze site power"
                    }
                  ]
                }
                """);
        Files.writeString(templatesDir.resolve("template.md"), "Site: {site}\\nTarget: {target}");
        Files.writeString(
                slotsDir.resolve("slot.json"),
                """
                {
                  "required": ["site", "target"],
                  "properties": {
                    "site": {
                      "type": "string"
                    },
                    "target": {
                      "type": "string"
                    }
                  }
                }
                """);
        Files.writeString(scenarioPromptDir.resolve("system.md"), "Identify the best matching scenario.");
        Files.writeString(scenarioPromptDir.resolve("user.md"), "Choose from the provided scenario list.");
        Files.writeString(slotPromptDir.resolve("system.md"), "Extract slots from the input.");
        Files.writeString(slotPromptDir.resolve("user.md"), "Return slots as JSON.");

        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=local_file
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=prompt_resources
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(provider));
        return envFile;
    }

    private static Path writeMinimalMultiScenarioClientEnv() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-client-multi-scenario");
        Path promptRoot = tempDir.resolve("prompt_resources");
        Path scenarioPromptDir =
                promptRoot.resolve("prompts").resolve("scenario_recognition").resolve("zh-CN");
        Path slotPromptDir =
                promptRoot.resolve("prompts").resolve("slot_extraction").resolve("zh-CN");
        Path scenariosDir = promptRoot.resolve("scenarios").resolve("zh-CN");
        Path templatesDir = promptRoot
                .resolve("templates")
                .resolve("Task-T")
                .resolve("network-layer")
                .resolve("ran-energy-saving")
                .resolve("v1")
                .resolve("zh-CN");
        Path slotsDir = promptRoot
                .resolve("slots")
                .resolve("Task-T")
                .resolve("network-layer")
                .resolve("ran-energy-saving")
                .resolve("v1")
                .resolve("zh-CN");
        Files.createDirectories(scenarioPromptDir);
        Files.createDirectories(slotPromptDir);
        Files.createDirectories(scenariosDir);
        Files.createDirectories(templatesDir);
        Files.createDirectories(slotsDir);

        Files.writeString(
                scenariosDir.resolve("scenarios.json"),
                """
                {
                  "scenarios": [
                    {
                      "scenario_code": "ran-energy-saving",
                      "scenario_name": "Energy Saving",
                      "description": "Energy analysis",
                      "example": "Analyze site power"
                    },
                    {
                      "scenario_code": "data-report",
                      "scenario_name": "Data Report",
                      "description": "Report generation",
                      "example": "Generate report"
                    }
                  ]
                }
                """);
        Files.writeString(templatesDir.resolve("template.md"), "Site: {site}\\nTarget: {target}");
        Files.writeString(
                slotsDir.resolve("slot.json"),
                """
                {
                  "required": ["site", "target"],
                  "properties": {
                    "site": {
                      "type": "string"
                    },
                    "target": {
                      "type": "string"
                    }
                  }
                }
                """);
        Files.writeString(scenarioPromptDir.resolve("system.md"), "Identify the best matching scenario.");
        Files.writeString(scenarioPromptDir.resolve("user.md"), "Choose from the provided scenario list.");
        Files.writeString(slotPromptDir.resolve("system.md"), "Extract slots from the input.");
        Files.writeString(slotPromptDir.resolve("user.md"), "Return slots as JSON.");

        Path envFile = tempDir.resolve("client.env");
        Files.writeString(
                envFile,
                """
                A2AT_LANGUAGE=zh-CN
                A2AT_PROMPT_SOURCE_TYPE=local_file
                A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=prompt_resources
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """
                        .formatted(TEST_MOCK_PROVIDER));
        return envFile;
    }

    public static final class RecordingClient implements LLMClient {

        public static final Map<String, String> SLOT_OVERRIDES = new ConcurrentHashMap<>();
        public static final AtomicInteger REQUEST_COUNT = new AtomicInteger(0);

        private final LLMClientConfig config;

        public RecordingClient(LLMClientConfig config) {
            this.config = config;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            REQUEST_COUNT.incrementAndGet();
            return new LLMResponse(buildResponse(jsonSchema), config.model(), Map.of(), Map.of());
        }

        private static String buildResponse(Map<String, Object> jsonSchema) {
            Object slotNames = jsonSchema.get("slotNames");
            if (slotNames instanceof List<?> names) {
                StringBuilder slots = new StringBuilder("{");
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) {
                        slots.append(",");
                    }
                    String name = String.valueOf(names.get(i));
                    String value = SLOT_OVERRIDES.getOrDefault(name, "placeholder");
                    slots.append("\"")
                            .append(name)
                            .append("\":\"")
                            .append(value)
                            .append("\"");
                }
                slots.append("}");
                return "{\"slots\":" + slots + ",\"slot_errors\":[]}";
            }
            return "{\"matched\":true,\"scenario_code\":\"ran-energy-saving\",\"error_message\":null}";
        }
    }
}
