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

package net.openan.a2at.sample.subscribe_incident.server.agentcard;

import java.util.List;
import java.util.Map;

/**
 * Builds sample-owned AgentCard payloads for the server demo.
 *
 * @since 2026-05
 */
public final class ServerSampleAgentCardBuilder {
    static final String TASK_T_EXTENSION_URI_NL =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/NL/v1";

    static final String NOTIFICATION_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1";

    private ServerSampleAgentCardBuilder() {}

    public static Map<String, Object> buildAgentCard(String host, int port) {
        return Map.of(
                "name", "SPN Domain Agent",
                "description", "SPN Domain Agent",
                "version", "1.0.0",
                "defaultInputModes", List.of("application/json", "text/plain"),
                "defaultOutputModes", List.of("application/json", "text/plain"),
                "provider",
                        Map.of(
                                "organization", "Huawei",
                                "url", "https://www.huawei.com"),
                "skills",
                        List.of(Map.of(
                                "id", "Incident-Subscription",
                                "name", "Incident reporting",
                                "description", "Mock incident reporting sample skill",
                                "tags", List.of("incident", "reporting"))),
                "capabilities",
                        Map.of(
                                "streaming", true,
                                "pushNotifications", false,
                                "extensions",
                                        List.of(
                                                Map.of(
                                                        "uri",
                                                        TASK_T_EXTENSION_URI_NL,
                                                        "description",
                                                        "Extension of structured prompt Task-T requests."),
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

    public static Map<String, Object> buildRegistrationPayload(String host, int port) {
        return Map.of("agentCards", List.of(buildAgentCard(host, port)));
    }
}
