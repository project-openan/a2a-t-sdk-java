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

package net.openan.a2at.sample.service_recovery.server.agentcard;

import java.util.List;
import java.util.Map;

/**
 * Builds sample-owned AgentCard payloads for the service-recovery server demo.
 *
 * @since 2026-08
 */
public final class ServerSampleAgentCardBuilder {
    static final String NOTIFICATION_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1";

    private ServerSampleAgentCardBuilder() {}

    /**
     * Builds the sample AgentCard payload for one bind address.
     *
     * @param host bind host
     * @param port bind port
     * @return AgentCard payload map
     */
    public static Map<String, Object> buildAgentCard(String host, int port) {
        return Map.of(
                "name", "SPN Service Recovery Agent",
                "description", "SPN Service Recovery Agent",
                "version", "1.0.0",
                "defaultInputModes", List.of("application/json", "text/plain"),
                "defaultOutputModes", List.of("application/json", "text/plain"),
                "provider",
                        Map.of(
                                "organization", "Huawei",
                                "url", "https://www.huawei.com"),
                "skills",
                        List.of(Map.of(
                                "id", "Service-Recovery-Subscription",
                                "name", "Service recovery event reporting",
                                "description", "Mock service recovery event reporting sample skill",
                                "tags", List.of("service-recovery", "reporting"))),
                "capabilities",
                        Map.of(
                                "streaming", true,
                                "pushNotifications", false,
                                "extensions",
                                        List.of(
                                                Map.of(
                                                        "uri",
                                                        NOTIFICATION_T_EXTENSION_URI,
                                                        "description",
                                                        "Extension of structured prompt Notification-T requests."))),
                "supportedInterfaces",
                        List.of(Map.of(
                                "protocolBinding", "HTTP+JSON",
                                "protocolVersion", "1.0",
                                "url", "http://" + host + ":" + port)));
    }

    /**
     * Builds the registry registration payload wrapping the sample AgentCard.
     *
     * @param host bind host
     * @param port bind port
     * @return registration payload map
     */
    public static Map<String, Object> buildRegistrationPayload(String host, int port) {
        return Map.of("agentCards", List.of(buildAgentCard(host, port)));
    }
}
