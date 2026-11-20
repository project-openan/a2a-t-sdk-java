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

package net.openan.a2at.sdk.negotiation.types.model;

/**
 * Minimal task prompt compliance result shared with server-side negotiation flows.
 *
 * @param passed prompt compliance result
 * @param failure prompt compliance failure payload
 * @since 2026-06
 */
public record TaskPromptComplianceResult(boolean passed, TaskPromptComplianceFailure failure) {

    /**
     * Creates a successful compliance result.
     *
     * @return successful compliance result
     */
    public static TaskPromptComplianceResult success() {
        return new TaskPromptComplianceResult(true, null);
    }

    /**
     * Creates a failed compliance result.
     *
     * @param failure standardized failure payload
     * @return failed compliance result
     */
    public static TaskPromptComplianceResult failure(TaskPromptComplianceFailure failure) {
        return new TaskPromptComplianceResult(false, failure);
    }
}
