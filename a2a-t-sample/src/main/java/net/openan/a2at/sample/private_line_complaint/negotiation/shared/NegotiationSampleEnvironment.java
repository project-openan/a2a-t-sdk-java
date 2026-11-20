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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the small set of environment values needed by the negotiation sample entry points. */
public final class NegotiationSampleEnvironment {

    private NegotiationSampleEnvironment() {}

    public static Path defaultEnvPath(String role) {
        return Path.of(
                "a2a-t-sample",
                "src",
                "main",
                "resources",
                "sample",
                "private-line-complaint-negotiation",
                role,
                role + ".env");
    }

    public static Map<String, String> read(Path envPath) {
        try {
            Map<String, String> values = new LinkedHashMap<>();
            List<String> lines = Files.exists(envPath) ? Files.readAllLines(envPath) : List.of();
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int separator = line.indexOf('=');
                values.put(
                        line.substring(0, separator).trim(),
                        line.substring(separator + 1).trim());
            }
            return values;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sample environment file: " + envPath, exception);
        }
    }

    public static String host(Map<String, String> values) {
        return values.getOrDefault("A2AT_SAMPLE_HOST", "127.0.0.1");
    }

    public static int port(Map<String, String> values) {
        try {
            return Integer.parseInt(values.getOrDefault("A2AT_SAMPLE_PORT", "8000"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("A2AT_SAMPLE_PORT must be an integer", exception);
        }
    }
}
