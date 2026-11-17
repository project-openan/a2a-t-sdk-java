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

package net.openan.a2at.sdk.corpus.engine.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.client.prompt.orchestration.ClientPromptGenerationOrchestrator;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.TemplateUri;

/**
 * Task-T client-side registration: JSON step arguments are bound to the production
 * {@link ClientPromptGenerationOrchestrator} assembled by {@link SdkRuntimeAssembler}.
 */
public final class ClientApis {

    private ClientApis() {}

    public static void registerTaskApis(ApiRegistry registry, ClientPromptGenerationOrchestrator client) {
        registry.register("generateTaskPromptFromText", args ->
                metadataToMap(client.generateTaskPromptFromText(
                        Args.text(args, "text"), Args.templateUri(args, "templateUri"))));
        registry.register("generateTaskPromptFromDataWithSchema", args ->
                metadataToMap(client.generateTaskPromptFromDataWithSchema(
                        Args.map(args, "data"), Args.map(args, "schema"), Args.templateUri(args, "templateUri"))));
    }

    static Map<String, Object> metadataToMap(MetadataContent content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templateUri", content.templateUri());
        map.put("promptText", content.promptText());
        map.put("extensionUri", content.extensionUri());
        return map;
    }

    static TemplateUri templateUri(String raw) {
        return TemplateUri.parse(raw)
                .orElseThrow(() -> new IllegalArgumentException("Unparseable template URI: " + raw));
    }

    static final class Args {

        private Args() {}

        static String text(Map<String, Object> args, String name) {
            Object value = args.get(name);
            if (!(value instanceof String str)) {
                throw new IllegalArgumentException("step argument " + name + " must be a string: " + value);
            }
            // Blank strings are passed through to the SDK: the facade raises its own coded error
            // (e.g. negotiation.invalid_input), which the expectation layer asserts against.
            return str;
        }

        static Map<String, Object> map(Map<String, Object> args, String name) {
            Object value = args.get(name);
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException("step argument " + name + " must be an object: " + value);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) value;
            if (typed.isEmpty()) {
                throw new IllegalArgumentException("step argument " + name + " must not be an empty object");
            }
            return typed;
        }

        static TemplateUri templateUri(Map<String, Object> args, String name) {
            return ClientApis.templateUri(text(args, name));
        }
    }
}