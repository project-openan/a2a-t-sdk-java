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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InputLimitConfig} parsing and length-guard helpers.
 *
 * @since 2026-08
 */
class InputLimitConfigTest {

    @Test
    void fromMapFallsBackToDefaultWhenKeyAbsent() {
        InputLimitConfig config = InputLimitConfig.fromMap(Map.of());

        assertEquals(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS, config.maxTextChars());
        assertEquals(16384, config.maxTextChars());
    }

    @Test
    void fromMapParsesConfiguredValue() {
        InputLimitConfig config =
                InputLimitConfig.fromMap(Map.of(A2ATConfigKeys.Input.MAX_TEXT_CHARS, " 2048 "));

        assertEquals(2048, config.maxTextChars());
    }

    @Test
    void fromMapFallsBackToDefaultOnNonNumericValue() {
        InputLimitConfig config =
                InputLimitConfig.fromMap(Map.of(A2ATConfigKeys.Input.MAX_TEXT_CHARS, "not-a-number"));

        assertEquals(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS, config.maxTextChars());
    }

    @Test
    void fromMapFallsBackToDefaultOnNonPositiveValue() {
        InputLimitConfig config =
                InputLimitConfig.fromMap(Map.of(A2ATConfigKeys.Input.MAX_TEXT_CHARS, "0"));

        assertEquals(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS, config.maxTextChars());
    }

    @Test
    void fromMapFallsBackToDefaultOnBlankValue() {
        InputLimitConfig config = InputLimitConfig.fromMap(Map.of(A2ATConfigKeys.Input.MAX_TEXT_CHARS, "  "));

        assertEquals(InputLimitConfig.DEFAULT_MAX_TEXT_CHARS, config.maxTextChars());
    }

    @Test
    void constructorRejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new InputLimitConfig(0));
        assertThrows(IllegalArgumentException.class, () -> new InputLimitConfig(-1));
    }

    @Test
    void isTooLongComparesByLengthAgainstLimit() {
        assertTrue(InputLimitConfig.isTooLong("a".repeat(101), 100));
        assertFalse(InputLimitConfig.isTooLong("a".repeat(100), 100));
        assertFalse(InputLimitConfig.isTooLong("", 100));
        assertFalse(InputLimitConfig.isTooLong(null, 100));
    }

    @Test
    void violationMessageStatesLengthLimitAndKey() {
        String message = InputLimitConfig.violationMessage("a".repeat(13000), 16384);

        assertTrue(message.contains("13000"));
        assertTrue(message.contains("16384"));
        assertTrue(message.contains(A2ATConfigKeys.Input.MAX_TEXT_CHARS));
    }
}
