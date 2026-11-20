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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObservabilitySetupTest {

    @BeforeEach
    void resetState() {
        GlobalOpenTelemetry.resetForTest();
        ObservabilitySetup.resetForTest();
    }

    @AfterEach
    void reset() {
        GlobalOpenTelemetry.resetForTest();
        ObservabilitySetup.resetForTest();
        System.clearProperty("A2AT_OBSERVABILITY_ENABLED");
        System.clearProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED");
    }

    @Test
    void consoleFallbackConfiguresAllThreeSignals() {
        GlobalOpenTelemetry.resetForTest();
        ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
        assertTrue(ObservabilitySetup.isConfigured());
        assertTrue(GlobalOpenTelemetry.isSet());
        Span span = GlobalOpenTelemetry.getTracer("t").spanBuilder("s").startSpan();
        assertTrue(span.isRecording());
        span.end();
    }

    @Test
    void existingGlobalProviderIsRespected() {
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().build());
        ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
        assertFalse(ObservabilitySetup.isConfigured());
    }

    @Test
    void disabledSkipsEverything() {
        System.setProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED", "false");
        ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
        assertFalse(ObservabilitySetup.isConfigured());
        assertFalse(ObservabilityRuntime.isEnabled());
    }
}
