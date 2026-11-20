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

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.openan.a2at.sdk.llm.providers.OpenAIClient;

/**
 * Registry and factory for LLM provider clients.
 *
 * @since 2026-06
 */
public final class LLMClientFactory {

    private static final Map<String, Class<? extends LLMClient>> CLIENTS = new LinkedHashMap<>();

    static {
        CLIENTS.put("openai", OpenAIClient.class);
    }

    private LLMClientFactory() {}

    public static void register(String provider, Class<? extends LLMClient> clientClass) {
        String normalizedProvider = normalizeProvider(provider);
        if (CLIENTS.containsKey(normalizedProvider)) {
            throw new LLMConfigError("LLM provider '" + normalizedProvider + "' is already registered");
        }
        CLIENTS.put(normalizedProvider, clientClass);
    }

    public static LLMClient create(String provider, LLMClientConfig config) {
        String normalizedProvider = normalizeProvider(provider);
        Class<? extends LLMClient> clientClass = CLIENTS.get(normalizedProvider);
        if (clientClass == null) {
            throw new LLMConfigError("Unknown llm provider: " + normalizedProvider + ". Available: " + availableProviders());
        }
        try {
            return clientClass.getConstructor(LLMClientConfig.class).newInstance(config);
        } catch (NoSuchMethodException exception) {
            throw new LLMConfigError(
                    "LLM provider '" + normalizedProvider + "' must expose a public LLMClientConfig constructor",
                    exception);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new LLMConfigError("Failed to create LLM provider '" + normalizedProvider + "'", exception);
        }
    }

    public static List<String> availableProviders() {
        return CLIENTS.keySet().stream().sorted().toList();
    }

    private static String normalizeProvider(String value) {
        String provider = value == null ? "" : value.trim();
        if (provider.isEmpty()
                || !provider.equals(provider.toLowerCase(Locale.ROOT))
                || provider.chars().anyMatch(Character::isWhitespace)) {
            throw new LLMConfigError("LLM provider must use lowercase non-whitespace characters");
        }
        return provider;
    }
}
