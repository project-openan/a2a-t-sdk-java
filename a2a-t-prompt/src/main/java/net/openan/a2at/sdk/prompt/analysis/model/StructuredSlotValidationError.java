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

package net.openan.a2at.sdk.prompt.analysis.model;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One slot validation error emitted by the structured extraction step.
 *
 * <p>The {@code message} is rendered by the SDK from the code's message template; {@code facts} carries the structured
 * fact values that produced the message.
 *
 * @param slotName slot name
 * @param code validation error code
 * @param message validation message
 * @param facts fact values keyed by fact parameter name, may be null
 * @since 2026-06
 */
public record StructuredSlotValidationError(
        String slotName, String code, String message, @Nullable Map<String, String> facts) {

    public StructuredSlotValidationError {
        facts = facts == null ? null : Map.copyOf(facts);
    }

    /**
     * Creates a slot validation error without structured facts.
     *
     * @param slotName slot name
     * @param code validation error code
     * @param message validation message
     */
    public StructuredSlotValidationError(String slotName, String code, String message) {
        this(slotName, code, message, null);
    }
}
