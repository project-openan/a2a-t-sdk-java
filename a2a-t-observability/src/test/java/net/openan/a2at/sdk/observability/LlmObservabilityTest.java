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

package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmObservabilityTest {

    private InMemoryMetricReader reader;

    @BeforeEach
    void setUp() {
        reader = InMemoryMetricReader.create();
        SdkMeterProvider provider =
                SdkMeterProvider.builder().registerMetricReader(reader).build();
        GlobalOpenTelemetry.set(
                OpenTelemetrySdk.builder().setMeterProvider(provider).build());
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void structuredStashesUsageAndRecordsTokenHistogram() {
        LLMClient scripted = (messages, schema, temperature, maxTokens) ->
                new LLMResponse("ok", "gpt-x", Map.of("prompt_tokens", 10, "completion_tokens", 5), Map.of());
        ObservabilityLLMClient client = new ObservabilityLLMClient(scripted);
        client.structured(List.of(Map.of("role", "user", "content", "hi")), Map.of(), null, null);

        LlmUsageStash stash = LlmUsageStashHolder.getAndClear();
        assertNotNull(stash);
        assertEquals(10, stash.inputTokens());
        assertEquals(5, stash.outputTokens());
        assertEquals("gpt-x", stash.requestModel());
        assertNull(LlmUsageStashHolder.getAndClear());

        var metrics = reader.collectAllMetrics();
        assertTrue(
                metrics.stream()
                        .anyMatch(m -> "gen_ai.client.token.usage".equals(m.getName())
                                && !m.getHistogramData().getPoints().isEmpty()),
                "token usage histogram must be recorded");
    }

    @Test
    void metricDisabledStillStashes() {
        System.setProperty("A2AT_METRIC_ENABLED", "false");
        try {
            LLMClient scripted = (messages, schema, temperature, maxTokens) ->
                    new LLMResponse("ok", "m", Map.of("prompt_tokens", 1, "completion_tokens", 2), Map.of());
            new ObservabilityLLMClient(scripted).structured(List.of(), Map.of(), null, null);
            assertNotNull(LlmUsageStashHolder.getAndClear());
            assertEquals(0, reader.collectAllMetrics().size());
        } finally {
            System.clearProperty("A2AT_METRIC_ENABLED");
        }
    }

    @Test
    void stashFailurePathKeepsSemantics() {
        // delegate exceptions propagate unchanged; nothing stashed (observability never breaks business)
        LLMClient throwing = (messages, schema, temperature, maxTokens) -> {
            throw new IllegalStateException("llm down");
        };
        assertThrows(IllegalStateException.class, () -> new ObservabilityLLMClient(throwing)
                .structured(List.of(), Map.of(), null, null));
        assertNull(LlmUsageStashHolder.getAndClear());
    }
}
