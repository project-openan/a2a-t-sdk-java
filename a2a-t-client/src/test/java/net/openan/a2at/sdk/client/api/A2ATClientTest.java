package net.openan.a2at.sdk.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class A2ATClientTest {

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
                .count();

        assertEquals(0, staticMethodCount);
    }

    @Test
    void pathBasedConstructorLoadsLocalPromptResources() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);

        PromptGenerationResult result =
                client.generateTaskPrompt(Map.of("site", "Site A", "target", "Reduce power by 10%"));

        assertTrue(result.success());
        assertEquals("Site: Site A\\nTarget: Reduce power by 10%", result.promptText());
    }

    @Test
    void pathBasedConstructorAcceptsRelativeEnvPath() throws IOException {
        Path targetDir = Files.createDirectories(Path.of("target"));
        Path tempDir = Files.createTempDirectory(targetDir, "a2at-client-relative-env");
        writeMinimalClientEnv(tempDir, "local_rule");
        Path relativeEnvPath =
                targetDir.getFileName().resolve(tempDir.getFileName()).resolve("client.env");

        A2ATClient client = new A2ATClient(relativeEnvPath);
        PromptGenerationResult result =
                client.generateTaskPrompt(Map.of("site", "Site A", "target", "Reduce power by 10%"));

        assertTrue(result.success());
        assertEquals("Site: Site A\\nTarget: Reduce power by 10%", result.promptText());
    }

    @Test
    void pathBasedConstructorLoadsClasspathPromptResources() throws IOException {
        Path envFile = writeMinimalClasspathClientEnv();

        A2ATClient client = new A2ATClient(envFile);

        assertNotNull(client);
    }

    @Test
    void pathBasedConstructorAcceptsOpenAiProvider() throws IOException {
        Path envFile = writeMinimalClientEnv("openai");

        A2ATClient client = new A2ATClient(envFile);
        PromptGenerationResult result =
                client.generateTaskPrompt(Map.of("site", "Site A", "target", "Reduce power by 10%"));

        assertTrue(result.success());
        assertEquals("Site: Site A\\nTarget: Reduce power by 10%", result.promptText());
    }

    @Test
    void pathBasedConstructorBuildsDefaultNegotiationRuntime() throws IOException {
        Path envFile = writeMinimalLocalClientEnv();
        A2ATClient client = new A2ATClient(envFile);

        Map<String, Object> startResult = client.startNegotiation(
                NegotiationType.CLARIFICATION, "Please clarify the target.", Map.of("site", "A"));
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>)
                startResult.get(net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler.NEGOTIATION_CONTEXT_KEY);

        Map<String, Object> continueResult = client.continueNegotiation(
                new NegotiationContext(
                        NegotiationType.CLARIFICATION,
                        String.valueOf(context.get("negotiationId")),
                        1,
                        NegotiationStatus.IN_PROGRESS),
                NegotiationStatus.IN_PROGRESS,
                "Site A");

        assertEquals(
                "Please clarify the target.",
                startResult.get(net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler.NEGOTIATION_TEXT_KEY));
        assertEquals(
                "Site A",
                continueResult.get(net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler.NEGOTIATION_TEXT_KEY));
    }

    @Test
    void pathBasedConstructorSupportsNonEnergySavingLocalScenarioCatalog() throws IOException {
        Path tempDir = Files.createTempDirectory("a2at-client-private-line");
        Path promptRoot = tempDir.resolve("prompt_resources");
        Path scenariosDir = promptRoot.resolve("scenarios").resolve("zh-CN");
        Path templatesDir = promptRoot
                .resolve("templates")
                .resolve("private_line_complaint")
                .resolve("zh-CN");
        Path slotsDir =
                promptRoot.resolve("slots").resolve("private_line_complaint").resolve("zh-CN");
        Files.createDirectories(scenariosDir);
        Files.createDirectories(templatesDir);
        Files.createDirectories(slotsDir);

        Files.writeString(
                scenariosDir.resolve("scenarios.json"),
                """
                {
                  "scenarios": [
                    {
                      "scenario_code": "private_line_complaint",
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
                A2AT_LLM_PROVIDER=local_rule
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """);

        A2ATClient client = new A2ATClient(envFile);
        PromptGenerationResult result = client.generateTaskPrompt(Map.of("line_id", "line-1", "fault_id", "fault-9"));

        assertTrue(result.success());
        assertEquals("Line: line-1\\nFault: fault-9", result.promptText());
    }

    private static Path writeMinimalLocalClientEnv() throws IOException {
        return writeMinimalClientEnv("local_rule");
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
                A2AT_LLM_PROVIDER=local_rule
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
        Path templatesDir =
                promptRoot.resolve("templates").resolve("energy_saving").resolve("zh-CN");
        Path slotsDir = promptRoot.resolve("slots").resolve("energy_saving").resolve("zh-CN");
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
                      "scenario_code": "energy_saving",
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
}
