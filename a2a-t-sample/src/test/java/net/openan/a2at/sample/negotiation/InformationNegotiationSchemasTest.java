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

package net.openan.a2at.sample.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.openan.a2at.sample.negotiation.shared.InformationNegotiationSchemas;
import org.junit.jupiter.api.Test;

class InformationNegotiationSchemasTest {

    @Test
    void proposeSchemaDefinesRequestedItemsAndTheirRequirements() {
        Map<String, Object> schema = InformationNegotiationSchemas.propose();
        Map<?, ?> properties = propertiesOf(schema);

        assertBaseSchema(schema, List.of("items"));
        assertEquals(Set.of("items", "relationship"), properties.keySet());
        assertItemArray(properties.get("items"), "requirement");
        assertNullableString((Map<?, ?>) properties.get("relationship"));
        assertFalse(properties.containsKey("access_port_name"));
        assertFalse(properties.containsKey("complaint_category"));
    }

    @Test
    void acceptSchemaDefinesItemsAndSuppliedValues() {
        Map<String, Object> schema = InformationNegotiationSchemas.accept();
        Map<?, ?> properties = propertiesOf(schema);

        assertBaseSchema(schema, List.of("items"));
        assertEquals(Set.of("items"), properties.keySet());
        assertItemArray(properties.get("items"), "value");
    }

    @Test
    void rejectSchemaDefinesItemsAndRejectionReasons() {
        Map<String, Object> schema = InformationNegotiationSchemas.reject();
        Map<?, ?> properties = propertiesOf(schema);

        assertBaseSchema(schema, List.of("items"));
        assertEquals(Set.of("items"), properties.keySet());
        assertItemArray(properties.get("items"), "reason");
    }

    @Test
    void phaseSchemasAreIndependentAndDeeplyImmutable() {
        Map<String, Object> propose = InformationNegotiationSchemas.propose();
        Map<String, Object> accept = InformationNegotiationSchemas.accept();
        Map<String, Object> reject = InformationNegotiationSchemas.reject();

        assertNotSame(propose, accept);
        assertNotSame(propose, reject);
        assertNotSame(accept, reject);
        assertFalse(propose.equals(accept));
        assertFalse(propose.equals(reject));
        assertFalse(accept.equals(reject));

        assertImmutable(propose);
        assertImmutable(propertiesOf(propose));
        Map<?, ?> proposeItems = (Map<?, ?>) propertiesOf(propose).get("items");
        assertImmutable(proposeItems);
        assertImmutable((Map<?, ?>) proposeItems.get("items"));
        assertImmutable(propertiesOf((Map<?, ?>) proposeItems.get("items")));
    }

    private static void assertBaseSchema(Map<?, ?> schema, List<String> required) {
        assertEquals("object", schema.get("type"));
        assertEquals(false, schema.get("additionalProperties"));
        assertEquals(required, schema.get("required"));
    }

    private static void assertItemArray(Object rawArray, String valueName) {
        Map<?, ?> array = (Map<?, ?>) rawArray;
        assertEquals("array", array.get("type"));
        assertEquals(1, array.get("minItems"));
        assertEquals("object", ((Map<?, ?>) array.get("items")).get("type"));

        Map<?, ?> itemSchema = (Map<?, ?>) array.get("items");
        assertEquals(false, itemSchema.get("additionalProperties"));
        assertEquals(List.of("name", valueName), itemSchema.get("required"));
        assertEquals(Set.of("name", valueName), propertiesOf(itemSchema).keySet());
        assertEquals("string", ((Map<?, ?>) propertiesOf(itemSchema).get("name")).get("type"));
        assertNullableString((Map<?, ?>) propertiesOf(itemSchema).get(valueName));
    }

    private static void assertNullableString(Map<?, ?> schema) {
        assertEquals(List.of("string", "null"), schema.get("type"));
    }

    private static Map<?, ?> propertiesOf(Map<?, ?> schema) {
        return (Map<?, ?>) schema.get("properties");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertImmutable(Map<?, ?> map) {
        assertThrows(UnsupportedOperationException.class, () -> ((Map) map).put("unexpected", true));
    }
}
