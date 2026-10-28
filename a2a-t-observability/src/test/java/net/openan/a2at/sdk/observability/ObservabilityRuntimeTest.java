package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservabilityRuntimeTest {

    @AfterEach
    void clearSwitches() {
        System.clearProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED");
        System.clearProperty("A2AT_TRACE_ENABLED");
        GlobalOpenTelemetry.resetForTest();
        ObservabilitySetup.resetForTest();
    }

    @Test
    void masterSwitchDisablesAllSignals() {
        System.setProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED", "false");
        assertFalse(ObservabilityRuntime.isEnabled());
        assertFalse(ObservabilityRuntime.isTraceEnabled());
        assertFalse(ObservabilityRuntime.isMetricEnabled());
        assertFalse(ObservabilityRuntime.isLogEnabled());
    }

    @Test
    void traceToggleIsIndependentOfMasterOn() {
        System.setProperty("A2AT_TRACE_ENABLED", "false");
        assertTrue(ObservabilityRuntime.isEnabled());
        assertFalse(ObservabilityRuntime.isTraceEnabled());
        assertTrue(ObservabilityRuntime.isMetricEnabled());
    }

    @Test
    void sameTraceOrphanContextKeepsTraceIdAndDropsParent() {
        SpanContext parent = SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        Context orphan = ObservabilityRuntime.sameTraceOrphanContext(parent);
        assertNotNull(orphan);
        SpanContext orphanSpan = Span.fromContext(orphan).getSpanContext();
        assertEquals(parent.getTraceId(), orphanSpan.getTraceId());
        assertNotEquals(parent.getSpanId(), orphanSpan.getSpanId());
        assertTrue(orphanSpan.isValid());
    }

    @Test
    void invalidParentYieldsNull() {
        assertNull(ObservabilityRuntime.sameTraceOrphanContext(SpanContext.getInvalid()));
        assertNull(ObservabilityRuntime.sameTraceOrphanContext(null));
    }

    @Test
    void tracerAndMeterScopeNames() {
        assertNotNull(ObservabilityRuntime.tracer());
        assertNotNull(ObservabilityRuntime.meter());
    }

    @Test
    void runtimeAccessBeforeGlobalSetDoesNotPoisonSetup() {
        GlobalOpenTelemetry.resetForTest();
        ObservabilitySetup.resetForTest();
        assertNotNull(ObservabilityRuntime.tracer());
        TracePropagation.extract(Map.of(
                "traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"));
        ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
        assertTrue(ObservabilitySetup.isConfigured());
        assertTrue(GlobalOpenTelemetry.isSet());
    }
}
