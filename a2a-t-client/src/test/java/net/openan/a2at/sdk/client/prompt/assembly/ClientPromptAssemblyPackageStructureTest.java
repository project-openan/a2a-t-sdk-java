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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import net.openan.a2at.sdk.client.prompt.orchestration.DefaultClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import org.junit.jupiter.api.Test;

class ClientPromptAssemblyPackageStructureTest {

    @Test
    void defaultAssemblyStillBuildsAndGeneratesPrompt() {
        LLMClient llmClient = new RecordingClient(
                "{\"matched\":true,\"scenario_code\":\"ran-energy-saving\",\"error_message\":null}",
                "{\"slots\":{\"site\":\"Site A\",\"additional_notes\":\"critical\",\"limit\":\"5\",\"severity\":\"high\"},\"slot_errors\":[]}");

        DefaultClientPromptGenerationOrchestrator orchestrator = ClientPromptGenerationOrchestratorBuilder.builder()
                .llmClient(llmClient)
                .scenarios(List.of(new ScenarioDefinition(
                        "ran-energy-saving", "Energy Saving", "Energy analysis", "Analyze site power")))
                .language("en-US")
                .scenarioSystemPrompt("Identify the best matching scenario.")
                .scenarioUserPrompt("Choose from the provided scenario list.")
                .slotSystemPrompt("Extract slots from the input.")
                .slotUserPrompt("Return slots as JSON.")
                .build();

        PromptGenerationResult result = orchestrator.generateTaskPrompt(Map.of(
                "site", "Site A",
                "additional_notes", "critical",
                "limit", "5",
                "severity", "high"));

        assertTrue(result.success());
        assertEquals(
                normalizeLineEndings("Site: Site A\nNotes: critical"),
                normalizeLineEndings(result.promptText().trim()));
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n");
    }

    private static final class RecordingClient implements LLMClient {

        private final java.util.List<String> payloads;

        private RecordingClient(String... payloads) {
            this.payloads = new java.util.ArrayList<>(java.util.List.of(payloads));
        }

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse(
                    payloads.remove(0), "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }
}
