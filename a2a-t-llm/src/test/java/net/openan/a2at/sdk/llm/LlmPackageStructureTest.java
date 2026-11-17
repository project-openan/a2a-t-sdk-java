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

package net.openan.a2at.sdk.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LlmPackageStructureTest {

    @Test
    void llmRootPackageExposesOnlySimplifiedPublicApi() throws IOException {
        Path root = llmRoot();

        assertEquals(
                List.of(
                        "LLMClient.java",
                        "LLMClientConfig.java",
                        "LLMClientFactory.java",
                        "LLMConfigError.java",
                        "LLMError.java",
                        "LLMResponse.java",
                        "LLMRuntimeError.java",
                        "package-info.java"),
                topLevelJavaFiles(root));
    }

    @Test
    void llmPackageKeepsOnlyProviderSubpackage() throws IOException {
        Path root = llmRoot();

        assertEquals(List.of("providers"), topLevelDirectories(root));
        assertEquals(List.of("OpenAIClient.java"), topLevelJavaFiles(root.resolve("providers")));
    }

    @Test
    void llmPackageRemovesLegacyAdapterBuilderParserTransportAndMapperLayers() {
        Path root = llmRoot();

        assertFalse(Files.exists(root.resolve("adapter")));
        assertFalse(Files.exists(root.resolve("config")));
        assertFalse(Files.exists(root.resolve("exception")));
        assertFalse(Files.exists(root.resolve("internal")));
        assertFalse(Files.exists(root.resolve("model")));
        assertFalse(Files.exists(root.resolve("spi")));
        assertFalse(Files.exists(root.resolve("api")));
    }

    @Test
    void llmTestsRemoveLegacyConfigInternalAndModelPackages() {
        Path root = llmTestRoot();

        assertFalse(Files.exists(root.resolve("config")));
        assertFalse(Files.exists(root.resolve("internal")));
        assertFalse(Files.exists(root.resolve("model")));
    }

    private static Path llmRoot() {
        return Path.of("src", "main", "java", "net", "openan", "a2at", "sdk", "llm");
    }

    private static Path llmTestRoot() {
        return Path.of("src", "test", "java", "net", "openan", "a2at", "sdk", "llm");
    }

    private static List<String> topLevelJavaFiles(Path path) throws IOException {
        try (var files = Files.list(path)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".java"))
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static List<String> topLevelDirectories(Path path) throws IOException {
        try (var files = Files.list(path)) {
            return files.filter(Files::isDirectory)
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
