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

package net.openan.a2at.sample.subscribe_incident.shared.mock;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.subscribe_incident.shared.logging.SampleLoggingFormatter;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.providers.OpenAIClient;

/**
 * Logging-aware LLM client wrapper used by the sample.
 *
 * <p>Wraps either the real {@link OpenAIClient} or the {@link SampleMockLLMClient} and logs the full request (messages,
 * JSON schema, temperature, max tokens) and response (content, model, usage) for every structured call, mirroring the
 * Python sample's {@code llm_logger}. In mock mode an extra {@code llm-mock} stage line marks that the canned response
 * was used.
 *
 * @since 2026-08
 */
public final class SampleLoggingLLMClient implements LLMClient {

    private static volatile boolean mockMode;

    private static volatile String role = "llm";

    private static volatile Consumer<String> logSink = System.out::println;

    private final LLMClient delegate;

    /**
     * Configures the wrapper's behavior before any client is created.
     *
     * @param mockEnabled whether the delegate should be the mock client
     * @param roleLabel log role label (for example {@code client} or {@code server})
     * @param sink log output sink (defaults to {@code System.out})
     */
    public static synchronized void configure(boolean mockEnabled, String roleLabel, Consumer<String> sink) {
        mockMode = mockEnabled;
        if (roleLabel != null && !roleLabel.isBlank()) {
            role = roleLabel.trim();
        }
        if (sink != null) {
            logSink = sink;
        }
    }

    /**
     * Creates a logging LLM client. The supplied config is passed through to the delegate.
     *
     * @param config resolved LLM provider config
     */
    public SampleLoggingLLMClient(LLMClientConfig config) {
        if (mockMode) {
            this.delegate = new SampleMockLLMClient(config);
        } else {
            this.delegate = new OpenAIClient(config);
        }
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema, Double temperature, Integer maxTokens) {
        logSink.accept(SampleLoggingFormatter.formatPayloadLog(
                role,
                "llm-request",
                Map.of(
                        "messages",
                        messages,
                        "json_schema",
                        jsonSchema,
                        "temperature",
                        temperature == null ? "null" : temperature,
                        "max_tokens",
                        maxTokens == null ? "null" : maxTokens)));
        LLMResponse response = delegate.structured(messages, jsonSchema, temperature, maxTokens);
        if (mockMode) {
            logSink.accept(SampleLoggingFormatter.formatStageLog(role, "llm-mock", "using canned mock LLM response"));
        }
        logSink.accept(SampleLoggingFormatter.formatPayloadLog(
                role,
                "llm-response",
                Map.of(
                        "content", response.content(),
                        "model", response.model(),
                        "usage", response.usage())));
        return response;
    }
}
