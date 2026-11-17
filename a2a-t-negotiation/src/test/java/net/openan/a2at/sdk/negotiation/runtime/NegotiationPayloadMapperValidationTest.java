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

package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.runtime.helper.NegotiationPayloadMapper;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import org.junit.jupiter.api.Test;

class NegotiationPayloadMapperValidationTest {

    @Test
    void contextFromMapRejectsNonPositiveRound() {
        assertThrows(
                NegotiationStateException.class,
                () -> NegotiationPayloadMapper.contextFromMap(Map.of(
                        "negotiationType", "target",
                        "negotiationId", "neg-invalid-round",
                        "round", 0,
                        "status", "in-progress",
                        "extra", Map.of())));
    }
}
