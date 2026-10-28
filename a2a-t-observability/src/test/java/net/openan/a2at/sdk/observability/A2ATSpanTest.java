package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class A2ATSpanTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider provider;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
    }

    @AfterEach
    void tearDown() {
        provider.close();
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void tryWithResourcesEndsWithOk() {
        try (A2ATSpan span = new A2ATSpan("manual.op", SpanKind.INTERNAL).setAttribute("k", "v").open()) {
            span.setAttribute("k2", 2);
            assertNotNull(CurrentSpans.current());
            assertNotNull(CurrentSpans.current().traceId());
        }
        List<SpanData> ended = exporter.getFinishedSpanItems();
        assertEquals(1, ended.size());
        assertEquals("manual.op", ended.get(0).getName());
        assertEquals(io.opentelemetry.api.trace.StatusCode.OK, ended.get(0).getStatus().getStatusCode());
        assertEquals("v", ended.get(0).getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("k")));
    }

    @Test
    void recordErrorEndsWithErrorAndException() {
        RuntimeException failure = new IllegalStateException("boom");
        try (A2ATSpan span = new A2ATSpan("manual.fail").open()) {
            span.recordError(failure);
        }
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, data.getStatus().getStatusCode());
        assertFalse(data.getEvents().isEmpty());
        assertTrue(data.getStatus().getDescription().contains("boom"));
    }

    @Test
    void addLinkBeforeOpenCreatesLink() {
        try (A2ATSpan entry = new A2ATSpan("entry").open()) {
            try (A2ATSpan event = new A2ATSpan("event").addLink(entry).open()) {
                // linked at creation
            }
        }
        List<SpanData> ended = exporter.getFinishedSpanItems();
        SpanData event = ended.stream().filter(d -> d.getName().equals("event")).findFirst().orElseThrow();
        assertEquals(1, event.getLinks().size());
        assertEquals(
                ended.stream().filter(d -> d.getName().equals("entry")).findFirst().orElseThrow()
                        .getSpanContext(),
                event.getLinks().get(0).getSpanContext());
    }

    @Test
    void singleUseAndClosedAbsorb() {
        A2ATSpan span = new A2ATSpan("once").open();
        assertThrows(IllegalStateException.class, span::open);
        span.close();
        span.close();
    }

    @Test
    void currentSpanNullOutsideAndValidInside() {
        assertNull(CurrentSpans.current());
        try (A2ATSpan span = new A2ATSpan("s").open()) {
            A2ATCurrentSpan current = CurrentSpans.current();
            assertNotNull(current);
            assertTrue(current.isValid());
            assertEquals(32, current.traceId().length());
            assertEquals(16, current.spanId().length());
            current.setAttribute("cur", "x");
        }
    }
}
