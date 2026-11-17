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

package net.openan.a2at.sdk.llm.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import org.junit.jupiter.api.Test;

class LlmClientContractTest {

    @Test
    void llmClientIsProviderFacingInterface() {
        assertTrue(LLMClient.class.isInterface());
        assertFalse(Modifier.isFinal(LLMClient.class.getModifiers()));
    }

    @Test
    void llmClientExposesStructuredMethodWithSimplifiedSignature() throws Exception {
        Class<?> responseType = assertDoesNotThrow(() -> Class.forName("net.openan.a2at.sdk.llm.LLMResponse"));

        Method method = LLMClient.class.getMethod("structured", List.class, Map.class, Double.class, Integer.class);

        assertEquals(responseType, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
    }
}
