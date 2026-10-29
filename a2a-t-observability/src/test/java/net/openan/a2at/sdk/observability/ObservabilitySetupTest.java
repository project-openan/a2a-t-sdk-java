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
