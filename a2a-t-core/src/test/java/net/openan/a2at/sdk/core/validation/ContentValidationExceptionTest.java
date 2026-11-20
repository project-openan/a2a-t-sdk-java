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

package net.openan.a2at.sdk.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards {@link ContentValidationException} param/error handling against the shapes produced by the semantic
 * validator: a {@code null} params map, a params map carrying {@code null} values (the semantic validator emits
 * {@code null} for slots it could not extract), and a {@code null} error list must all be accepted defensively.
 */
class ContentValidationExceptionTest {

    @Test
    void should_AcceptNullParamsAndErrors() {
        ContentValidationException exception = new ContentValidationException("code", "message", null, null, null);

        assertTrue(exception.params().isEmpty());
        assertTrue(exception.errors().isEmpty());
    }

    @Test
    void should_AcceptParamsWithNullValues_FromPartialExtraction() {
        Map<String, Object> partial = new HashMap<>();
        partial.put("accessPort", null); // slot not extractable -> null per the semantic validator contract
        partial.put("bizScenario", "专线中断");

        ContentValidationException exception =
                new ContentValidationException("code", "message", List.of(), partial, null);

        assertEquals(2, exception.params().size());
        assertNull(exception.params().get("accessPort"));
        assertEquals("专线中断", exception.params().get("bizScenario"));
    }

    @Test
    void should_ExposeParamsAndErrorsAsUnmodifiableCopies() {
        Map<String, Object> partial = new HashMap<>();
        partial.put("accessPort", "P781-珠江新城-PTN7900-23-TPA1EG24-17");
        ContentValidationException exception =
                new ContentValidationException("code", "message", List.of(), partial, null);

        assertThrows(UnsupportedOperationException.class, () -> exception.params().put("k", "v"));
        assertThrows(UnsupportedOperationException.class, () -> exception.errors().add(null));
    }
}