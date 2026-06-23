package net.openan.a2at.sdk.server.assembly;

import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.model.A2ATConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMConfigLoader;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.server.compliance.DefaultServerPromptComplianceOrchestrator;
import net.openan.a2at.sdk.server.metadata.LlmBackedPromptMetadataExtractor;
import net.openan.a2at.sdk.server.validation.LlmBackedPromptSemanticValidator;
import net.openan.a2at.sdk.server.metadata.TemplateMatchingPromptMetadataExtractor;
import net.openan.a2at.sdk.server.validation.TemplateRoundTripPromptSemanticValidator;
import net.openan.a2at.sdk.prompt.analysis.impl.DefaultStructuredPromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.ScenarioRecognizer;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptResourceAccess;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.server.model.PromptTemplateDefinition;

/**
 * Default builder that assembles one high-level A2AT server runtime from unified config.
 *
 * @since 2026-06
 */
public final class DefaultA2ATServerBuilder {

    private static final String LOCAL_RULE_PROVIDER = "local_rule";

    private static final String OPENAI_PROVIDER = "openai";

    private static final String SCENARIO_RECOGNITION_PROMPT = "scenario_recognition";

    private static final String SLOT_EXTRACTION_PROMPT = "slot_extraction";

    private static final String SEMANTIC_VALIDATION_PROMPT = "semantic_validation";

    private A2ATConfig config;

    private Path envPath;

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
     * Builds the default prompt-compliance orchestrator from the configured unified SDK config.
     *
     * @return assembled prompt-compliance orchestrator
     */
    public DefaultServerPromptComplianceOrchestrator buildPromptComplianceOrchestrator() {
        require(config, "Unified SDK config must be configured.");
        require(envPath, "Unified SDK env path must be configured.");
        requireSupportedConfig();

        PromptResourceAccess resources = PromptResourceAccess.create(config.prompt());
        if (LOCAL_RULE_PROVIDER.equals(config.llm().provider())) {
            return buildLocalRulePromptComplianceOrchestrator(resources);
        }

        return buildLlmBackedPromptComplianceOrchestrator(resources);
    }

    private DefaultServerPromptComplianceOrchestrator buildLocalRulePromptComplianceOrchestrator(
            PromptResourceAccess resources) {
        String language = config.prompt().language();
        List<PromptTemplateDefinition> templates;
        if (resources.classpath()) {
            List<ScenarioDefinition> scenarios = resources.loadScenarios(language);
            templates = new ClasspathServerPromptTemplateLoader(resources.classpathResourceLoader())
                    .loadAll(scenarios, language);
        } else {
            templates = new LocalFileServerPromptTemplateLoader(resources.localRootDir()).loadAll(language);
        }
        return new DefaultServerPromptComplianceOrchestrator(
                new TemplateMatchingPromptMetadataExtractor(templates),
                new TemplateRoundTripPromptSemanticValidator());
    }

    private DefaultServerPromptComplianceOrchestrator buildLlmBackedPromptComplianceOrchestrator(
            PromptResourceAccess resources) {
        String language = config.prompt().language();
        List<ScenarioDefinition> scenarios = resources.loadScenarios(language);
        PromptTemplateTextLoader templateLoader = resources.templateLoader();
        PromptSlotSchemaLoader slotSchemaLoader = resources.slotSchemaLoader();
        LLMClient llmClient = createLlmClient();

        String scenarioSystemPrompt = resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "system.md");
        String scenarioUserPrompt = resources.loadPrompt(SCENARIO_RECOGNITION_PROMPT, language, "user.md");
        String slotSystemPrompt = resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "system.md");
        String slotUserPrompt = resources.loadPrompt(SLOT_EXTRACTION_PROMPT, language, "user.md");
        String semanticSystemPrompt = resources.loadPrompt(SEMANTIC_VALIDATION_PROMPT, language, "system.md");
        String semanticUserPrompt = resources.loadPrompt(SEMANTIC_VALIDATION_PROMPT, language, "user.md");

        return new DefaultServerPromptComplianceOrchestrator(
                new LlmBackedPromptMetadataExtractor(
                        new ScenarioRecognizer(llmClient),
                        scenarios,
                        language,
                        scenarioSystemPrompt,
                        scenarioUserPrompt,
                        templateLoader,
                        slotSchemaLoader,
                        new DefaultStructuredPromptSlotValueExtractor(
                                llmClient, slotSchemaLoader, slotSystemPrompt, slotUserPrompt)),
                new LlmBackedPromptSemanticValidator(
                        llmClient, slotSchemaLoader, semanticSystemPrompt, semanticUserPrompt));
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

    private static void require(Object value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
    }

    private void requireSupportedConfig() {
        if (!PromptResourceAccess.CLASSPATH_SOURCE_TYPE.equals(config.prompt().sourceType())
                && !PromptResourceAccess.LOCAL_FILE_SOURCE_TYPE.equals(config.prompt().sourceType())) {
            throw new UnsupportedOperationException(
                    "Unsupported prompt source type: " + config.prompt().sourceType());
        }
        if (!LOCAL_RULE_PROVIDER.equals(config.llm().provider())
                && !OPENAI_PROVIDER.equals(config.llm().provider())) {
            throw new UnsupportedOperationException("Unsupported LLM provider: " + config.llm().provider());
        }
        if (!"in_memory".equals(config.negotiation().stateStoreType())) {
            throw new UnsupportedOperationException(
                    "Unsupported negotiation state store type: " + config.negotiation().stateStoreType());
        }
    }

    private LLMClient createLlmClient() {
        LLMClientConfig loadedConfig = LLMConfigLoader.load(envPath);
        return LLMClientFactory.create(loadedConfig.provider(), loadedConfig);
    }

}
