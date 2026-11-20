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

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits the one-time warning for a built-in classpath fallback of a local-file business resource.
 *
 * <p>The warning is deduplicated per resource path across the whole local-file access: the same resource path warns at
 * most once, regardless of whether the first fallback happens at assembly time or at runtime. The deduplication set is
 * shared by the loaders of one {@link PromptResourceAccess.LocalFileAccess} and is thread-safe.
 */
final class BuiltinFallbackWarnings {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuiltinFallbackWarnings.class);

    private BuiltinFallbackWarnings() {}

    /**
     * Warns once for a built-in fallback of the given resource path.
     *
     * @param warnedPaths thread-safe set of resource paths already warned about
     * @param resourcePath path of the resource that fell back to the built-in classpath copy
     */
    static void warnOnce(Set<String> warnedPaths, String resourcePath) {
        if (warnedPaths.add(resourcePath)) {
            LOGGER.atWarn().log("prompt_resource_builtin_fallback path={} source=classpath", resourcePath);
        }
    }
}
