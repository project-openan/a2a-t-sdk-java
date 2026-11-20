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

package net.openan.a2at.sdk.negotiation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Outcome of the LLM-backed semantic validation of a negotiation message.
 *
 * <p>The semantic validation step combines the semantic verdict, the negotiation type implied by the message sections,
 * the structured semantic errors and the parameters extracted from the message per the caller-provided schema.
 *
 * @param verdict overall semantic verdict; {@code true} only when every semantic constraint holds
 * @param negotiationType negotiation type implied by the message sections, one of {@code information}, {@code target}
 *     or {@code feasibility}; may be null when the verdict is false
 * @param errors structured semantic errors using language-neutral {@code section.*} slot names; empty when the verdict
 *     is true
 * @param params parameters extracted from the message per the caller-provided schema
 * @since 2026-08
 */
public record SemanticValidationResult(
        boolean verdict, String negotiationType, List<SlotValidationError> errors, Map<String, Object> params) {

    /**
     * Normalizes the errors and the extracted parameters.
     *
     * @throws NullPointerException if the errors list or the params map is null
     */
    public SemanticValidationResult {
        errors = List.copyOf(errors);
        params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }
}
