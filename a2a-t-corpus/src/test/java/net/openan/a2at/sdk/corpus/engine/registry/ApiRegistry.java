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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Explicit registry mapping SDK facade method names to their {@link ApiHandler}. Registration is declarative (never
 * reflective): adding a new API is one registry line that the IDE can navigate. Unknown method names fail at load
 * time.
 */
public final class ApiRegistry {

    private final Map<String, ApiHandler> handlers = new LinkedHashMap<>();

    public ApiRegistry register(String apiName, ApiHandler handler) {
        if (handlers.containsKey(apiName)) {
            throw new IllegalStateException("API registered twice: " + apiName);
        }
        handlers.put(apiName, handler);
        return this;
    }

    public Optional<ApiHandler> handler(String apiName) {
        return Optional.ofNullable(handlers.get(apiName));
    }

    public java.util.Set<String> apiNames() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
}