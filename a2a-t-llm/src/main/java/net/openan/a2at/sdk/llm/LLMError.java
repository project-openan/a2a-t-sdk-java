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

package net.openan.a2at.sdk.llm;

import net.openan.a2at.sdk.core.exception.A2ATError;

/**
 * Base unchecked error for LLM integration failures.
 *
 * <p>LLM failures are part of the A2A-T processing-failure tree: catching {@link A2ATError} also catches this error and
 * its subclasses ({@link LLMConfigError}, {@link LLMRuntimeError}).
 *
 * @since 2026-06
 */
public class LLMError extends A2ATError {

    public LLMError(String message) {
        super(message);
    }

    public LLMError(String message, Throwable cause) {
        super(message, cause);
    }
}
