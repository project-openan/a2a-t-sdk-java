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

package net.openan.a2at.sdk.core.validation;

import org.jspecify.annotations.NonNull;

/**
 * Loads the template text for a template reference during validation.
 *
 * <p>Injected into {@link ValidationPipeline} as an optional gate that runs after the rule gate and before semantic
 * validation. Loaders resolve the template the content is validated against, so the semantic validator can compare the
 * content against the authoritative template body.
 *
 * @param <T> template addressing type the loader resolves
 * @since 2026-08
 */
@FunctionalInterface
public interface TemplateContentLoader<T> {

    /**
     * Loads the template text for the given template reference.
     *
     * @param reference template addressing value to resolve
     * @return loaded template text
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if the template cannot be resolved
     */
    @NonNull
    String load(@NonNull T reference);
}
