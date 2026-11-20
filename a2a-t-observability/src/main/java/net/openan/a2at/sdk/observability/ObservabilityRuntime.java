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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

/** Master/signal switches + OTel accessors. Switches read dynamically (property > env > default). */
public final class ObservabilityRuntime {

    public static final String ENV_MASTER = "OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED";
    public static final String ENV_TRACE = "A2AT_TRACE_ENABLED";
    public static final String ENV_METRIC = "A2AT_METRIC_ENABLED";
    public static final String ENV_LOG = "A2AT_LOG_ENABLED";

    public static final String SCOPE_NAME = "a2at-observability";
    public static final String SCOPE_VERSION = "1.0.0";

    private ObservabilityRuntime() {}

    private static boolean switchOn(String key) {
        String raw = System.getProperty(key, System.getenv(key));
        if (raw == null) {
            return true;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    public static boolean isEnabled() {
        return switchOn(ENV_MASTER);
    }

    public static boolean isTraceEnabled() {
        return isEnabled() && switchOn(ENV_TRACE);
    }

    public static boolean isMetricEnabled() {
        return isEnabled() && switchOn(ENV_METRIC);
    }

    public static boolean isLogEnabled() {
        return isEnabled() && switchOn(ENV_LOG);
    }

    public static Tracer tracer() {
        return GlobalOpenTelemetry.getOrNoop().getTracer(SCOPE_NAME, SCOPE_VERSION);
    }

    public static Meter meter() {
        return GlobalOpenTelemetry.getOrNoop().meterBuilder(SCOPE_NAME)
                .setInstrumentationVersion(SCOPE_VERSION)
                .build();
    }

    /** Same-trace orphan context: new span context shares trace_id, random span_id, no parent. */
    public static @Nullable Context sameTraceOrphanContext(@Nullable SpanContext parent) {
        if (parent == null || !parent.isValid()) {
            return null;
        }
        long random = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        SpanContext orphan = SpanContext.create(
                parent.getTraceId(), String.format("%016x", random),
                TraceFlags.fromByte(parent.getTraceFlags().asByte()), parent.getTraceState());
        return Context.root().with(io.opentelemetry.api.trace.Span.wrap(orphan));
    }
}
