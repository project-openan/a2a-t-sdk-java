package net.openan.a2at.sdk.client.prompt.assembly;

import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRole;
import net.openan.a2at.sdk.prompt.analysis.impl.DefaultStructuredPromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.LlmScenarioRecognizer;
import net.openan.a2at.sdk.prompt.analysis.impl.PromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.ScenarioRecognizer;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateQueryService;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.TaskPromptRenderer;
import org.jspecify.annotations.Nullable;

/**
 * Default builder that assembles one high-level A2AT client runtime from unified config.
 *
 * @since 2026-06
 */
public final class DefaultA2ATClientBuilder {

    private static final String SCENARIO_RECOGNITION_PROMPT = "scenario_recognition";

    private static final String SLOT_EXTRACTION_PROMPT = "slot_extraction";

    private A2ATConfig config;

    private Path envPath;

    private LLMClient llmClient;

    /**
     * Creates one new builder instance.
     *
     * @return empty client builder
     */
    public static DefaultA2ATClientBuilder builder() {
        return new DefaultA2ATClientBuilder();
    }

    /**
     * Configures the unified SDK config consumed by the high-level client facade.
     *
     * @param config unified SDK config
     * @return current builder
     */
    public DefaultA2ATClientBuilder config(A2ATConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Configures the `.env` file path used to assemble downstream facades.
     *
     * @param envPath caller-supplied `.env` path
     * @return current builder
     */
    public DefaultA2ATClientBuilder envPath(Path envPath) {
        this.envPath = envPath;
        return this;
    }

    /**
     * Injects an explicit LLM client that fully replaces the factory default.
     *
     * <p>The injection point exists for testability and custom LLM assemblies: when set, every orchestrator built by
     * this builder reuses the given client and no client is created from the `.env` LLM config. When unset, the builder
     * keeps creating its default client and the behavior is unchanged.
     *
     * @param llmClient LLM client to inject; {@code null} keeps the factory default built from the `.env` LLM config
     * @return current builder
     * @since 2026-08
     */
    public DefaultA2ATClientBuilder llmClient(@Nullable LLMClient llmClient) {
        this.llmClient = llmClient;
        return this;
    }

    /**
     * Builds the default prompt-generation orchestrator from the configured unified SDK config.
     *
     * @return assembled prompt-generation orchestrator
     */
    public ClientPromptGenerationOrchestrator buildPromptGenerationOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        require(envPath, "Unified SDK env path must be configured.");
        requireSupportedConfig();

        PromptResourceAccess resources = PromptResourceAccess.create(config.prompt());
        List<ScenarioDefinition> scenarios =
                resources.loadScenarios(config.prompt().language());

        PromptSlotSchemaLoader slotSchemaLoader = resources.slotSchemaLoader();
        PromptTemplateTextLoader templateLoader = resources.templateLoader();
        String language = config.prompt().language();

        LLMClient client = llmClient();

        String scenarioSystemPrompt = resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "system.md");
        String scenarioUserPrompt = resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "user.md");
        String slotSystemPrompt = resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "system.md");
        String slotUserPrompt = resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "user.md");

        ScenarioRecognizer scenarioRecognizer =
                new SingleScenarioAwareRecognizer(scenarios, new LlmScenarioRecognizer(client)::recognize);
        PromptSlotValueExtractor slotValueExtractor = new DefaultStructuredPromptSlotValueExtractor(
                client, slotSchemaLoader, slotSystemPrompt, slotUserPrompt);

        return ClientPromptGenerationOrchestratorBuilder.builder()
                .llmClient(client)
                .scenarios(scenarios)
                .language(language)
                .scenarioSystemPrompt(scenarioSystemPrompt)
                .scenarioUserPrompt(scenarioUserPrompt)
                .slotSystemPrompt(slotSystemPrompt)
                .slotUserPrompt(slotUserPrompt)
                .scenarioRecognizer(scenarioRecognizer)
                .templateLoader(templateLoader)
                .slotSchemaLoader(slotSchemaLoader)
                .slotValueExtractor(slotValueExtractor)
                .renderer(new TaskPromptRenderer())
                .maxTextChars(config.inputLimits().maxTextChars())
                .build();
    }

    /**
     * Builds the default negotiation orchestrator from the configured unified SDK config.
     *
     * @return assembled negotiation orchestrator
     */
    public RoleBoundNegotiationOrchestrator buildNegotiationOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        return new RoleBoundNegotiationOrchestrator(
                NegotiationHandler.builder()
                        .store(new InMemoryNegotiationStore())
                        .build(),
                NegotiationRole.CLIENT);
    }

    /**
     * Builds the default negotiation content-layer orchestrator from the configured unified SDK config.
     *
     * <p>The wiring is shared with the server side through
     * {@link NegotiationContentService#buildOrchestrator(A2ATConfig, LLMClient)}: the message language and the local
     * template root come from the prompt runtime config, the retry attempt limit comes from the LLM config.
     *
     * @return assembled negotiation generation orchestrator
     */
    public NegotiationGenerationOrchestrator buildNegotiationGenerationOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        require(envPath, "Unified SDK env path must be configured.");
        return NegotiationContentService.buildOrchestrator(config, llmClient());
    }

    /**
     * Builds the generic template query service from the configured unified SDK config.
     *
     * <p>The service answers the extension-agnostic template queries: the message language and the local template root
     * come from the prompt runtime config, exactly like the negotiation generation orchestrator wiring.
     *
     * @return assembled template query service
     */
    public TemplateQueryService buildTemplateQueryService() {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        return new TemplateQueryService(
                config.prompt().language(),
                config.prompt().sourceType(),
                config.prompt().localRootDir());
    }

    private void requireSupportedConfig() {
        if (!PromptResourceAccess.CLASSPATH_SOURCE_TYPE.equals(config.prompt().sourceType())
                && !PromptResourceAccess.LOCAL_FILE_SOURCE_TYPE.equals(
                        config.prompt().sourceType())) {
            throw new UnsupportedOperationException(
                    "Unsupported prompt source type: " + config.prompt().sourceType());
        }
        if (!LLMClientFactory.availableProviders().contains(config.llm().provider())) {
            throw new UnsupportedOperationException(
                    "Unsupported LLM provider: " + config.llm().provider());
        }
        if (!"in_memory".equals(config.negotiation().stateStoreType())) {
            throw new UnsupportedOperationException("Unsupported negotiation state store type: "
                    + config.negotiation().stateStoreType());
        }
    }

    private synchronized LLMClient llmClient() {
        if (llmClient == null) {
            llmClient = LLMClientFactory.create(config.llm().provider(), LLMClientConfig.from(config.llm()));
        }
        return llmClient;
    }

    private static void require(Object value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
    }

    private static final class SingleScenarioAwareRecognizer implements ScenarioRecognizer {

        private final List<ScenarioDefinition> scenarios;

        private final ScenarioRecognizer delegate;

        private SingleScenarioAwareRecognizer(List<ScenarioDefinition> scenarios, ScenarioRecognizer delegate) {
            this.scenarios = scenarios;
            this.delegate = delegate;
        }

        @Override
        public ScenarioRecognitionResult recognize(
                String normalizedInput, List<ScenarioDefinition> scenarios, String systemPrompt, String userPrompt) {
            if (this.scenarios.size() == 1) {
                return new ScenarioRecognitionResult(true, this.scenarios.get(0).scenarioCode(), null);
            }
            return delegate.recognize(normalizedInput, this.scenarios, systemPrompt, userPrompt);
        }
    }
}
