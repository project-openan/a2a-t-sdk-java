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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.handler.TargetNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationRuntimeTest {

    @Test
    void receiveAllowsFirstRoundWithoutExistingRecord() {
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.TARGET, new TargetNegotiation()),
                new InMemoryNegotiationStore());

        NegotiationReceiveResult result = runtime.receive(
                "Clarify intent",
                new NegotiationContext(NegotiationType.TARGET, "neg-receive", 1, NegotiationStatus.IN_PROGRESS));

        assertTrue(result.needResponse());
        assertEquals("Clarify intent", result.message());
        assertTrue(result.facts().isEmpty());
    }

    @Test
    void continueMessageReturnsIncrementedRound() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);
        NegotiationContext context =
                new NegotiationContext(NegotiationType.TARGET, "neg-continue", 1, NegotiationStatus.IN_PROGRESS);
        store.save(new NegotiationRecord(context, "old"));

        NegotiationContext nextContext = runtime.continueMessage(context, NegotiationStatus.IN_PROGRESS);

        assertEquals(2, nextContext.round());
        assertEquals(NegotiationStatus.IN_PROGRESS, nextContext.status());
    }

    @Test
    void receiveRejectsIncomingRoundThatSkipsLocalProgress() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(NegotiationType.TARGET, "neg-skip", 2, NegotiationStatus.IN_PROGRESS),
                "old"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);

        assertThrows(
                NegotiationStateException.class,
                () -> runtime.receive(
                        "Clarify intent",
                        new NegotiationContext(
                                NegotiationType.TARGET, "neg-skip", 4, NegotiationStatus.IN_PROGRESS)));
    }
}
