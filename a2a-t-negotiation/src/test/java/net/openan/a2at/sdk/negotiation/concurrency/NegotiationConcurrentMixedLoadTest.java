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

package net.openan.a2at.sdk.negotiation.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestratorBuilder;
import org.junit.jupiter.api.Test;

/**
 * Runs a mixed concurrent load against one orchestrator and compares every thread's outcome with its serial baseline.
 *
 * <p>Threads perform deterministic from-data generation, LLM-backed from-text generation and validation-based parameter
 * extraction in parallel against one shared orchestrator whose scripted LLM client routes its responses by the schema
 * of the call. Any leaked exception, wrong routing or shared mutable state fails the test; each thread's result must
 * equal the serial baseline of its operation.
 */
class NegotiationConcurrentMixedLoadTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final int THREADS = 12;

    private static final int ITERATIONS = 5;

    @Test
    void everyThreadMatchesItsSerialBaselineWithoutExceptionLeaks() throws Exception {
        RoutingScriptedClient llm = new RoutingScriptedClient();
        NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
                .language("zh-CN")
                .llmClient(llm)
                .maxAttempts(3)
                .build();

        NegotiationProposeData proposeData = new NegotiationProposeData(
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                new InformationProposeContent(List.of(new NegotiationItem("节能区域", "松山湖")), null));
        MetadataContent fromDataBase = orchestrator.generateProposeFromData(proposeData, INFORMATION_PROPOSE_URI);
        MetadataContent fromTextBase = orchestrator.generateProposeFromText(
                "请提供节能区域信息。", new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE), INFORMATION_PROPOSE_URI);
        FilledParamData validateBase = orchestrator.validateProposePromptAndDataFilling(
                fromDataBase.promptText(),
                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                Map.of("type", "object"),
                INFORMATION_PROPOSE_URI);
        int baselineExtractionCalls = llm.extractionCalls.get();
        int baselineSemanticCalls = llm.semanticCalls.get();

        List<Callable<Void>> workers = new ArrayList<>();
        int fromDataThreads = 0;
        int fromTextThreads = 0;
        int validateThreads = 0;
        for (int index = 0; index < THREADS; index++) {
            int kind = index % 3;
            if (kind == 0) {
                fromDataThreads++;
                workers.add(callableOf(() -> {
                    MetadataContent result = orchestrator.generateProposeFromData(proposeData, INFORMATION_PROPOSE_URI);
                    assertEquals(fromDataBase.promptText(), result.promptText(), "from-data baseline");
                }));
            } else if (kind == 1) {
                fromTextThreads++;
                workers.add(callableOf(() -> {
                    MetadataContent result = orchestrator.generateProposeFromText(
                            "请提供节能区域信息。", new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE), INFORMATION_PROPOSE_URI);
                    assertEquals(fromTextBase.promptText(), result.promptText(), "from-text baseline");
                }));
            } else {
                validateThreads++;
                workers.add(callableOf(() -> {
                    FilledParamData filled = orchestrator.validateProposePromptAndDataFilling(
                            fromDataBase.promptText(),
                            new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                            Map.of("type", "object"),
                            INFORMATION_PROPOSE_URI);
                    assertEquals(validateBase.data(), filled.data(), "validation baseline");
                }));
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                workers.forEach(worker -> futures.add(pool.submit(worker)));
            }
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            org.junit.jupiter.api.Assertions.assertTrue(
                    pool.awaitTermination(30, TimeUnit.SECONDS), "pool must terminate");
        }

        assertEquals(
                baselineExtractionCalls + fromTextThreads * ITERATIONS,
                llm.extractionCalls.get(),
                "extraction call count");
        assertEquals(
                baselineSemanticCalls + validateThreads * ITERATIONS, llm.semanticCalls.get(), "semantic call count");
    }

    private static Callable<Void> callableOf(ThrowingRunnable action) {
        return () -> {
            action.run();
            return null;
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    /**
     * Thread-safe scripted LLM client routing by the output schema of the call: schemas carrying the semantic-verdict
     * key belong to the validation pipeline, every other schema to the content-extraction step.
     */
    private static final class RoutingScriptedClient implements LLMClient {

        private final AtomicInteger extractionCalls = new AtomicInteger();

        private final AtomicInteger semanticCalls = new AtomicInteger();

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            Object properties = jsonSchema == null ? null : jsonSchema.get("properties");
            boolean semantic =
                    properties instanceof Map<?, ?> propertyMap && propertyMap.containsKey("semantic_verdict");
            String payload;
            if (semantic) {
                semanticCalls.incrementAndGet();
                payload = "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],"
                        + "\"params\":{\"region\":\"松山湖\"}}";
            } else {
                extractionCalls.incrementAndGet();
                payload = "{\"items\":[{\"name\":\"节能区域\",\"value\":\"松山湖\"}],\"relationship\":null}";
            }
            return new LLMResponse(payload, "test-model", Map.of("prompt_tokens", 1, "completion_tokens", 1), Map.of());
        }
    }
}
