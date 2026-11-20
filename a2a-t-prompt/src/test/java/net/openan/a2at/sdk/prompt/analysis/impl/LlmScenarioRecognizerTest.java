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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.json.JsonValueParser;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.analysis.exception.ScenarioRecognitionException;
import net.openan.a2at.sdk.prompt.analysis.model.ScenarioRecognitionResult;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import org.junit.jupiter.api.Test;

class LlmScenarioRecognizerTest {

    @Test
    void recognizeBuildsStructuredMessagesAndReturnsMatchedScenario() {
        RecordingClient llmClient =
                new RecordingClient("{\"matched\":true,\"scenario_code\":\"ran-energy-saving\",\"error_message\":null}");

        LlmScenarioRecognizer recognizer = new LlmScenarioRecognizer(llmClient);
        ScenarioRecognitionResult result = recognizer.recognize(
                "Please analyze site A energy usage.",
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.");

        assertTrue(result.matched());
        assertEquals("ran-energy-saving", result.scenarioCode());
        assertEquals(2, llmClient.lastMessages().size());
        assertEquals("system", llmClient.lastMessages().get(0).get("role"));
        assertTrue(llmClient.lastMessages().get(1).get("content").contains("ran-energy-saving"));
        assertTrue(llmClient.lastSchema().containsKey("required"));
    }

    @Test
    void recognizeRejectsMatchedPayloadWithoutScenarioCode() {
        LLMClient llmClient = new RecordingClient("{\"matched\":true,\"scenario_code\":null,\"error_message\":null}");

        LlmScenarioRecognizer recognizer = new LlmScenarioRecognizer(llmClient);

        assertThrows(
                ScenarioRecognitionException.class,
                () -> recognizer.recognize(
                        "Analyze site A energy usage.",
                        List.of(new ScenarioDefinition(
                                "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                        "Identify the best matching scenario.",
                        "Choose from the provided scenario list."));
    }

    @Test
    void recognizeCanUseSharedJsonParserAbstraction() {
        LLMClient llmClient = new RecordingClient("ignored");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("matched", true);
        payload.put("scenario_code", "ran-energy-saving");
        payload.put("error_message", null);
        RecordingJsonValueParser parser = new RecordingJsonValueParser(payload);

        LlmScenarioRecognizer recognizer = new LlmScenarioRecognizer(llmClient, parser);
        ScenarioRecognitionResult result = recognizer.recognize(
                "Please analyze site A energy usage.",
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.");

        assertTrue(result.matched());
        assertEquals("ran-energy-saving", result.scenarioCode());
        assertEquals("ignored", parser.lastPayload);
    }

    @Test
    void scenarioRecognizerImplementsScenarioRecognizer() {
        LlmScenarioRecognizer recognizer = new LlmScenarioRecognizer(new RecordingClient("ignored"));
        assertInstanceOf(ScenarioRecognizer.class, recognizer);
    }

    @Test
    void recognizeReturnsUnmatchedWithErrorMessageWhenMatchedIsFalse() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":false,\"scenario_code\":null,\"error_message\":\"No scenario matched.\"}");

        LlmScenarioRecognizer recognizer = new LlmScenarioRecognizer(llmClient);
        ScenarioRecognitionResult result = recognizer.recognize(
                "Unrelated input.",
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.");

        assertFalse(result.matched());
        assertNotNull(result.errorMessage());
        assertEquals("No scenario matched.", result.errorMessage());
    }

    @Test
    void recognizeTreatsNonBooleanMatchedAsUnmatched() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":\"yes\",\"scenario_code\":null,\"error_message\":\"Ambiguous\"}");

        LlmScenarioRecognizer recognizer = new LlmScenarioRecognizer(llmClient);
        ScenarioRecognitionResult result = recognizer.recognize(
                "Ambiguous input.",
                List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                "Identify the best matching scenario.",
                "Choose from the provided scenario list.");

        assertFalse(result.matched());
        assertEquals("Ambiguous", result.errorMessage());
    }

    @Test
    void recognizeRejectsUnmatchedPayloadWithScenarioCode() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":false,\"scenario_code\":\"ran-energy-saving\",\"error_message\":null}");

        LlmScenarioRecognizer recognizer = new LlmScenarioRecognizer(llmClient);

        assertThrows(
                ScenarioRecognitionException.class,
                () -> recognizer.recognize(
                        "Analyze site A energy usage.",
                        List.of(new ScenarioDefinition(
                                "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                        "Identify the best matching scenario.",
                        "Choose from the provided scenario list."));
    }

    @Test
    void recognizeRejectsNonStringScenarioCodeField() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":42,\"error_message\":null}");

        LlmScenarioRecognizer recognizer = new LlmScenarioRecognizer(llmClient);

        assertThrows(
                ScenarioRecognitionException.class,
                () -> recognizer.recognize(
                        "Analyze site A energy usage.",
                        List.of(new ScenarioDefinition(
                                "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")),
                        "Identify the best matching scenario.",
                        "Choose from the provided scenario list."));
    }

    private static final class RecordingClient implements LLMClient {

        private final String payload;

        private List<Map<String, String>> lastMessages;

        private Map<String, Object> lastSchema;

        private RecordingClient(String payload) {
            this.payload = payload;
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            this.lastMessages = messages;
            this.lastSchema = jsonSchema;
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }

        List<Map<String, String>> lastMessages() {
            return lastMessages;
        }

        Map<String, Object> lastSchema() {
            return lastSchema;
        }
    }

    private static final class RecordingJsonValueParser implements JsonValueParser {
        private final Map<String, Object> result;
        private String lastPayload;

        private RecordingJsonValueParser(Map<String, Object> result) {
            this.result = result;
        }

        @Override
        public Map<String, Object> parseObject(String payload) {
            this.lastPayload = payload;
            return result;
        }
    }
}