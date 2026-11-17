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

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.handler.TargetNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

import java.util.Map;

class NegotiationHandlerStateTest {

    @Test
    void continueMessageRejectsContextRoundMismatch() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        store.save(new NegotiationRecord(
                new NegotiationContext(
                        NegotiationType.TARGET, "neg-continue-mismatch", 2, NegotiationStatus.IN_PROGRESS),
                "old"));
        NegotiationHandler handler = new NegotiationHandler(Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);

        assertThrows(
                NegotiationStateException.class,
                () -> handler.continueMessage(
                        new NegotiationContext(
                                NegotiationType.TARGET,
                                "neg-continue-mismatch",
                                1,
                                NegotiationStatus.IN_PROGRESS),
                        NegotiationStatus.IN_PROGRESS,
                        "Here is the target."));
    }
}
