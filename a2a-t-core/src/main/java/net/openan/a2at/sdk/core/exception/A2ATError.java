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

import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Single root exception type for all A2A-T SDK processing failures.
 *
 * <p>Every runtime, environment or data-processing failure raised by the SDK is part of this tree, so callers can catch
 * this one root type and branch on the machine-readable error code returned by {@link #getCode()}, which is never
 * {@code null}. Caller contract violations (null, blank or otherwise malformed arguments) are programming errors raised
 * as {@link NullPointerException} or {@link IllegalArgumentException} and intentionally stay outside this tree.
 *
 * @since 2026-08
 */
public class A2ATError extends RuntimeException {

    /** Machine-readable error code for this failure, never null. */
    private final String code;

    /**
     * Creates an A2A-T processing failure with one message and the default error code.
     *
     * @param message failure message
     */
    public A2ATError(String message) {
        this(ErrorCatalog.INFRA_INTERNAL_ERROR.getCode(), message);
    }

    /**
     * Creates an A2A-T processing failure with one message, one root cause and the default error code.
     *
     * @param message failure message
     * @param cause root cause
     */
    public A2ATError(String message, @Nullable Throwable cause) {
        this(ErrorCatalog.INFRA_INTERNAL_ERROR.getCode(), message, cause);
    }

    /**
     * Creates an A2A-T processing failure with one specific error code and one message.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @throws NullPointerException if {@code code} is null
     */
    public A2ATError(@NonNull String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    /**
     * Creates an A2A-T processing failure with one specific error code, one message and one root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param cause root cause
     * @throws NullPointerException if {@code code} is null
     */
    public A2ATError(@NonNull String code, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    /**
     * Returns the machine-readable error code for this failure.
     *
     * @return error code, never null
     */
    public @NonNull String getCode() {
        return code;
    }
}
