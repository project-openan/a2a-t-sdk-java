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

import java.util.Objects;
import net.openan.a2at.sdk.core.exception.A2ATError;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Minimal structured result wrapper used by higher-level orchestration flows.
 *
 * @param <T> result value type
 * @since 2026-06
 */
public record OperationResult<T>(@Nullable T value, @Nullable A2ATError error) {

    /**
     * Creates a successful operation result.
     *
     * @param value operation value
     * @param <T> value type
     * @return successful result wrapper
     */
    public static <T> OperationResult<T> success(@NonNull T value) {
        return new OperationResult<>(value, null);
    }

    /**
     * Creates a failed operation result.
     *
     * @param error SDK error describing the failure
     * @param <T> value type
     * @return failed result wrapper
     */
    public static <T> OperationResult<T> failure(@NonNull A2ATError error) {
        return new OperationResult<>(null, Objects.requireNonNull(error, "error"));
    }

    /**
     * Indicates whether the operation completed without an SDK error.
     *
     * @return {@code true} when {@link #error()} is {@code null}
     */
    public boolean isSuccess() {
        return error == null;
    }
}
