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

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NegotiationConfig}.
 *
 * <p>Tests cover the following scenarios:
 * <ul>
 *   <li>Default state store type when configuration key is missing</li>
 *   <li>Overriding default with environment variable value</li>
 * </ul>
 *
 * @since 2026-06
 */
class NegotiationConfigTest {

    /**
     * Verifies that {@link NegotiationConfig#fromMap(Map)} uses the default
     * "in_memory" state store type when the key is missing.
     *
     * <p>Scenario: An empty map is passed to fromMap().
     * Expected result: stateStoreType() returns "in_memory".
     */
    @Test
    void should_useDefaultStateStoreType_When_keyIsMissing() {
        Map<String, String> values = Map.of();

        NegotiationConfig config = NegotiationConfig.fromMap(values);

        assertEquals("in_memory", config.stateStoreType());
    }

    /**
     * Verifies that {@link NegotiationConfig#fromMap(Map)} overrides the default
     * value when the key is provided.
     *
     * <p>Scenario: A map contains A2AT_NEGOTIATION_STATE_STORE_TYPE="redis".
     * Expected result: stateStoreType() returns "redis".
     */
    @Test
    void should_overrideDefault_When_keyIsProvided() {
        Map<String, String> values = Map.of("A2AT_NEGOTIATION_STATE_STORE_TYPE", "redis");

        NegotiationConfig config = NegotiationConfig.fromMap(values);

        assertEquals("redis", config.stateStoreType());
    }
}