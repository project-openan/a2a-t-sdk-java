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

package net.openan.a2at.sdk.server.compliance;

import net.openan.a2at.sdk.server.model.PromptComplianceResult;

/**
 * Internal server-side prompt-compliance orchestration contract used by the public server facade.
 *
 * @since 2026-06
 */
@FunctionalInterface
public interface ServerPromptComplianceOrchestrator {

    /**
     * Checks one processed task prompt for server-side compliance.
     *
     * @param processedPromptText processed task prompt text
     * @return typed compliance result
     */
    PromptComplianceResult checkTaskPrompt(String processedPromptText);
}
