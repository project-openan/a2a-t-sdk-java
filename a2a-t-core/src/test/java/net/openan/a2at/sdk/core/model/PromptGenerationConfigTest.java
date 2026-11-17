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

package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PromptGenerationConfig}.
 *
 * <p>Tests cover the construction of prompt generation configuration
 * with all required fields.
 *
 * @since 2026-06
 */
class PromptGenerationConfigTest {

    /**
     * Verifies that {@link PromptGenerationConfig} can be constructed with all
     * prompt-related fields.
     *
     * <p>Scenario: Create a config with language, scenario prompts (system and user),
     * and slot prompts (system and user).
     * Expected result: All fields are correctly accessible via their accessor methods.
     */
    @Test
    void should_createConfigWithAllFields_When_constructorCalled() {
        PromptGenerationConfig config = new PromptGenerationConfig(
                "zh-CN",
                "scenario-system",
                "scenario-user",
                "slot-system",
                "slot-user");

        assertEquals("zh-CN", config.language());
        assertEquals("scenario-system", config.scenarioSystemPrompt());
        assertEquals("scenario-user", config.scenarioUserPrompt());
        assertEquals("slot-system", config.slotSystemPrompt());
        assertEquals("slot-user", config.slotUserPrompt());
    }
}