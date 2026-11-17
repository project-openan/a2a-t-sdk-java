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

package net.openan.a2at.sdk.negotiation.contract;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Scans the SDK main sources for references to internal design documents.
 *
 * <p>Comments and javadoc of the shipped sources must be self-contained English text: they must not reference the
 * internal design specification, internal document paths or numbered spec sections. The scan walks the main source
 * trees of the core, negotiation, client and server modules and fails with the offending file and line.
 */
class SdkSourceCommentScanTest {

    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("设计规范"),
            Pattern.compile("design spec"),
            Pattern.compile("docs-local"),
            Pattern.compile("docs/feat"),
            Pattern.compile("§\\d"));

    private static final List<String> SCANNED_MODULE_ROOTS = List.of(
            "../a2a-t-core/src/main/java",
            "../a2a-t-negotiation/src/main/java",
            "../a2a-t-client/src/main/java",
            "../a2a-t-server/src/main/java");

    @Test
    void mainSourcesNeverReferenceInternalDesignDocuments() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String moduleRoot : SCANNED_MODULE_ROOTS) {
            Path root = Path.of(moduleRoot);
            assertTrue(Files.isDirectory(root), "the scanned module root must exist: " + moduleRoot);
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(file -> file.toString().endsWith(".java"))
                        .forEach(file -> collectViolations(file, violations));
            }
        }
        assertTrue(
                violations.isEmpty(),
                "main sources must not reference internal design documents: " + String.join("; ", violations));
    }

    private static void collectViolations(Path file, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + file, exception);
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            for (Pattern pattern : FORBIDDEN_PATTERNS) {
                if (pattern.matcher(line).find()) {
                    violations.add(file + ":" + (index + 1) + " matches " + pattern.pattern());
                }
            }
        }
    }
}
