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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Loads the bundled private-line complaint negotiation input. */
public final class NegotiationScenarioLoader {

    public static final String RESOURCE_PATH = "sample/private-line-complaint-negotiation/scenario.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private NegotiationScenarioLoader() {}

    public static NegotiationScenario load() {
        try (InputStream input =
                NegotiationScenarioLoader.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Negotiation scenario resource not found: " + RESOURCE_PATH);
            }
            Map<String, String> values = OBJECT_MAPPER.readValue(input, new TypeReference<>() {});
            return new NegotiationScenario(
                    require(values, "scenario"),
                    require(values, "propose_text"),
                    require(values, "accept_text"),
                    require(values, "reject_text"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read negotiation scenario", exception);
        }
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Negotiation scenario field must not be blank: " + key);
        }
        return value;
    }
}
