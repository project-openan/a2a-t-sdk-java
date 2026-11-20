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

package net.openan.a2at.sample.subscribe_incident.shared.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import net.openan.a2at.sample.subscribe_incident.shared.error.ValueErrorException;

/**
 * Loads static sample scenarios from classpath resources.
 *
 * @since 2026-05
 */
public final class SampleScenarioLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SampleScenarioLoader() {}

    public static Map<String, Object> loadClasspathScenario(String resourcePath) {
        try (InputStream inputStream =
                SampleScenarioLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new ValueErrorException("Scenario resource not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new ValueErrorException("Unable to load scenario resource: " + resourcePath);
        }
    }
}
