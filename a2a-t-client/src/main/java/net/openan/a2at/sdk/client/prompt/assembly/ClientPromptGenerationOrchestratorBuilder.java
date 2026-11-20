/*
 * Copyright (c) 2026 Huawei Technologies Co., Ltd.
 * All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package net.openan.a2at.sdk.client.prompt.assembly;

import java.util.List;
import net.openan.a2at.sdk.client.prompt.orchestration.DefaultClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.model.InputLimitConfig;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.analysis.impl.DefaultStructuredPromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.PromptSlotValueExtractor;
import net.openan.a2at.sdk.prompt.analysis.impl.LlmScenarioRecognizer;
import net.openan.a2at.sdk.prompt.analysis.impl.ScenarioRecognizer;
import net.openan.a2at.sdk.prompt.resources.loader.ClasspathPromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.ClasspathPromptTemplateLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.prompt.taskrendering.TaskPromptRenderer;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;

/**
 * Builder for the default client prompt generation orchestration chain.
 *
 * @since 2026-06
 */
public final class ClientPromptGenerationOrchestratorBuilder {

    private ClientPromptGenerationOrchestratorBuilder() {}

    private LLMClient llmClient;

    private List<ScenarioDefinition> scenarios;

    private String language;

    private String scenarioSystemPrompt;

    private String scenarioUserPrompt;

    private String slotSystemPrompt;

    private String slotUserPrompt;

    private ScenarioRecognizer scenarioRecognizer;

    private PromptTemplateTextLoader templateLoader;

    private PromptSlotSchemaLoader slotSchemaLoader;

    private PromptSlotValueExtractor slotValueExtractor;

    private TaskPromptRenderer renderer;

    private Integer maxTextChars;

    private ClasspathPromptResourceLoader resourceLoader;

    /**
     * Creates a new builder instance.
     *
     * @return empty builder for assembling the default client orchestration chain
     */
    public static ClientPromptGenerationOrchestratorBuilder builder() {
        return new ClientPromptGenerationOrchestratorBuilder();
    }

    /**
     * Configures the LLM client used by the default chain.
     *
     * @param llmClient LLM client
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder llmClient(LLMClient llmClient) {
        this.llmClient = llmClient;
        return this;
    }

    /**
     * Configures the scenario definitions visible to scenario recognition.
     *
     * @param scenarios supported scenario definitions
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder scenarios(List<ScenarioDefinition> scenarios) {
        this.scenarios = scenarios;
        return this;
    }

    /**
     * Configures the resource language to resolve templates and schemas from.
     *
     * @param language locale identifier such as {@code zh-CN}
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder language(String language) {
        this.language = language;
        return this;
    }

    /**
     * Configures the system prompt for scenario recognition.
     *
     * @param scenarioSystemPrompt scenario-recognition system prompt
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder scenarioSystemPrompt(String scenarioSystemPrompt) {
        this.scenarioSystemPrompt = scenarioSystemPrompt;
        return this;
    }

    /**
     * Configures the user prompt for scenario recognition.
     *
     * @param scenarioUserPrompt scenario-recognition user prompt
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder scenarioUserPrompt(String scenarioUserPrompt) {
        this.scenarioUserPrompt = scenarioUserPrompt;
        return this;
    }

    /**
     * Configures the system prompt for slot extraction.
     *
     * @param slotSystemPrompt slot-extraction system prompt
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder slotSystemPrompt(String slotSystemPrompt) {
        this.slotSystemPrompt = slotSystemPrompt;
        return this;
    }

    /**
     * Configures the user prompt for slot extraction.
     *
     * @param slotUserPrompt slot-extraction user prompt
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder slotUserPrompt(String slotUserPrompt) {
        this.slotUserPrompt = slotUserPrompt;
        return this;
    }

    /**
     * Overrides the scenario recognizer used by the default chain.
     *
     * @param scenarioRecognizer scenario recognizer implementation
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder scenarioRecognizer(
            ScenarioRecognizer scenarioRecognizer) {
        this.scenarioRecognizer = scenarioRecognizer;
        return this;
    }

    /**
     * Overrides the template loader used by the default chain.
     *
     * @param templateLoader template loader implementation
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder templateLoader(PromptTemplateTextLoader templateLoader) {
        this.templateLoader = templateLoader;
        return this;
    }

    /**
     * Overrides the slot schema loader used by the default chain.
     *
     * @param slotSchemaLoader slot schema loader implementation
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder slotSchemaLoader(PromptSlotSchemaLoader slotSchemaLoader) {
        this.slotSchemaLoader = slotSchemaLoader;
        return this;
    }

    /**
     * Overrides the slot value extractor used by the default chain.
     *
     * @param slotValueExtractor slot extractor implementation
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder slotValueExtractor(PromptSlotValueExtractor slotValueExtractor) {
        this.slotValueExtractor = slotValueExtractor;
        return this;
    }

    /**
     * Overrides the task prompt renderer used by the default chain.
     *
     * @param renderer task prompt renderer implementation
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder renderer(TaskPromptRenderer renderer) {
        this.renderer = renderer;
        return this;
    }

    /**
     * Configures the maximum accepted length in characters for free-text inputs.
     *
     * @param maxTextChars maximum accepted length in characters; unset falls back to
     *     {@link InputLimitConfig#DEFAULT_MAX_TEXT_CHARS}
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder maxTextChars(int maxTextChars) {
        this.maxTextChars = maxTextChars;
        return this;
    }

    /**
     * Overrides the classpath resource loader shared by template and schema resolution.
     *
     * @param resourceLoader resource loader implementation
     * @return current builder
     */
    public ClientPromptGenerationOrchestratorBuilder resourceLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        return this;
    }

    /**
     * Builds the default client prompt-generation orchestrator.
     *
     * @return fully assembled orchestrator with all required dependencies resolved
     */
    public DefaultClientPromptGenerationOrchestrator build() {
        require(llmClient, "LLM client must be configured.");
        require(scenarios, "Scenario definitions must be configured.");
        require(language, "Prompt language must be configured.");
        require(scenarioSystemPrompt, "Scenario system prompt must be configured.");
        require(scenarioUserPrompt, "Scenario user prompt must be configured.");
        require(slotSystemPrompt, "Slot system prompt must be configured.");
        require(slotUserPrompt, "Slot user prompt must be configured.");

        // Resolve optional dependencies in one place so all convenience entry points share the same
        // defaults.
        ClasspathPromptResourceLoader effectiveResourceLoader =
                resourceLoader == null ? new ClasspathPromptResourceLoader() : resourceLoader;
        PromptTemplateTextLoader effectiveTemplateLoader =
                templateLoader == null ? new ClasspathPromptTemplateLoader(effectiveResourceLoader) : templateLoader;
        PromptSlotSchemaLoader effectiveSlotSchemaLoader = slotSchemaLoader == null
                ? new ClasspathPromptSlotSchemaLoader(effectiveResourceLoader)
                : slotSchemaLoader;
        PromptSlotValueExtractor effectiveSlotValueExtractor = slotValueExtractor == null
                ? new DefaultStructuredPromptSlotValueExtractor(
                        llmClient, effectiveSlotSchemaLoader, slotSystemPrompt, slotUserPrompt)
                : slotValueExtractor;
        ScenarioRecognizer effectiveScenarioRecognizer =
                scenarioRecognizer == null ? new LlmScenarioRecognizer(llmClient)::recognize : scenarioRecognizer;
        TaskPromptRenderer effectiveRenderer = renderer == null ? new TaskPromptRenderer() : renderer;

        return new DefaultClientPromptGenerationOrchestrator(
                effectiveScenarioRecognizer,
                List.copyOf(scenarios),
                language,
                scenarioSystemPrompt,
                scenarioUserPrompt,
                effectiveTemplateLoader,
                effectiveSlotValueExtractor,
                effectiveRenderer,
                effectiveSlotSchemaLoader,
                maxTextChars == null ? InputLimitConfig.DEFAULT_MAX_TEXT_CHARS : maxTextChars);
    }

    private static void require(Object value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
    }
}
