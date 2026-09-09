package net.openan.a2at.sdk.corpus.engine.registry;

import java.util.List;
import net.openan.a2at.sdk.client.prompt.assembly.DefaultA2ATClientBuilder;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.core.model.LlmConfig;
import net.openan.a2at.sdk.core.model.NegotiationConfig;
import net.openan.a2at.sdk.core.model.PromptComplianceConfig;
import net.openan.a2at.sdk.core.model.PromptRuntimeConfig;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import net.openan.a2at.sdk.corpus.engine.config.CorpusEnvConfig;
import net.openan.a2at.sdk.corpus.engine.llm.RecordingMeteredLlmClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.providers.OpenAIClient;
import net.openan.a2at.sdk.server.assembly.DefaultA2ATServerBuilder;

/**
 * Assembles the SDK runtime the corpus exercises against.
 *
 * <p>Every component is produced by the production builders ({@code DefaultA2ATClientBuilder} /
 * {@code DefaultA2ATServerBuilder}); the sole test seam is the {@link RecordingMeteredLlmClient} injected through the
 * builder {@code llmClient(...)} injection point. The unified {@link A2ATConfig} is constructed directly from the
 * corpus test configuration (classpath prompt resources, fixed language), so the module never touches production
 * {@code A2AT_LLM_*} environment values.
 */
public final class SdkRuntimeAssembler {

    /** Fully assembled runtime with the single shared recording LLM client. */
    public record Runtime(
            RecordingMeteredLlmClient recorder,
            ClientPromptGenerationOrchestrator clientGeneration,
            ContentValidator taskValidator) {}

    private SdkRuntimeAssembler() {}

    public static Runtime taskRuntime() {
        CorpusEnvConfig env = CorpusEnvConfig.load();

        LLMClientConfig llmClientConfig = new LLMClientConfig(
                env.provider(),
                env.model(),
                env.apiKey(),
                env.baseUrl(),
                10,
                env.maxTokens(),
                env.temperature(),
                env.timeoutSeconds(),
                300,
                100,
                false,
                null);
        RecordingMeteredLlmClient recorder =
                new RecordingMeteredLlmClient(new OpenAIClient(llmClientConfig), env.model());

        LlmConfig llmConfig = new LlmConfig(
                env.provider(),
                env.model(),
                env.apiKey(),
                env.baseUrl(),
                10,
                env.maxTokens(),
                env.temperature(),
                env.timeoutSeconds(),
                300,
                100,
                false,
                null,
                env.maxAttempts(),
                List.of());
        A2ATConfig config = new A2ATConfig(
                new PromptRuntimeConfig("zh-CN", PromptRuntimeConfig.SOURCE_TYPE_CLASSPATH, null),
                llmConfig,
                new InputLimitConfig(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS),
                new NegotiationConfig("in_memory"),
                new PromptComplianceConfig(false));

        ClientPromptGenerationOrchestrator clientGeneration = DefaultA2ATClientBuilder.builder()
                .config(config)
                .envPath(env.envPath())
                .llmClient(recorder)
                .buildPromptGenerationOrchestrator();
        ContentValidator taskValidator = DefaultA2ATServerBuilder.builder()
                .config(config)
                .envPath(env.envPath())
                .llmClient(recorder)
                .buildTaskContentValidator();

        return new Runtime(recorder, clientGeneration, taskValidator);
    }
}