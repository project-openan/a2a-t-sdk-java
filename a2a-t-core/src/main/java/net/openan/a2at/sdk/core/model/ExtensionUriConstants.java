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

/**
 * Extension URI constants for the A2A-T protocol.
 *
 * <p>These URIs identify the A2A-T extension protocols supported by this SDK.
 *
 * @since 2026-08
 */
public final class ExtensionUriConstants {

    private ExtensionUriConstants() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** URI for the Task-T extension. */
    public static final String TASK_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1";

    /** URI for the Authorization-T extension. */
    public static final String AUTHORIZATION_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1";

    /** URI for the Notification-T extension. */
    public static final String NOTIFICATION_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1";

    /**
     * Canonical URI for the Negotiation-T extension.
     *
     * <p>This is the URI under which generated negotiation messages travel in A2A-T metadata.
     */
    public static final String NEGOTIATION_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1";

    /**
     * Legacy alias URI for the Negotiation-T extension.
     *
     * <p>Kept for runtime compatibility reads of messages emitted under the NL naming; new metadata emission uses
     * {@link #NEGOTIATION_T_EXTENSION_URI}.
     */
    public static final String NEGOTIATION_T_EXTENSION_URI_NL =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/NL/v1";
}