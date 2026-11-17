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

package net.openan.a2at.sdk.core.model;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Structured validation error details for one named slot.
 *
 * <p>Instances are carried inside parameter-extraction failures so callers can inspect which slot failed, under which
 * error code, and why, without parsing exception messages. The {@code message} is rendered by the SDK from the code's
 * message template; {@code facts} carries the structured fact values that produced the message.
 *
 * @param slotName name of the slot the error refers to
 * @param code machine-readable error code for the error
 * @param message human-readable explanation of the error
 * @param facts fact values keyed by fact parameter name, may be null
 * @since 2026-08
 */
public record SlotValidationError(String slotName, String code, String message, @Nullable Map<String, String> facts) {

    public SlotValidationError {
        facts = facts == null ? null : Map.copyOf(facts);
    }

    /**
     * Creates a slot validation error without structured facts.
     *
     * @param slotName name of the slot the error refers to
     * @param code machine-readable error code for the error
     * @param message human-readable explanation of the error
     */
    public SlotValidationError(String slotName, String code, String message) {
        this(slotName, code, message, null);
    }
}
