package net.openan.a2at.sdk.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** In-house dev-mode exporters (JSON lines on SLF4J); replaces opentelemetry-exporter-logging. */
final class ConsoleExporters {

    private static final Logger LOG = LoggerFactory.getLogger("net.openan.a2at.sdk.observability.console");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConsoleExporters() {}

    static SpanExporter spanExporter() {
        return new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                spans.forEach(span -> LOG.info(json(
                        "span",
                        span.getName(),
                        Map.of(
                                "traceId",
                                span.getTraceId(),
                                "spanId",
                                span.getSpanId(),
                                "kind",
                                span.getKind().name(),
                                "status",
                                span.getStatus().getStatusCode().name(),
                                "attributes",
                                span.getAttributes().asMap()))));
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
    }

    static MetricExporter metricExporter() {
        return new MetricExporter() {
            @Override
            public CompletableResultCode export(Collection<MetricData> metrics) {
                metrics.forEach(metric -> LOG.info(json(
                        "metric",
                        metric.getName(),
                        Map.of(
                                "unit",
                                String.valueOf(metric.getUnit()),
                                "description",
                                String.valueOf(metric.getDescription())))));
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
                return AggregationTemporality.CUMULATIVE;
            }
        };
    }

    static LogRecordExporter logRecordExporter() {
        return new LogRecordExporter() {
            @Override
            public CompletableResultCode export(Collection<LogRecordData> records) {
                records.forEach(record -> LOG.info(json(
                        "log",
                        String.valueOf(record.getBodyValue()),
                        Map.of(
                                "severity", String.valueOf(record.getSeverityText()),
                                "attributes", record.getAttributes().asMap()))));
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };
    }

    private static String json(String signal, String name, Object data) {
        try {
            return MAPPER.writeValueAsString(Map.of("signal", signal, "name", name, "data", data));
        } catch (Exception e) {
            return "{\"signal\":\"" + signal + "\",\"error\":\"serialize_failed\"}";
        }
    }
}
