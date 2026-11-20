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

package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PromptMessage}.
 *
 * <p>Tests cover the construction of chat-style prompt messages
 * with role and content fields.
 *
 * @since 2026-06
 */
class PromptMessageTest {

    /**
     * Verifies that {@link PromptMessage} can be constructed with a system role.
     *
     * <p>Scenario: Create a system message with role "system" and assistant instructions.
     * Expected result: role() returns "system", content() returns the message content.
     */
    @Test
    void should_createMessageWithRoleAndContent_When_constructorCalledWithSystemRole() {
        PromptMessage message = new PromptMessage("system", "You are a helpful assistant.");

        assertEquals("system", message.role());
        assertEquals("You are a helpful assistant.", message.content());
    }

    /**
     * Verifies that {@link PromptMessage} can be constructed with a user role.
     *
     * <p>Scenario: Create a user message with role "user" and user input.
     * Expected result: role() returns "user", content() returns the message content.
     */
    @Test
    void should_createUserMessage_When_constructorCalledWithUserRole() {
        PromptMessage message = new PromptMessage("user", "Hello, how can I help?");

        assertEquals("user", message.role());
        assertEquals("Hello, how can I help?", message.content());
    }
}