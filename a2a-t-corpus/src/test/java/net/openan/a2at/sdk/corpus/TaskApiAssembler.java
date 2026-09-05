package net.openan.a2at.sdk.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.openan.a2at.sdk.client.prompt.assembly.DefaultA2ATClientBuilder;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.server.assembly.DefaultA2ATServerBuilder;

/**
 * Real-builder assembly of the three closed-loop task APIs (Q21): the corpus harness drives exactly the production
 * wiring the {@code A2ATClient} and {@code A2ATServer} facades drive internally, so a change of the production assembly
 * cannot drift past the corpus.
 *
 * <ul>
 *   <li>{@code generateTaskPromptFromText} and {@code generateTaskPromptFromDataWithSchema} run through the
 *       {@link ClientPromptGenerationOrchestrator} built by
 *       {@link DefaultA2ATClientBuilder#buildPromptGenerationOrchestrator()} — the same build method the client facade
 *       constructor calls;
 *   <li>{@code validateTaskPromptAndDataFilling} runs through the {@link ContentValidator} built by
 *       {@link DefaultA2ATServerBuilder#buildTaskContentValidator()} — the same build method the server facade
 *       constructor calls.
 * </ul>
 *
 * <p>Both builders are driven exactly like the facades drive them: a minimal classpath-source {@code .env} (written
 * once per language and retry limit into a temporary directory, following the facade test precedent) is loaded through
 * {@link A2ATConfig#load(Path)} plus {@link A2ATConfig#resolvePromptResourceLocalRootDir(A2ATConfig, Path)}, and the
 * {@link ScriptedNegotiationLlmClient} is injected through the builders' {@code llmClient(...)} seam — the same
 * instance feeds the client-side and the server-side components, so the {@code llmCalls} expectation counts the whole
 * closed loop. The facade constructors are locked to the {@code Path}-only signature by the API-surface guard tests, so
 * the builders are the direct route to the identical assembly.
 *
 * @since 2026-08
 */
final class TaskApiAssembler {

    private static final Map<String, Path> ENV_FILES = new ConcurrentHashMap<>();

    private final ClientPromptGenerationOrchestrator promptGeneration;

    private final ContentValidator taskValidator;

    /**
     * Assembles the task API wiring for one language.
     *
     * @param language language of the generated and validated task prompts, such as {@code zh-CN}
     * @param maxAttempts retry limit of the LLM steps, mirroring the builder default of 3
     * @param llmClient scripted LLM client injected at the same seam the facade builders inject their real client
     */
    TaskApiAssembler(String language, int maxAttempts, LLMClient llmClient) {
        this(minimalEnvFor(language, maxAttempts), llmClient);
    }

    /**
     * Assembles the task API wiring around an explicit {@code .env} file — the seam of the live family, whose harness
     * hands in the {@code LiveLlmEnvWriter} bridge (real test-endpoint values plus the explicit stability knobs and the
     * pipeline retry limit) instead of the scripted minimal env.
     *
     * @param envPath the {@code .env} file the config is loaded from, carrying the language and retry limit
     * @param llmClient LLM client injected at the same seam the facade builders inject their real client
     */
    TaskApiAssembler(Path envPath, LLMClient llmClient) {
        A2ATConfig config = A2ATConfig.resolvePromptResourceLocalRootDir(A2ATConfig.load(envPath), envPath);
        this.promptGeneration = DefaultA2ATClientBuilder.builder()
                .config(config)
                .envPath(envPath)
                .llmClient(llmClient)
                .buildPromptGenerationOrchestrator();
        this.taskValidator = DefaultA2ATServerBuilder.builder()
                .config(config)
                .envPath(envPath)
                .llmClient(llmClient)
                .buildTaskContentValidator();
    }

    /**
     * Generates a task prompt with metadata from natural-language input through the real client orchestrator, the
     * pipeline behind {@code A2ATClient.generateTaskPromptFromText}.
     *
     * @param text natural-language task input
     * @param templateUri template URI identifying the target task template
     * @return metadata content carrying the resolved template URI, rendered prompt text and Task-T extension URI
     */
    MetadataContent generateTaskPromptFromText(String text, TemplateUri templateUri) {
        return promptGeneration.generateTaskPromptFromText(text, templateUri);
    }

    /**
     * Generates a task prompt with metadata from structured input and a data schema through the real client
     * orchestrator, the pipeline behind {@code A2ATClient.generateTaskPromptFromDataWithSchema}.
     *
     * @param data structured task input as a string-to-object map
     * @param schema data schema map describing the meaning of each input field
     * @param templateUri template URI identifying the target task template
     * @return metadata content carrying the resolved template URI, rendered prompt text and Task-T extension URI
     */
    MetadataContent generateTaskPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri) {
        return promptGeneration.generateTaskPromptFromDataWithSchema(data, schema, templateUri);
    }

    /**
     * Validates a task prompt and extracts its filled parameters through the real server-side task content validator,
     * the pipeline behind {@code A2ATServer.validateTaskPromptAndDataFilling}.
     *
     * <p>A schema slot the prompt misses surfaces as a null-valued entry of the returned parameter data — that set of
     * null-valued keys is the missing-parameter set the negotiation loop then fills.
     *
     * @param prompt rendered task prompt text to validate
     * @param schema caller-provided parameter JSON schema describing the parameters to extract
     * @param templateUri template URI declaring the expected task template
     * @return filled parameter data carrying the extracted parameters; null values mark missing parameters
     */
    FilledParamData validateTaskPromptAndDataFilling(
            String prompt, Map<String, Object> schema, TemplateUri templateUri) {
        return taskValidator.validate(prompt, schema, templateUri);
    }

    // ------------------------------------------------------------------ minimal facade env

    /**
     * Writes (once per language and retry limit) the minimal classpath-source {@code .env} the facades would be handed,
     * following the {@code A2ATClientTest} minimal-env precedent; the LLM entries are inert because the scripted client
     * is injected at the builders' LLM seam.
     *
     * @param language language of the corpus case
     * @param maxAttempts retry limit of the LLM steps from the case's LLM script
     * @return path of the written {@code .env} file
     */
    private static Path minimalEnvFor(String language, int maxAttempts) {
        return ENV_FILES.computeIfAbsent(language + "/" + maxAttempts, key -> {
            try {
                Path envFile = Files.createTempDirectory("a2at-corpus-task-env").resolve("client.env");
                Files.writeString(
                        envFile,
                        ("A2AT_LANGUAGE=%s%n"
                                        + "A2AT_PROMPT_SOURCE_TYPE=classpath%n"
                                        + "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=%n"
                                        + "A2AT_LLM_PROVIDER=openai%n"
                                        + "A2AT_LLM_MODEL=scripted-model%n"
                                        + "A2AT_LLM_BASE_URL=https://llm.example.test/v1%n"
                                        + "A2AT_LLM_API_KEY=corpus-scripted%n"
                                        + "A2AT_LLM_MAX_ATTEMPTS=%d%n"
                                        + "A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory%n")
                                .formatted(language, maxAttempts),
                        StandardCharsets.UTF_8);
                return envFile;
            } catch (IOException exception) {
                throw new UncheckedIOException(
                        "Failed to write the minimal corpus .env for language " + language, exception);
            }
        });
    }
}
