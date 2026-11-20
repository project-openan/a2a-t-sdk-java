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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.resources.ClasspathResourceDirectories;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Loads shared prompt templates from packaged classpath prompt resources.
 *
 * <p>A full template URI resolves directly to {@code templates/<templateUri>/<language>/template.md}. A bare scenario
 * code is probed per template type — first in a fixed order and then in the order the extension directories appear
 * under {@code prompt_resources/templates/} on the classpath — trying both the {@code network-layer} domain layout and
 * the plain layout, so extensions bundled later — such as Authorization-T — are loadable without extending a hardcoded
 * list.
 *
 * @since 2026-06
 */
public final class ClasspathPromptTemplateLoader implements PromptTemplateTextLoader {

    private static final List<String> KNOWN_TEMPLATE_TYPES = List.of(
            StandardTemplates.TASK_EXTENSION_NAME, StandardTemplates.NOTIFICATION_EXTENSION_NAME, StandardTemplates.NEGOTIATION_EXTENSION_NAME);

    private static final List<String> TEMPLATE_TYPES = discoverTemplateTypes();

    private final ClasspathPromptResourceLoader resourceLoader;

    public ClasspathPromptTemplateLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String loadTemplate(String scenarioCode, String language) {
        Optional<TemplateUri> parsed = TemplateUri.parse(scenarioCode);
        if (parsed.isPresent()) {
            return resourceLoader.loadText(PromptResourceKey.template(parsed.orElseThrow(), language, "template.md"));
        }
        for (String templateType : TEMPLATE_TYPES) {
            for (TemplateUri candidate : bareCodeCandidates(templateType, scenarioCode)) {
                try {
                    return resourceLoader.loadText(PromptResourceKey.template(candidate, language, "template.md"));
                } catch (ResourceNotFoundException ignored) {
                    // try next candidate layout
                }
            }
        }
        throw new ResourceNotFoundException(
                "Prompt resource file does not exist.",
                "prompt_resources/templates/*/network-layer/" + scenarioCode + "/v1/" + language
                        + "/template.md (or the layout without the network-layer segment)");
    }

    /**
     * Returns the candidate template URIs a bare scenario code can address under one template type: the
     * {@code network-layer} domain layout first, then the plain layout.
     */
    private static List<TemplateUri> bareCodeCandidates(String templateType, String scenarioCode) {
        return List.of(
                TemplateUri.of(templateType, StandardTemplates.NETWORK_LAYER_SEGMENT,
                        scenarioCode),
                TemplateUri.of(templateType, scenarioCode));
    }

    private static List<String> discoverTemplateTypes() {
        Set<String> types = new LinkedHashSet<>(KNOWN_TEMPLATE_TYPES);
        try {
            types.addAll(ClasspathResourceDirectories.list("prompt_resources/templates/"));
        } catch (Exception ignored) {
            // classpath enumeration is unavailable; fall back to the known types only
        }
        return List.copyOf(types);
    }
}
