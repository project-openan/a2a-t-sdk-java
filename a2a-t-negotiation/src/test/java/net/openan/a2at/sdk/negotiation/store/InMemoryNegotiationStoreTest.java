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

package net.openan.a2at.sdk.negotiation.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class InMemoryNegotiationStoreTest {

    @Test
    void storeSavesGetsAndDeletesRecords() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationRecord record = new NegotiationRecord(
                new NegotiationContext(NegotiationType.TARGET, "neg-store", 1, NegotiationStatus.IN_PROGRESS),
                "message");

        store.save(record);

        assertEquals(record, store.get("neg-store"));

        store.delete("neg-store");

        assertNull(store.get("neg-store"));
    }

    @Test
    void saveRejectsNullRecordAndNullContext() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();

        assertEquals(
                "Negotiation record must not be null.",
                assertThrows(NullPointerException.class, () -> store.save(null)).getMessage());
        assertEquals(
                "Negotiation record context must not be null.",
                assertThrows(NullPointerException.class, () -> store.save(new NegotiationRecord(null, "message")))
                        .getMessage());
    }

    @Test
    void saveRejectsMissingNegotiationId() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();

        IllegalArgumentException missingId = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(new NegotiationRecord(
                        new NegotiationContext(NegotiationType.TARGET, null, 1, NegotiationStatus.IN_PROGRESS),
                        "message")));

        assertEquals("negotiation id is null or empty.", missingId.getMessage());

        IllegalArgumentException emptyId = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(new NegotiationRecord(
                        new NegotiationContext(NegotiationType.TARGET, "", 1, NegotiationStatus.IN_PROGRESS),
                        "message")));

        assertEquals("negotiation id is null or empty.", emptyId.getMessage());
    }
}
