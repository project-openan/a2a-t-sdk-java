package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.metrics.LongHistogram;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Lazy histogram cache keyed by metric name (shared meter scope a2at-observability/1.0.0). */
final class MetricHistograms {

    private static final ConcurrentMap<String, LongHistogram> INSTRUMENTS = new ConcurrentHashMap<>();

    private MetricHistograms() {}

    static LongHistogram histogram(String name, String unit, String description) {
        return INSTRUMENTS.computeIfAbsent(name, metricName -> ObservabilityRuntime.meter()
                .histogramBuilder(metricName)
                .setUnit(unit)
                .setDescription(description)
                .ofLongs()
                .build());
    }
}
