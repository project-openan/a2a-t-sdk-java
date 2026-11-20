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

package net.openan.a2at.sdk.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.prompt.resources.loader.ClasspathPromptSlotSchemaLoader;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import org.junit.jupiter.api.Test;

class AuthorizationSlotSchemaResourceTest {

    @Test
    void should_loadAuthorizationSlotSchemaFromClasspath_ForZhCN() {
        ClasspathPromptSlotSchemaLoader loader =
                new ClasspathPromptSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema schema = loader.loadSlotSchema("authorization-policy-management", "zh-CN");

        assertEquals("authorization-policy-management", schema.scenarioCode());
        assertEquals(2, schema.slotDefinitions().size());
        assertTrue(schema.slotDefinitions().get(0).required());
        assertEquals("授权策略的操作类型", schema.slotDefinitions().get(0).name());
    }

    @Test
    void should_loadAuthorizationSlotSchema_ForEnUS() {
        ClasspathPromptSlotSchemaLoader loader =
                new ClasspathPromptSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema schema = loader.loadSlotSchema("authorization-policy-management", "en-US");

        assertEquals("authorization-policy-management", schema.scenarioCode());
        assertEquals(2, schema.slotDefinitions().size());
        assertTrue(schema.slotDefinitions().get(0).required());
        assertEquals(
                "authorization_policy_operation_type",
                schema.slotDefinitions().get(0).name());
    }

    @Test
    void should_haveOperationTypeOnlyInRequired() {
        ClasspathPromptSlotSchemaLoader loader =
                new ClasspathPromptSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema schema = loader.loadSlotSchema("authorization-policy-management", "zh-CN");

        long requiredCount = schema.slotDefinitions().stream()
                .filter(PromptSlotDefinition::required)
                .count();
        assertEquals(1, requiredCount);
        assertEquals("授权策略的操作类型", schema.slotDefinitions().get(0).name());
        assertFalse(schema.slotDefinitions().get(1).required());
    }

    @Test
    void should_havePropertyNamesMatchingTemplatePlaceholders() {
        ClasspathPromptSlotSchemaLoader loader =
                new ClasspathPromptSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema zhSchema = loader.loadSlotSchema("authorization-policy-management", "zh-CN");
        PromptSlotSchema enSchema = loader.loadSlotSchema("authorization-policy-management", "en-US");

        assertEquals("授权策略的操作类型", zhSchema.slotDefinitions().get(0).name());
        assertEquals("动网操作的授权策略列表", zhSchema.slotDefinitions().get(1).name());
        assertEquals(
                "authorization_policy_operation_type",
                enSchema.slotDefinitions().get(0).name());
        assertEquals(
                "network_operation_authorization_policy_list",
                enSchema.slotDefinitions().get(1).name());
    }

    @Test
    void should_haveSchemaStructureParity() {
        ClasspathPromptSlotSchemaLoader loader =
                new ClasspathPromptSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema authSchema = loader.loadSlotSchema("authorization-policy-management", "zh-CN");
        PromptSlotSchema energySchema = loader.loadSlotSchema("ran-energy-saving", "zh-CN");

        assertFalse(authSchema.slotDefinitions().isEmpty());
        assertFalse(energySchema.slotDefinitions().isEmpty());

        for (PromptSlotDefinition def : authSchema.slotDefinitions()) {
            assertEquals("string", def.jsonType());
            assertFalse(def.description().isBlank());
            assertFalse(def.valueConstraint().isBlank());
        }
    }

    @Test
    void should_throwResourceNotFoundException_WhenSlotSchemaMissing() {
        ClasspathPromptSlotSchemaLoader loader =
                new ClasspathPromptSlotSchemaLoader(new ClasspathPromptResourceLoader());

        assertThrows(ResourceNotFoundException.class, () -> loader.loadSlotSchema("nonexistent-scenario", "zh-CN"));
    }
}
