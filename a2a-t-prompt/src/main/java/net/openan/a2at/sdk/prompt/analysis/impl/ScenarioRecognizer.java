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