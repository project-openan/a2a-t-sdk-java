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

package net.openan.a2at.sdk.core.exception;

import java.util.List;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Unified exception type for MetadataContent pipeline failures.
 *
 * <p>The machine-readable error code is carried by the root {@link A2ATError} and is never null.
 *
 * @since 2026-08
 */
public final class PromptGenerationException extends A2ATBusinessException {

    private final List<SlotValidationError> failedParameters;

    /**
     * Creates an exception with a stable error code and a human-readable message.
     *
     * @param code stable error code
     * @param message human-readable failure description
     * @throws NullPointerException if {@code code} is null
     */
    public PromptGenerationException(@NonNull String code, String message) {
        super(code, message);
        this.failedParameters = List.of();
    }

    /**
     * Creates an exception with a stable error code, a human-readable message, and a list of failed parameters.
     *
     * @param code stable error code
     * @param message human-readable failure description
     * @param failedParameters list of failed slot validations
     * @throws NullPointerException if {@code code} is null
     */
    public PromptGenerationException(
            @NonNull String code, String message, @Nullable List<SlotValidationError> failedParameters) {
        super(code, message);
        this.failedParameters = failedParameters == null ? List.of() : List.copyOf(failedParameters);
    }

    /**
     * Creates an exception with a stable error code, a human-readable message, and a root cause.
     *
     * @param code stable error code
     * @param message human-readable failure description
     * @param cause root cause
     * @throws NullPointerException if {@code code} is null
     */
    public PromptGenerationException(@NonNull String code, String message, @Nullable Throwable cause) {
        super(code, message, null, cause);
        this.failedParameters = List.of();
    }

    /**
     * Returns the list of failed parameters, or an empty list when not applicable.
     *
     * @return failed parameters
     */
    public @NonNull List<SlotValidationError> failedParameters() {
        return failedParameters;
    }
}
