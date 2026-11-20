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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/** Writes one JSON object per invocation stage so a failed evaluation can be diagnosed without rerunning the model. */
final class NegotiationEvaluationProcessLogger implements AutoCloseable {

    private final ObjectMapper objectMapper;
    private final BufferedWriter writer;

    NegotiationEvaluationProcessLogger(ObjectMapper objectMapper, Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.objectMapper = objectMapper.copy().disable(SerializationFeature.INDENT_OUTPUT);
        this.writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    void write(Map<String, Object> event) throws IOException {
        writer.write(objectMapper.writeValueAsString(event));
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
