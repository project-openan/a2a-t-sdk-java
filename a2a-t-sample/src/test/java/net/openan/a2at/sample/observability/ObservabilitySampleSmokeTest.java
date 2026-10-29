package net.openan.a2at.sample.observability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import net.openan.a2at.sdk.observability.ObservabilityRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObservabilitySampleSmokeTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider provider;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
        System.setProperty(ObservabilityRuntime.ENV_MASTER, "true");
        System.setProperty(ObservabilityRuntime.ENV_TRACE, "true");
        System.setProperty(ObservabilityRuntime.ENV_METRIC, "true");
        System.setProperty(ObservabilityRuntime.ENV_LOG, "true");
    }

    @AfterEach
    void tearDown() {
        System.getProperties().remove(ObservabilityRuntime.ENV_MASTER);
        System.getProperties().remove(ObservabilityRuntime.ENV_TRACE);
        System.getProperties().remove(ObservabilityRuntime.ENV_METRIC);
        System.getProperties().remove(ObservabilityRuntime.ENV_LOG);
        provider.close();
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void offlineFlowEmitsExpectedSpansAndIsRepeatable() {
        assertDoesNotThrow(ObservabilitySampleMain::run);
        List<String> names = exporter.getFinishedSpanItems().stream()
                .map(SpanData::getName)
                .toList();
        assertTrue(names.contains("sample.workflow"), "root span missing; got: " + names);
        assertTrue(names.contains("a2at.sdk.client.getPrompts"), "facade span missing; got: " + names);
        assertTrue(names.contains("sample.negotiation-rounds"), "negotiation span missing; got: " + names);

        assertDoesNotThrow(ObservabilitySampleMain::run);
    }
}
