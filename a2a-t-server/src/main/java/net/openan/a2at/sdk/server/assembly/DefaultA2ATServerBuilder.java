package net.openan.a2at.sdk.server.assembly;

import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.prompt.analysis.impl.DefaultStructuredPromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.LlmScenarioRecognizer;
import net.openan.a2at.sdk.prompt.resources.catalog.TemplateQueryService;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.validation.DefaultContentValidator;
import net.openan.a2at.sdk.server.compliance.DefaultServerPromptComplianceOrchestrator;
import net.openan.a2at.sdk.server.metadata.LlmBackedPromptMetadataExtractor;
import net.openan.a2at.sdk.server.validation.InputLimitedContentValidator;
import net.openan.a2at.sdk.server.validation.LlmBackedPromptSemanticValidator;
import org.jspecify.annotations.Nullable;

/**
 * Default builder that assembles one high-level A2AT server runtime from unified config.
 *
 * @since 2026-06
 */
public final class DefaultA2ATServerBuilder {

    private static final String SCENARIO_RECOGNITION_PROMPT = "scenario_recognition";

    private static final String SLOT_EXTRACTION_PROMPT = "slot_extraction";

    private static final String SEMANTIC_VALIDATION_PROMPT = "semantic_validation";

    private A2ATConfig config;

    private Path envPath;

    private LLMClient llmClient;

    private PromptResourceAccess promptResourceAccess;

    private DefaultServerPromptComplianceOrchestrator promptComplianceOrchestrator;

    /**
     * Creates one new builder instance.
     *
     * @return empty server builder
     */
    public static DefaultA2ATServerBuilder builder() {
        return new DefaultA2ATServerBuilder();
    }

    /**
     * Configures the unified SDK config consumed by the high-level server facade.
     *
     * @param config unified SDK config
     * @return current builder
     */
    public DefaultA2ATServerBuilder config(A2ATConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Configures the `.env` file path used to assemble downstream facades.
     *
     * @param envPath caller-supplied `.env` path
     * @return current builder
     */
    public DefaultA2ATServerBuilder envPath(Path envPath) {
        this.envPath = envPath;
        return this;
    }

    /**
     * Injects an explicit LLM client that fully replaces the factory default.
     *
     * <p>The injection point exists for testability and custom LLM assemblies: when set, every orchestrator and
     * validator built by this builder reuses the given client and no client is created from the `.env` LLM config. When
     * unset, the builder keeps creating its default client and the behavior is unchanged.
     *
     * @param llmClient LLM client to inject; {@code null} keeps the factory default built from the `.env` LLM config
     * @return current builder
     * @since 2026-08
     */
    public DefaultA2ATServerBuilder llmClient(@Nullable LLMClient llmClient) {
        this.llmClient = llmClient;
        return this;
    }

    /**
     * Builds the default prompt-compliance orchestrator from the configured unified SDK config.
     *
     * @return assembled prompt-compliance orchestrator
     */
    public synchronized DefaultServerPromptComplianceOrchestrator buildPromptComplianceOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        require(envPath, "Unified SDK env path must be configured.");
        requireSupportedConfig();
        if (promptComplianceOrchestrator != null) {
            return promptComplianceOrchestrator;
        }

        PromptResourceAccess resources = promptResourceAccess();
        String language = config.prompt().language();
        List<ScenarioDefinition> scenarios = resources.loadScenarios(language);
        PromptTemplateTextLoader templateLoader = resources.templateLoader();
        PromptSlotSchemaLoader slotSchemaLoader = resources.slotSchemaLoader();
        LLMClient client = llmClient();

        String scenarioSystemPrompt = resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "system.md");
        String scenarioUserPrompt = resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "user.md");
        String slotSystemPrompt = resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "system.md");
        String slotUserPrompt = resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "user.md");
        String semanticSystemPrompt = resources.loadPrompt(SEMANTIC_VALIDATION_PROMPT, language, "system.md");
        String semanticUserPrompt = resources.loadPrompt(SEMANTIC_VALIDATION_PROMPT, language, "user.md");

        promptComplianceOrchestrator = new DefaultServerPromptComplianceOrchestrator(
                new LlmBackedPromptMetadataExtractor(
                        new LlmScenarioRecognizer(client),
                        scenarios,
                        language,
                        scenarioSystemPrompt,
                        scenarioUserPrompt,
                        templateLoader,
                        slotSchemaLoader,
                        new DefaultStructuredPromptSlotValueExtractor(
                                client, slotSchemaLoader, slotSystemPrompt, slotUserPrompt)),
                new LlmBackedPromptSemanticValidator(
                        client, slotSchemaLoader, semanticSystemPrompt, semanticUserPrompt),
                config.inputLimits().maxTextChars(),
                language);
        return promptComplianceOrchestrator;
    }

    /**
     * Builds the default negotiation orchestrator from the configured unified SDK config.
     *
     * @return assembled negotiation orchestrator
     */
    public RoleBoundNegotiationOrchestrator buildNegotiationOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        return new ServerNegotiationOrchestratorBuilder()
                .promptComplianceOrchestrator(buildPromptComplianceOrchestrator())
                .build();
    }

    /**
     * Builds the default negotiation content-layer orchestrator from the configured unified SDK config.
     *
     * <p>The wiring is shared with the client side through
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

    /**
     * Builds the task content validator from the configured unified SDK config.
     *
     * <p>The validator enforces the {@code Task-T} extension prefix and reuses the configured language, LLM retry
     * attempt limit and LLM client; the content_validation prompt resources are loaded from the classpath.
     *
     * @return assembled task content validator
     */
    public ContentValidator buildTaskContentValidator() {
        return buildContentValidator(StandardTemplates.TASK_EXTENSION_NAME);
    }

    /**
     * Builds the notification content validator from the configured unified SDK config.
     *
     * <p>The validator enforces the {@code Notification-T} extension prefix and reuses the configured language, LLM
     * retry attempt limit and LLM client; the content_validation prompt resources are loaded from the classpath.
     *
     * @return assembled notification content validator
     */
    public ContentValidator buildNotificationContentValidator() {
        return buildContentValidator(StandardTemplates.NOTIFICATION_EXTENSION_NAME);
    }

    /**
     * Builds the authorization content validator from the configured unified SDK config.
     *
     * <p>The validator enforces the {@code Authorization-T} extension prefix and reuses the configured language, LLM
     * retry attempt limit and LLM client; the content_validation prompt resources are loaded from the classpath.
     *
     * @return assembled authorization content validator
     */
    public ContentValidator buildAuthContentValidator() {
        return buildContentValidator(StandardTemplates.AUTHORIZATION_EXTENSION_NAME);
    }

    private ContentValidator buildContentValidator(String extensionName) {
        require(config, "Unified SDK config must be configured.");
        requireSupportedConfig();
        require(envPath, "Unified SDK env path must be configured.");
        return new InputLimitedContentValidator(
                new DefaultContentValidator(
                        extensionName,
                        config.prompt().language(),
                        config.llm().maxAttempts(),
                        llmClient(),
                        promptResourceAccess().templateLoader()),
                config.inputLimits().maxTextChars(),
                config.prompt().language());
    }

    private static void require(Object value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
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

    private synchronized PromptResourceAccess promptResourceAccess() {
        if (promptResourceAccess == null) {
            promptResourceAccess = PromptResourceAccess.create(config.prompt());
        }
        return promptResourceAccess;
    }
}
