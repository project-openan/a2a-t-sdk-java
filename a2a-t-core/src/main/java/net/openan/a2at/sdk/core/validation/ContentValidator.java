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

import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.TemplateUri;
import org.jspecify.annotations.NonNull;

/**
 * Entry point for validating content and extracting filled parameters.
 *
 * <p>Implementations orchestrate the full validation pipeline: input validation, rule-level gate, semantic validation
 * and parameter merging.
 *
 * @since 2026-08
 */
public interface ContentValidator {

    /**
     * Validates one content prompt and extracts its filled parameters.
     *
     * @param prompt content prompt text
     * @param schema caller-provided parameter JSON schema
     * @param templateUri URI of the template the content is validated against, such as
     *     {@code Task-T/network-layer/ran-energy-saving/v1}
     * @return filled parameter data carrying the merged parameters
     * @throws ContentValidationException with {@code negotiation.invalid_input} if the template URI is null or
     *     malformed, the prompt is null or blank, or the schema is null
     * @throws ContentValidationException if the validation fails at any stage
     */
    FilledParamData validate(
            @NonNull String prompt, @NonNull Map<String, Object> schema, @NonNull TemplateUri templateUri);
}
