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

package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads deterministic abnormal-input cases packaged with the private-line sample. */
public final class NegotiationAbnormalEvaluationCaseLoader {

    public static final String RESOURCE_PATH =
            "sample/private-line-complaint-negotiation/evaluation/abnormal-cases.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private NegotiationAbnormalEvaluationCaseLoader() {}

    public static List<NegotiationAbnormalEvaluationCase> load() {
        try (InputStream input =
                NegotiationAbnormalEvaluationCaseLoader.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Abnormal negotiation cases not found: " + RESOURCE_PATH);
            }
            return List.copyOf(OBJECT_MAPPER.readValue(input, new TypeReference<>() {}));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read abnormal negotiation cases", exception);
        }
    }
}
