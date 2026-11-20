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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExtensionUriConstants}.
 *
 * <p>Tests cover the extension URI constants and utility class invariants.
 *
 * @since 2026-08
 */
class ExtensionUriConstantsTest {

    @Test
    void should_haveCorrectTaskTExtensionUri() {
        assertEquals(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1",
                ExtensionUriConstants.TASK_T_EXTENSION_URI);
    }

    @Test
    void should_haveCorrectAuthorizationTExtensionUri() {
        assertEquals(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1",
                ExtensionUriConstants.AUTHORIZATION_T_EXTENSION_URI);
    }

    @Test
    void should_haveCorrectNotificationTExtensionUri() {
        assertEquals(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1",
                ExtensionUriConstants.NOTIFICATION_T_EXTENSION_URI);
    }

    @Test
    void should_havePrivateConstructor() throws Exception {
        Constructor<ExtensionUriConstants> constructor =
                ExtensionUriConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(Exception.class, constructor::newInstance);
    }
}