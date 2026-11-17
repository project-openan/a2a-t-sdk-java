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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.handler.TargetNegotiation;
import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationHandlerTest {

    @Test
    void startReturnsFixedKeyMapAndSavesRecord() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationHandler handler =
                new NegotiationHandler(Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);

        Map<String, Object> payload = handler.start(
                NegotiationType.TARGET,
                "Please clarify the request.",
                Map.of("targetItems", new Object[] {"intent"}));

        Map<String, Object> negotiationData = cast(payload.get(NegotiationHandler.NEGOTIATION_T_URI_NL));
        assertEquals("Please clarify the request.", negotiationData.get("message"));
        assertNotNull(negotiationData.get("negotiationId"));
        assertNotNull(store.get(stringValue(negotiationData.get("negotiationId"))));
    }

    @Test
    void continueMessageReturnsPayloadWithIncrementedRound() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationHandler handler =
                new NegotiationHandler(Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);
        Map<String, Object> startPayload =
                handler.start(NegotiationType.TARGET, "Please clarify the request.", Map.of());
        Map<String, Object> startData = cast(startPayload.get(NegotiationHandler.NEGOTIATION_T_URI_NL));
        NegotiationContext context = new NegotiationContext(
                NegotiationType.TARGET,
                stringValue(startData.get("negotiationId")),
                numberValue(startData.get("round")).intValue(),
                NegotiationStatus.IN_PROGRESS);

        Map<String, Object> payload =
                handler.continueMessage(context, NegotiationStatus.IN_PROGRESS, "Here is the target.");

        Map<String, Object> nextData = cast(payload.get(NegotiationHandler.NEGOTIATION_T_URI_NL));
        assertEquals(2, numberValue(nextData.get("round")).intValue());
        assertEquals("Here is the target.", nextData.get("message"));
    }

    @Test
    void receiveReturnsNegotiationPayloadMap() {
        InMemoryNegotiationStore store = new InMemoryNegotiationStore();
        NegotiationHandler handler =
                new NegotiationHandler(Map.of(NegotiationType.TARGET, new TargetNegotiation()), store);
        Map<String, Object> startPayload = handler.start(NegotiationType.TARGET, "Please clarify", Map.of());
        Map<String, Object> context = cast(startPayload.get(NegotiationHandler.NEGOTIATION_T_URI_NL));

        Map<String, Object> result = handler.receive("Clarify intent", context);

        assertTrue(booleanValue(result.get("needResponse")));
        assertEquals("Clarify intent", result.get("message"));
    }

    private static Map<String, Object> cast(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> stringValue(entry.getKey()), Map.Entry::getValue));
        }
        throw new AssertionError("Expected map value but was: " + value);
    }

    private static String stringValue(Object value) {
        if (value instanceof String text) {
            return text;
        }
        throw new AssertionError("Expected string value but was: " + value);
    }

    private static Number numberValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new AssertionError("Expected number value but was: " + value);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new AssertionError("Expected boolean value but was: " + value);
    }
}
