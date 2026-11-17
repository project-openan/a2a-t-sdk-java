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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.core.exception.A2ATError;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OperationResult}.
 *
 * <p>Tests cover the following scenarios:
 * <ul>
 *   <li>Creating successful results with values</li>
 *   <li>Creating failure results with SDK errors</li>
 *   <li>Null safety for failure result errors</li>
 * </ul>
 *
 * @since 2026-06
 */
class OperationResultTest {

    /**
     * Verifies that {@link OperationResult#success(Object)} creates a result
     * containing a value without an error.
     *
     * <p>Scenario: Create a successful result with a string value.
     * Expected result: isSuccess() returns true, value() returns the value, error() returns null.
     */
    @Test
    void should_carryValueWithoutError_When_successResultCreated() {
        OperationResult<String> result = OperationResult.success("ok");

        assertTrue(result.isSuccess());
        assertEquals("ok", result.value());
        assertNull(result.error());
    }

    /**
     * Verifies that {@link OperationResult#failure(A2ATError)} creates a result
     * containing an error without a value.
     *
     * <p>Scenario: Create a failure result with an A2ATError.
     * Expected result: isSuccess() returns false, value() returns null, error() returns the error.
     */
    @Test
    void should_carryErrorWithoutValue_When_failureResultCreated() {
        A2ATError error = new A2ATError("operation failed");

        OperationResult<String> result = OperationResult.failure(error);

        assertFalse(result.isSuccess());
        assertNull(result.value());
        assertEquals(error, result.error());
    }

    /**
     * Verifies that {@link OperationResult#failure(A2ATError)} rejects null errors.
     *
     * <p>Scenario: Attempt to create a failure result with a null error.
     * Expected result: NullPointerException is thrown.
     */
    @Test
    void should_throwNullPointerException_When_failureResultCreatedWithNullError() {
        assertThrows(NullPointerException.class, () -> OperationResult.failure(null));
    }
}
