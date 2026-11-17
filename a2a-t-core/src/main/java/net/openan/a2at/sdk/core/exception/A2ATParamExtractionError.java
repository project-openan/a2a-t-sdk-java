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

/**
 * Shared failure type raised when validating a prompt and extracting parameters from it fails.
 *
 * <p>This base type is shared across prompt families so that a caller can handle extraction failures uniformly. The
 * machine-readable error code is carried by the root {@link A2ATError} and is never null; subclasses pass a more
 * specific code when one is available.
 *
 * @since 2026-08
 */
public class A2ATParamExtractionError extends A2ATBusinessException {

    /** Structured per-slot validation error details. */
    private final List<SlotValidationError> errors;

    /**
     * Creates a parameter-extraction failure with the default error code and no slot details.
     *
     * @param message failure message
     */
    public A2ATParamExtractionError(String message) {
        this(ErrorCatalog.SLOT_NOT_PROVIDED.getCode(), message, List.of());
    }

    /**
     * Creates a parameter-extraction failure with one specific error code and slot details.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     */
    public A2ATParamExtractionError(@NonNull String code, String message, @NonNull List<SlotValidationError> errors) {
        super(code, message);
        this.errors = List.copyOf(errors);
    }

    /**
     * Creates a parameter-extraction failure with one specific error code, slot details and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param errors structured per-slot validation error details
     * @param cause root cause of the failure
     */
    public A2ATParamExtractionError(
            @NonNull String code, String message, @NonNull List<SlotValidationError> errors, Throwable cause) {
        super(code, message, null, cause);
        this.errors = List.copyOf(errors);
    }

    /**
     * Returns the structured per-slot validation error details.
     *
     * @return immutable list of slot validation errors, never null
     */
    public @NonNull List<SlotValidationError> getErrors() {
        return errors;
    }
}
