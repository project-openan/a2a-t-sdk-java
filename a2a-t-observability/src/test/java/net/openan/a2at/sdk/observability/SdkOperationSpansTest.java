package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import net.openan.a2at.sdk.core.telemetry.TelemetryHook;
import net.openan.a2at.sdk.core.telemetry.TelemetryOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SdkOperationSpansTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider provider;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
        System.getProperties().remove(ObservabilityRuntime.ENV_MASTER);
        System.getProperties().remove(ObservabilityRuntime.ENV_TRACE);
    }

    @AfterEach
    void tearDown() {
        System.getProperties().remove(ObservabilityRuntime.ENV_MASTER);
        System.getProperties().remove(ObservabilityRuntime.ENV_TRACE);
        provider.close();
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void closeRecordsInternalSpanNamedAfterRoleAndMethod() {
        TelemetryHook hook = new SdkOperationSpans();
        try (TelemetryOperation operation = hook.startSdkOperation("client", "getPrompts")) {
            operation.markSuccess();
        }
        List<SpanData> ended = exporter.getFinishedSpanItems();
        assertEquals(1, ended.size());
        SpanData span = ended.get(0);
        assertEquals("a2at.sdk.client.getPrompts", span.getName());
        assertEquals(SpanKind.INTERNAL, span.getKind());
        assertEquals(StatusCode.OK, span.getStatus().getStatusCode());
    }

    @Test
    void markErrorEndsWithErrorStatusAndExceptionEvent() {
        TelemetryHook hook = new SdkOperationSpans();
        try (TelemetryOperation operation = hook.startSdkOperation("client", "generateTaskPrompt")) {
            operation.markError(new IllegalStateException("boom"));
        }
        SpanData span = exporter.getFinishedSpanItems().get(0);
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertTrue(span.getStatus().getDescription().contains("boom"));
        assertFalse(span.getEvents().isEmpty());
        assertEquals("exception", span.getEvents().get(0).getName());
    }

    @Test
    void setAttributeLandsOnSpanWithTypedDispatch() {
        TelemetryHook hook = new SdkOperationSpans();
        try (TelemetryOperation operation =
                hook.startSdkOperation("server", "validateTaskPromptAndDataFilling")) {
            operation.setAttribute("string.key", "v");
            operation.setAttribute("int.key", 7);
            operation.setAttribute("long.key", 9L);
            operation.setAttribute("bool.key", true);
            operation.setAttribute("double.key", 1.5d);
            operation.setAttribute("other.key", List.of("a", "b"));
        }
        var attributes = exporter.getFinishedSpanItems().get(0).getAttributes();
        assertEquals("v", attributes.get(AttributeKey.stringKey("string.key")));
        assertEquals(7L, attributes.get(AttributeKey.longKey("int.key")));
        assertEquals(9L, attributes.get(AttributeKey.longKey("long.key")));
        assertEquals(true, attributes.get(AttributeKey.booleanKey("bool.key")));
        assertEquals(1.5d, attributes.get(AttributeKey.doubleKey("double.key")));
        assertEquals("[a, b]", attributes.get(AttributeKey.stringKey("other.key")));
    }

    @Test
    void closeIsIdempotentAndNeverThrows() {
        TelemetryHook hook = new SdkOperationSpans();
        TelemetryOperation operation = hook.startSdkOperation("client", "getPrompts");
        operation.setAttribute("k", "v");
        assertDoesNotThrow(() -> {
            operation.close();
            operation.close();
        });
        assertEquals(1, exporter.getFinishedSpanItems().size());
    }

    @Test
    void disabledTraceSwitchAbsorbsOperationsWithoutSpan() {
        System.setProperty(ObservabilityRuntime.ENV_MASTER, "false");
        TelemetryHook hook = new SdkOperationSpans();
        TelemetryOperation operation =
                assertDoesNotThrow(() -> hook.startSdkOperation("client", "getPrompts"));
        assertSame(TelemetryOperation.NO_OP, operation);
        assertDoesNotThrow(() -> {
            operation.setAttribute("k", "v");
            operation.markSuccess();
            operation.markError(new RuntimeException("ignored"));
            operation.close();
        });
        assertTrue(exporter.getFinishedSpanItems().isEmpty());
    }

    @Test
    void reportNegotiationRoundsAbsorbsExtremeRoundCounts() {
        TelemetryHook hook = new SdkOperationSpans();
        assertDoesNotThrow(() -> hook.reportNegotiationRounds("neg-1", "accept", Long.MAX_VALUE));
        assertDoesNotThrow(() -> hook.reportNegotiationRounds("neg-2", "reject", -1L));
        assertDoesNotThrow(() -> hook.reportNegotiationRounds("neg-3", "accept", 3L));
    }
}
