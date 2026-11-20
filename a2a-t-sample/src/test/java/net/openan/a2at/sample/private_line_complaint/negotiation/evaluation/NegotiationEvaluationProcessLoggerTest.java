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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NegotiationEvaluationProcessLoggerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesOneCompactJsonObjectPerLine() throws Exception {
        Path log = temporaryDirectory.resolve("process.jsonl");
        try (NegotiationEvaluationProcessLogger logger = new NegotiationEvaluationProcessLogger(OBJECT_MAPPER, log)) {
            logger.write(Map.of("stage", "generate", "request", Map.of("text", "first")));
            logger.write(Map.of("stage", "validate_and_fill", "request", Map.of("text", "second")));
        }

        List<String> lines = Files.readAllLines(log);
        assertEquals(2, lines.size());
        assertFalse(lines.stream().anyMatch(String::isBlank));
        assertEquals(
                "generate", OBJECT_MAPPER.readTree(lines.get(0)).get("stage").asText());
        assertEquals(
                "validate_and_fill",
                OBJECT_MAPPER.readTree(lines.get(1)).get("stage").asText());
    }
}
