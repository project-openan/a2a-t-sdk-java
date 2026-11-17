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

package net.openan.a2at.sdk.server.validation;

import net.openan.a2at.sdk.server.model.ProcessedPromptMetadata;

/**
 * Validates that a processed task prompt is semantically consistent with extracted metadata.
 *
 * @since 2026-06
 */
public interface ServerPromptSemanticValidator {

    /**
     * Validates one processed task prompt against extracted metadata.
     *
     * @param processedPromptText processed task prompt text
     * @param metadata extracted metadata
     */
    void validate(String processedPromptText, ProcessedPromptMetadata metadata);
}
