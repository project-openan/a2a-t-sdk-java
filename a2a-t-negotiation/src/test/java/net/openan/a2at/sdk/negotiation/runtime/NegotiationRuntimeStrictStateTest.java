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

import java.util.Map;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.handler.TargetNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationRuntimeStrictStateTest {

    @Test
    void receiveRejectsIncomingRoundOlderThanLocalProgress() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.TARGET, "neg-round-regress", 3, NegotiationStatus.IN_PROGRESS),
                "latest"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);

        NegotiationStateException error = assertThrows(
                NegotiationStateException.class,
                () -> runtime.receive(
                        "stale",
                        new NegotiationContext(
                                NegotiationType.TARGET, "neg-round-regress", 2, NegotiationStatus.IN_PROGRESS)));

        assertEquals("Incoming negotiation round is older than local progress.", error.getMessage());
    }

    @Test
    void receiveRejectsTerminalContextReturningToInProgress() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.TARGET, "neg-terminal-regress", 2, NegotiationStatus.AGREED),
                "done"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);

        assertThrows(
                NegotiationStateException.class,
                () -> runtime.receive(
                        "Clarify intent",
                        new NegotiationContext(
                                NegotiationType.TARGET,
                                "neg-terminal-regress",
                                3,
                                NegotiationStatus.IN_PROGRESS)));
    }

    @Test
    void receiveRejectsChangingOneTerminalStatusToAnother() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.TARGET, "neg-terminal-switch", 2, NegotiationStatus.AGREED),
                "done"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);

        NegotiationStateException error = assertThrows(
                NegotiationStateException.class,
                () -> runtime.receive(
                        "switch terminal",
                        new NegotiationContext(
                                NegotiationType.TARGET, "neg-terminal-switch", 3, NegotiationStatus.REJECTED)));

        assertEquals("Terminal negotiation status cannot change once finalized.", error.getMessage());
    }

    @Test
    void continueMessageRejectsRepeatedTerminalProgression() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.TARGET, "neg-terminal-repeat", 3, NegotiationStatus.REJECTED),
                "done"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);

        assertThrows(
                NegotiationStateException.class,
                () -> runtime.continueMessage(
                        new NegotiationContext(
                                NegotiationType.TARGET, "neg-terminal-repeat", 3, NegotiationStatus.REJECTED),
                        NegotiationStatus.REJECTED));
    }

    @Test
    void continueMessageRejectsContextRoundThatDoesNotMatchStoredProgress() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.TARGET, "neg-continue-mismatch", 3, NegotiationStatus.IN_PROGRESS),
                "latest"));
        NegotiationRuntime runtime = new NegotiationRuntime(
                Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);

        NegotiationStateException error = assertThrows(
                NegotiationStateException.class,
                () -> runtime.continueMessage(
                        new NegotiationContext(
                                NegotiationType.TARGET,
                                "neg-continue-mismatch",
                                2,
                                NegotiationStatus.IN_PROGRESS),
                        NegotiationStatus.IN_PROGRESS));

        assertEquals("Negotiation context does not match stored progress.", error.getMessage());
    }
}
