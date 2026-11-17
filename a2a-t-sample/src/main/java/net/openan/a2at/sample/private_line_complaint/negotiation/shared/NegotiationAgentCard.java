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

package net.openan.a2at.sample.private_line_complaint.negotiation.shared;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;

/** AgentCard payload shared by the negotiation sample client and server. */
public final class NegotiationAgentCard {

    private NegotiationAgentCard() {}

    public static Map<String, Object> build(String host, int port) {
        return Map.of(
                "name", "Transmission Workbench Negotiation Agent",
                "description", "Private-line complaint information negotiation sample",
                "version", "1.0.0",
                "defaultInputModes", List.of("text/plain"),
                "defaultOutputModes", List.of("text/plain"),
                "provider", Map.of("organization", "OpenAN", "url", "https://github.com/project-openan"),
                "skills",
                        List.of(Map.of(
                                "id", "private-line-complaint-negotiation",
                                "name", "Private-line complaint negotiation",
                                "description", "Completes an information negotiation with Accept or Reject.",
                                "tags", List.of("negotiation", "private-line"))),
                "capabilities",
                        Map.of(
                                "streaming", true,
                                "pushNotifications", false,
                                "extensions",
                                        List.of(Map.of(
                                                "uri",
                                                ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI,
                                                "description",
                                                "Negotiation-T information negotiation extension."))),
                "supportedInterfaces",
                        List.of(Map.of(
                                "protocolBinding", "HTTP+JSON",
                                "protocolVersion", "1.0",
                                "url", "http://" + host + ":" + port)));
    }
}
