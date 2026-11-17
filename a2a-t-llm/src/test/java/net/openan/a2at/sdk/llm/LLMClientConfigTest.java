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

package net.openan.a2at.sdk.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LLMClientConfigTest {

    @Test
    void storesResolvedDefaultLlmClientConfiguration() {
        LLMClientConfig config = new LLMClientConfig(
                "openai",
                "gpt-4o-mini",
                "sk-test",
                "https://api.example.test/v1",
                10,
                1024,
                0.2d,
                15.5d,
                300,
                100,
                true,
                false,
                false,
                "none");

        assertEquals("openai", config.provider());
        assertEquals("gpt-4o-mini", config.model());
        assertEquals("sk-test", config.apiKey());
        assertEquals("https://api.example.test/v1", config.baseUrl());
        assertEquals(10, config.historyWindow());
        assertEquals(1024, config.maxTokens());
        assertEquals(0.2d, config.temperature());
        assertEquals(15.5d, config.timeoutSeconds());
        assertEquals(true, config.disableSystemProxy());
        assertEquals(false, config.sslVerify());
        assertEquals(false, config.detailLogEnabled());
        assertEquals(300, config.sessionMaxTotal());
        assertEquals(100, config.sessionMaxPerProvider());
        assertEquals("none", config.reasoningEffort());
    }

    @Test
    void legacyConstructorDefaultsSslVerifyToTrue() {
        LLMClientConfig config = new LLMClientConfig(
                "openai",
                "gpt-4o-mini",
                "sk-test",
                "https://api.example.test/v1",
                10,
                1024,
                0.2d,
                15.5d,
                300,
                100,
                true,
                "none");

        assertEquals(true, config.sslVerify());
        assertEquals(false, config.detailLogEnabled());
    }
}
