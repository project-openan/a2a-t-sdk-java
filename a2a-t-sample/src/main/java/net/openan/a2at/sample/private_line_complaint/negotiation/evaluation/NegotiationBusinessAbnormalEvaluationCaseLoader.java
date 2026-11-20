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

/** Loads business-level malformed-content cases bundled with the sample. */
final class NegotiationBusinessAbnormalEvaluationCaseLoader {

    private static final String RESOURCE =
            "/sample/private-line-complaint-negotiation/evaluation/business-abnormal-cases.json";

    private NegotiationBusinessAbnormalEvaluationCaseLoader() {}

    static List<NegotiationBusinessAbnormalEvaluationCase> load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = NegotiationBusinessAbnormalEvaluationCaseLoader.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Business abnormal case resource not found: " + RESOURCE);
            }
            return mapper.readValue(input, new TypeReference<>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load business abnormal cases: " + RESOURCE, exception);
        }
    }
}
