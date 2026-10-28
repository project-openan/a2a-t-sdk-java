package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NegotiationMetricsTest {

    private InMemoryMetricReader reader;

    @BeforeEach
    void setUp() {
        reader = InMemoryMetricReader.create();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder()
                .setMeterProvider(
                        SdkMeterProvider.builder().registerMetricReader(reader).build())
                .build());
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void terminalOutcomeRecordsRoundsWithAttributes() {
        NegotiationMetrics.reportRounds("N001", "accept", 3);
        var metrics = reader.collectAllMetrics();
        var histogram = metrics.stream()
                .filter(m -> "a2at.negotiation.total_rounds".equals(m.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("a2at.negotiation.total_rounds", histogram.getName());
        assertTrue(!histogram.getHistogramData().getPoints().isEmpty());
    }

    @Test
    void nonTerminalOutcomeAndNegativeRoundsAreSkipped() {
        NegotiationMetrics.reportRounds("N1", "propose", 2);
        NegotiationMetrics.reportRounds("N1", "accept", -1);
        assertEquals(0, reader.collectAllMetrics().size());
    }
}
