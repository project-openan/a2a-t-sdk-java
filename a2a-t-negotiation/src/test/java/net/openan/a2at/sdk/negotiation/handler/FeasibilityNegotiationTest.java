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

package net.openan.a2at.sdk.negotiation.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class FeasibilityNegotiationTest {

    private final FeasibilityNegotiation negotiationType = new FeasibilityNegotiation();

    @Test
    void processReceivedMessagePassesThroughFactsAndMessage() {
        NegotiationReceiveResult result = negotiationType.processReceivedMessage(
                "check whether this task is feasible",
                new NegotiationContext(
                        NegotiationType.FEASIBILITY, "neg-feasibility", 1, NegotiationStatus.IN_PROGRESS));

        assertTrue(result.needResponse());
        assertEquals("check whether this task is feasible", result.message());
        assertTrue(result.facts().isEmpty());
    }

    @Test
    void processReceivedMessageStopsResponseWhenNegotiationIsTerminal() {
        NegotiationReceiveResult result = negotiationType.processReceivedMessage(
                "feasibility reached",
                new NegotiationContext(NegotiationType.FEASIBILITY, "neg-feasibility", 2, NegotiationStatus.AGREED));

        assertFalse(result.needResponse());
        assertEquals("feasibility reached", result.message());
    }
}
