package net.openan.a2at.sdk.prompt.analysis.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.core.json.JacksonJsonValueParser;
import net.openan.a2at.sdk.core.json.JsonValueParser;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.prompt.analysis.exception.ScenarioRecognitionException;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;

/**
 * Minimal scenario recognizer that delegates classification to a structured LLM response.
 *
 * @since 2026-05
 */
public final class ScenarioRecognizer {

    private final LLMClient llmClient;

    private final JsonValueParser parser;

    /**
     * Creates a scenario recognizer backed by one LLM client.
     *
     * @param llmClient LLM client
     */
    public ScenarioRecognizer(LLMClient llmClient) {
        this(llmClient, new JacksonJsonValueParser());
    }

    /**
     * Creates a scenario recognizer backed by one LLM client and one shared JSON parser abstraction.
     *
     * @param llmClient LLM client
     * @param parser shared JSON parser abstraction
     */
    public ScenarioRecognizer(LLMClient llmClient, JsonValueParser parser) {
        this.llmClient = llmClient;
        this.parser = parser;
    }

    /**
     * Recognizes the most likely scenario for one normalized input string.
     *
     * @param normalizedInput normalized input text
     * @param scenarios supported scenarios
     * @param systemPrompt system prompt for recognition
     * @param userPrompt user prompt for recognition
     * @return recognition result
     */
    public ScenarioRecognitionResult recognize(
            String normalizedInput, List<ScenarioDefinition> scenarios, String systemPrompt, String userPrompt) {
        Map<String, Object> payload = parser.parseObject(llmClient
                .structured(
                        toStructuredMessages(buildMessages(normalizedInput, scenarios, systemPrompt, userPrompt)),
                        schema(),
                        null,
                        null)
                .content());

        boolean matched = Boolean.TRUE.equals(payload.get("matched"));
        String scenarioCode = (String) payload.get("scenario_code");
        String errorMessage = (String) payload.get("error_message");

        if (matched && (scenarioCode == null || scenarioCode.isBlank())) {
            throw new ScenarioRecognitionException("Matched scenario recognition result must include scenario_code.");
        }
        if (!matched && scenarioCode != null) {
            throw new ScenarioRecognitionException(
                    "Unmatched scenario recognition result must not include scenario_code.");
        }
        return new ScenarioRecognitionResult(matched, scenarioCode, errorMessage);
    }

    private static List<PromptMessage> buildMessages(
            String normalizedInput, List<ScenarioDefinition> scenarios, String systemPrompt, String userPrompt) {
        // Keep the prompt assembly explicit so the exact recognizer contract remains visible at the
        // SDK
        // layer.
        String scenarioSummary = scenarios.stream()
                .map(scenario -> scenario.scenarioCode() + ": " + scenario.description())
                .collect(Collectors.joining("\n"));
        return List.of(
                new PromptMessage("system", systemPrompt),
                new PromptMessage(
                        "user", userPrompt + "\n\nScenarios:\n" + scenarioSummary + "\n\nInput:\n" + normalizedInput));
    }

    private static Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("matched", "scenario_code", "error_message"));
        return schema;
    }

    private static List<Map<String, String>> toStructuredMessages(List<PromptMessage> messages) {
        return messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
    }

}
