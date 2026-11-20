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

package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Loads shared scenario catalogs from packaged classpath prompt resources.
 *
 * @since 2026-06
 */
public final class ClasspathPromptScenarioCatalogLoader {

    private final ClasspathPromptResourceLoader resourceLoader;

    public ClasspathPromptScenarioCatalogLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<ScenarioDefinition> load(String language) {
        String payload = resourceLoader.loadText(PromptResourceKey.scenario(language, "scenarios.json"));
        try {
            return PromptResourceJsonParser.parse(payload, ScenarioCatalog.class)
                    .scenarios();
        } catch (JsonProcessingException exception) {
            throw new A2ATError("Failed to parse scenario catalog for language: " + language, exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScenarioCatalog(@JsonProperty("scenarios") List<ScenarioDefinition> scenarios) {}
}
