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

package net.openan.a2at.sample.subscribe_incident.shared.endpoint;

import java.net.URI;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.subscribe_incident.shared.error.ValueErrorException;

/**
 * Resolves the preferred HTTP endpoint from a registry AgentCard payload.
 *
 * @since 2026-05
 */
public final class AgentEndpointResolver {

    private AgentEndpointResolver() {}

    public static ResolvedAgentEndpoint resolvePreferredInterface(Map<String, Object> agentCard) {
        Object supportedInterfacesValue = agentCard.get("supportedInterfaces");
        if (!(supportedInterfacesValue instanceof List<?> supportedInterfaces)) {
            throw new ValueErrorException("AgentCard.supportedInterfaces is required");
        }

        for (Object item : supportedInterfaces) {
            if (!(item instanceof Map<?, ?> interfaceMap)) {
                continue;
            }
            String url = stringValue(interfaceMap.get("url"));
            if (!isValidHttpUrl(url)) {
                throw new ValueErrorException("Invalid supportedInterfaces url: " + url);
            }
            return new ResolvedAgentEndpoint(
                    stringValue(agentCard.get("name")),
                    stringValue(interfaceMap.get("protocolBinding")),
                    stringValue(interfaceMap.get("protocolVersion")),
                    url);
        }

        throw new ValueErrorException("No supported HTTP interface found in AgentCard.supportedInterfaces");
    }

    private static boolean isValidHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
