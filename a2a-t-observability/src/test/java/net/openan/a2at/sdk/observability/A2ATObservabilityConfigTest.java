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
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromMap(Map.of(
                "A2AT_EXPORTER_PROTOCOL", "udp", "A2AT_LOGS_PAYLOAD_MAX_LENGTH", "abc"));
        assertEquals("grpc", config.protocol());
        assertEquals(4096, config.payloadLogMaxLength());
    }

    @Test
    void builderBeatsMap() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromMap(Map.of("A2AT_SERVICE_NAME", "from-map"))
                .toBuilder()
                .serviceName("explicit")
                .taskTypeRegex("")
                .build();
        assertEquals("explicit", config.serviceName());
        assertEquals("", config.taskTypeRegex());
    }
}
