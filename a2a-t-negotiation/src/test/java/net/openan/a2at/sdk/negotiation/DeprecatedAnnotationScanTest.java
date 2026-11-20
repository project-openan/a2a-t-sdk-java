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

package net.openan.a2at.sdk.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Guards the compatibility promise of the negotiation content layer: now that the legacy state-machine negotiation
 * packages are removed, no compilation unit of the module main sources may carry a {@code @Deprecated} marker. The
 * scan pins the empty set of {@code @Deprecated} annotations, so any newly added annotation fails the test.
 */
class DeprecatedAnnotationScanTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /** No @Deprecated markers remain after the legacy state-machine negotiation removal. */
    private static final List<String> PINNED_DEPRECATED_ANNOTATIONS = List.of();

    @Test
    void noNewDeprecatedAnnotationsWereAddedToTheModuleMainSources() throws IOException {
        Assumptions.assumeTrue(
                Files.isDirectory(MAIN_SOURCES), "module main sources are not visible from the test working directory");

        List<String> annotatedFiles;
        try (Stream<Path> sources = Files.walk(MAIN_SOURCES)) {
            annotatedFiles = sources.filter(Files::isRegularFile)
                    .filter(DeprecatedAnnotationScanTest::containsDeprecatedAnnotation)
                    .map(path -> MAIN_SOURCES.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }

        assertEquals(
                PINNED_DEPRECATED_ANNOTATIONS,
                annotatedFiles,
                "the negotiation module must not gain @Deprecated annotations; the legacy state-machine negotiation"
                        + " APIs that carried them were removed");
        assertTrue(
                annotatedFiles.stream()
                        .noneMatch(file -> file.startsWith("content")
                                || file.startsWith("generation")
                                || file.startsWith("validation")
                                || file.startsWith("resources")),
                "none of the negotiation content layer packages may carry @Deprecated annotations");
    }

    private static boolean containsDeprecatedAnnotation(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains("@Deprecated");
        } catch (IOException exception) {
            return false;
        }
    }
}
