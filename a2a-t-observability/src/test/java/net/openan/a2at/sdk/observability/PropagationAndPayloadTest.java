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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PropagationAndPayloadTest {

    @AfterEach
    void reset() {
        GlobalOpenTelemetry.resetForTest();
        System.clearProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED");
    }

    @Test
    void injectExtractRoundtripSurvivesLowercasing() {
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build());
        Map<String, String> headers = new HashMap<>();
        String traceId;
        try (var span = new A2ATSpan("root", SpanKind.CLIENT).open()) {
            traceId = Span.current().getSpanContext().getTraceId();
            TracePropagation.inject(headers);
        }
        Map<String, String> mixed = Map.of("TRACEPARENT", headers.get("traceparent"));
        Context extracted = TracePropagation.extract(mixed);
        assertNotNull(extracted);
        assertEquals(traceId, Span.fromContext(extracted).getSpanContext().getTraceId());
        assertTrue(headers.get("traceparent").startsWith("00-" + traceId));
    }

    @Test
    void extractReturnsNullWhenInvalidOrDisabled() {
        assertNull(TracePropagation.extract(Map.of("traceparent", "00-abc-def-01")));
        System.setProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED", "false");
        assertNull(TracePropagation.extract(
                Map.of("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")));
    }

    @Test
    void payloadRedactsBeforeTruncation() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromEnv().toBuilder()
                .payloadLogMaxLength(10)
                .payloadRedactor(value -> value.replace("secret", "***"))
                .build();
        String prepared = PayloadSanitizer.prepare("secret-abcdefghijklmnopqrstuvwxyz", config);
        assertFalse(prepared.contains("secret"));
        assertTrue(prepared.endsWith("[truncated]"));
        assertTrue(prepared.length() <= 10 + "[truncated]".length());
    }

    @Test
    void payloadExtractorSerializesRecordsAndNullFails() {
        record Point(int x, int y) {}
        assertTrue(PayloadExtractor.toJson(new Point(1, 2)).contains("\"x\":1"));
        assertNull(PayloadExtractor.toJson(null));
        assertNull(PayloadExtractor.toJson(new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("unserializable");
            }
        }));
    }
}
