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

package net.openan.a2at.sample.service_recovery.server.flow;

import net.openan.a2at.sdk.core.model.FilledParamData;

/**
 * Sample-owned bridge for validating one rendered notification prompt with the server SDK.
 *
 * <p>The bridge wraps {@code A2ATServer.validateAndFillingNotificationData} with the fixed service-recovery parameter
 * schema and template URI; a validation failure surfaces as
 * {@link net.openan.a2at.sdk.core.validation.ContentValidationException}.
 *
 * @since 2026-08
 */
@FunctionalInterface
public interface NotificationPromptValidator {

    /**
     * Validates one rendered notification prompt and extracts its subscription parameters.
     *
     * @param promptText rendered notification prompt text received from the client
     * @return filled parameter data carrying the extracted subscription parameters
     */
    FilledParamData validateNotificationPrompt(String promptText);
}
