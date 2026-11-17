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
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.validation.ContentValidator;

/**
 * Task-T server-side registration: the {@code validateTaskPromptAndDataFilling} step is bound to the production
 * {@link ContentValidator} assembled by {@link SdkRuntimeAssembler} from the default server builder.
 */
public final class ServerApis {

    private ServerApis() {}

    public static void registerTaskApis(ApiRegistry registry, ContentValidator taskContentValidator) {
        registry.register("validateTaskPromptAndDataFilling", args -> filledToMap(taskContentValidator.validate(
                ClientApis.Args.text(args, "promptText"),
                ClientApis.Args.map(args, "schema"),
                ClientApis.Args.templateUri(args, "templateUri"))));
    }

    static Map<String, Object> filledToMap(FilledParamData filled) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("data", filled.data() == null ? Map.of() : filled.data());
        return map;
    }
}