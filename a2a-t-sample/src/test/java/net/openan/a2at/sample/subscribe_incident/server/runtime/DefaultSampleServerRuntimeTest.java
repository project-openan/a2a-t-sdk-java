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

package net.openan.a2at.sample.subscribe_incident.server.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import org.junit.jupiter.api.Test;

class DefaultSampleServerRuntimeTest {

    @Test
    void resolveBindUsesDefaultsWhenHostAndPortAreBlank() {
        Path envPath = createEnvFile("A2AT_SAMPLE_HOST=\nA2AT_SAMPLE_PORT=\n");

        ServerBind bind = new DefaultSampleServerRuntime(envPath, message -> {}).resolveBind();

        assertEquals("127.0.0.1", bind.host());
        assertEquals(8000, bind.port());
    }

    @Test
    void createSampleThreadFactoryRegistersUncaughtExceptionHandler() {
        List<String> logs = new ArrayList<>();
        AssertionError failure = new AssertionError("boom");

        ThreadFactory threadFactory = DefaultSampleServerRuntime.createSampleThreadFactory(logs::add);
        Thread thread = threadFactory.newThread(() -> {
            throw failure;
        });
        thread.start();
        assertDoesNotThrow(() -> thread.join(1000L));

        assertEquals("a2a-t-sample-server-1", thread.getName());
        assertTrue(thread.isDaemon());
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("a2a-t-sample-server"));
        assertTrue(logs.get(0).contains(AssertionError.class.getName()));
        assertTrue(logs.get(0).contains("boom"));
    }

    private static Path createEnvFile(String content) {
        try {
            Path envPath = Files.createTempFile("a2a-t-server", ".env");
            Files.writeString(envPath, content);
            return envPath;
        } catch (IOException exception) {
            throw new AssertionError("Failed to create server env test file", exception);
        }
    }
}
