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

package net.openan.a2at.sdk.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.openan.a2at.sdk.core.model.TemplateUri;

/**
 * Identifies one resource bundled with the SDK prompt resource tree.
 *
 * <p>Template resources mirror the {@link TemplateUri} layout one-to-one, so a template or slot schema for the URI
 * {@code Task-T/network-layer/ran-energy-saving/v1} lives at
 * {@code prompt_resources/templates/Task-T/network-layer/ran-energy-saving/v1/<language>/<fileName>}.
 *
 * @param category resource category such as prompts, templates, slots or scenarios
 * @param pathSegments segments between the category and the language, such as {@code [slot_extraction]} for a prompt
 *     or the full template URI segments for a template resource; empty for scenarios
 * @param language locale identifier
 * @param fileName target file name
 * @since 2026-06
 */
public record PromptResourceKey(String category, List<String> pathSegments, String language, String fileName) {

    /**
     * Validates the components and defensively copies the path segment list.
     *
     * @throws NullPointerException if any component is null
     * @throws IllegalArgumentException if any component is blank or not a simple path segment
     */
    public PromptResourceKey {
        validateSegment("category", category);
        Objects.requireNonNull(pathSegments, "pathSegments must not be null");
        for (String segment : pathSegments) {
            validateSegment("path segment", segment);
        }
        pathSegments = List.copyOf(pathSegments);
        validateSegment("language", language);
        validateSegment("fileName", fileName);
    }

    /**
     * Creates a prompt resource key for one prompt action bundle.
     *
     * @param action prompt action name
     * @param language locale identifier
     * @param fileName target file name
     * @return prompt resource key resolving to {@code prompt_resources/prompts/<action>/<language>/<fileName>}
     */
    public static PromptResourceKey prompt(String action, String language, String fileName) {
        return new PromptResourceKey("prompts", List.of(action), language, fileName);
    }

    /**
     * Creates a template resource key for one template bundle, mirroring the template URI layout.
     *
     * @param templateUri template URI identifying the bundle
     * @param language locale identifier
     * @param fileName target file name
     * @return template resource key resolving to
     *     {@code prompt_resources/templates/<templateUri>/<language>/<fileName>}
     */
    public static PromptResourceKey template(TemplateUri templateUri, String language, String fileName) {
        List<String> segments = new ArrayList<>();
        segments.add(templateUri.extensionName());
        segments.addAll(templateUri.pathSegments());
        segments.add(templateUri.templateVersion());
        return new PromptResourceKey("templates", List.copyOf(segments), language, fileName);
    }

    /**
     * Creates a slot schema resource key for one template bundle, mirroring the template URI layout.
     *
     * @param templateUri template URI identifying the bundle
     * @param language locale identifier
     * @param fileName target file name
     * @return slot schema resource key resolving to
     *     {@code prompt_resources/slots/<templateUri>/<language>/<fileName>}
     */
    public static PromptResourceKey slotSchema(TemplateUri templateUri, String language, String fileName) {
        List<String> segments = new ArrayList<>();
        segments.add(templateUri.extensionName());
        segments.addAll(templateUri.pathSegments());
        segments.add(templateUri.templateVersion());
        return new PromptResourceKey("slots", List.copyOf(segments), language, fileName);
    }

    /**
     * Creates a scenario catalog resource key.
     *
     * @param language locale identifier
     * @param fileName target file name
     * @return scenario resource key resolving to {@code prompt_resources/scenarios/<language>/<fileName>}
     */
    public static PromptResourceKey scenario(String language, String fileName) {
        return new PromptResourceKey("scenarios", List.of(), language, fileName);
    }

    /**
     * Resolves the relative classpath path for the current resource key.
     *
     * @return relative path under {@code prompt_resources/}
     */
    public String relativePath() {
        List<String> all = new ArrayList<>();
        all.add("prompt_resources");
        all.add(category);
        if (!"scenarios".equals(category)) {
            all.addAll(pathSegments);
        }
        all.add(language);
        all.add(fileName);
        return String.join("/", all);
    }

    private static void validateSegment(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.contains("..") || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException(fieldName + " must be a simple path segment");
        }
    }
}
