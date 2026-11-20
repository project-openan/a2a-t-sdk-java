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

package net.openan.a2at.sample.service_recovery.shared.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.service_recovery.shared.error.ValueErrorException;
import org.junit.jupiter.api.Test;

class SampleMockLLMClientTest {

    @Test
    void responseResourceReturnsSlotExtractionWhenSchemaHasSlotNames() {
        Map<String, Object> schema = Map.of("slotNames", List.of("topic"));

        String resource = SampleMockLLMClient.responseResource(schema);

        assertEquals("slot_extraction.json", resource);
    }

    @Test
    void responseResourceReturnsContentValidationWhenSchemaHasSemanticVerdict() {
        Map<String, Object> schema = Map.of("required", List.of("semantic_verdict"));

        String resource = SampleMockLLMClient.responseResource(schema);

        assertEquals("content_validation.json", resource);
    }

    @Test
    void responseResourceRaisesForUnknownSchema() {
        Map<String, Object> schema = Map.of("type", "object");

        assertThrows(ValueErrorException.class, () -> SampleMockLLMClient.responseResource(schema));
    }

    @Test
    void responseResourceRaisesForNullSchema() {
        assertThrows(ValueErrorException.class, () -> SampleMockLLMClient.responseResource(null));
    }
}
