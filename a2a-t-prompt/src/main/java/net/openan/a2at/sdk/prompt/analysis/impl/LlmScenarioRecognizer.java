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

package net.openan.a2at.sdk.prompt.analysis.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * @since 2026-08
 */
public final class LlmScenarioRecognizer implements ScenarioRecognizer {

    private final LLMClient llmClient;

    private final JsonValueParser parser;

    /**
     * Creates a scenario recognizer backed by one LLM client.
     *
     * @param llmClient LLM client
     */
    public LlmScenarioRecognizer(LLMClient llmClient) {
        this(llmClient, new JacksonJsonValueParser());
    }

    /**
     * Creates a scenario recognizer backed by one LLM client and one shared JSON parser abstraction.
     *
     * @param llmClient LLM client
     * @param parser shared JSON parser abstraction
     */
    public LlmScenarioRecognizer(LLMClient llmClient, JsonValueParser parser) {
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
    @Override
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
        Optional<String> scenarioCode = optionalString(payload.get("scenario_code"), "scenario_code");
        Optional<String> errorMessage = optionalString(payload.get("error_message"), "error_message");

        if (matched && (!scenarioCode.isPresent() || scenarioCode.orElseThrow().isBlank())) {
            throw new ScenarioRecognitionException("Matched scenario recognition result must include scenario_code.");
        }
        if (!matched && scenarioCode.isPresent()) {
            throw new ScenarioRecognitionException(
                    "Unmatched scenario recognition result must not include scenario_code.");
        }
        return new ScenarioRecognitionResult(matched, scenarioCode.orElse(null), errorMessage.orElse(null));
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

    private static Optional<String> optionalString(Object value, String fieldName) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String text) {
            return Optional.of(text);
        }
        throw new ScenarioRecognitionException("Scenario recognition field must be a string: " + fieldName);
    }
}