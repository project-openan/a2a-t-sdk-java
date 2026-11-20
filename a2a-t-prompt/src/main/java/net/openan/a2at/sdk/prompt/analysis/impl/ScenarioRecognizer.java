package net.openan.a2at.sdk.prompt.analysis.impl;

import java.util.List;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;

/**
 * Shared recognition contract exported by the prompt module.
 *
 * <p>This functional interface defines the single abstract method for scenario recognition. Client-side wrappers and
 * test injections use this type as the dependency, while {@link LlmScenarioRecognizer} provides the LLM-based
 * implementation.
 *
 * @since 2026-08
 */
@FunctionalInterface
public interface ScenarioRecognizer {

    ScenarioRecognitionResult recognize(
            String normalizedInput, List<ScenarioDefinition> scenarios, String systemPrompt, String userPrompt);
}