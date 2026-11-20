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

package net.openan.a2at.sdk.core.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ConfigFileNotFoundException;
import org.jspecify.annotations.NonNull;

/**
 * Reads SDK configuration from one caller-supplied `.env` file.
 * The SDK does not scan default locations automatically.
 *
 * @since 2026-06
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DotEnvConfigSource {

    private static final int MAX_ENTRIES = 200;

    /**
     * Loads non-empty key/value pairs from one `.env` file.
     *
     * @param path caller-supplied `.env` file path
     * @return parsed non-empty key/value pairs
     * @throws A2ATError if the file contains more than {@value #MAX_ENTRIES} entries or cannot be read
     */
    public static @NonNull Map<String, String> load(@NonNull Path path) {
        if (!Files.exists(path)) {
            throw new ConfigFileNotFoundException(path);
        }

        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                if (key.isEmpty() || value.isEmpty()) {
                    continue;
                }
                if (values.size() >= MAX_ENTRIES) {
                    throw new A2ATError(
                            "Config file exceeds maximum allowed entries: " + MAX_ENTRIES);
                }
                values.put(key, value);
            }
        } catch (IOException exception) {
            throw new A2ATError("Failed to read config file: " + path, exception);
        }
        return Map.copyOf(values);
    }
}
