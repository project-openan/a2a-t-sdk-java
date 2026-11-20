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

package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class A2ATObservabilityConfigTest {

    @Test
    void defaultsMatchSpec() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromEnv();
        assertTrue(config.enabled());
        assertTrue(config.traceEnabled());
        assertTrue(config.metricEnabled());
        assertTrue(config.logEnabled());
        assertEquals("grpc", config.protocol());
        assertEquals(A2ATObservabilityConfig.DEFAULT_PAYLOAD_LOG_MAX_LENGTH, config.payloadLogMaxLength());
        assertFalse(config.extractRequest());
        assertFalse(config.extractResponse());
        assertFalse(config.payloadLogEnabled());
    }

    @Test
    void mapValuesBeatDefaults() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromMap(Map.of(
                "A2AT_OBSERVABILITY_ENABLED", "false",
                "A2AT_TRACE_ENABLED", "false",
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318",
                "A2AT_EXPORTER_PROTOCOL", "http",
                "A2AT_SERVICE_NAME", "svc",
                "A2AT_LOGS_PAYLOAD_ENABLED", "TRUE",
                "A2AT_LOGS_PAYLOAD_MAX_LENGTH", "128"));
        assertFalse(config.enabled());
        assertFalse(config.traceEnabled());
        assertTrue(config.metricEnabled());
        assertEquals("http/protobuf", config.protocol());
        assertEquals("http://collector:4318", config.endpoint());
        assertEquals("svc", config.serviceName());
        assertTrue(config.payloadLogEnabled());
        assertEquals(128, config.payloadLogMaxLength());
    }

    @Test
    void invalidProtocolFallsBackToGrpcAndInvalidIntFallsBackToDefault() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromMap(
                Map.of("A2AT_EXPORTER_PROTOCOL", "udp", "A2AT_LOGS_PAYLOAD_MAX_LENGTH", "abc"));
        assertEquals("grpc", config.protocol());
        assertEquals(4096, config.payloadLogMaxLength());
    }

    @Test
    void builderBeatsMap() {
        A2ATObservabilityConfig config =
                A2ATObservabilityConfig.fromMap(Map.of("A2AT_SERVICE_NAME", "from-map")).toBuilder()
                        .serviceName("explicit")
                        .taskTypeRegex("")
                        .build();
        assertEquals("explicit", config.serviceName());
        assertEquals("", config.taskTypeRegex());
    }
}
