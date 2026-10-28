package net.openan.a2at.sdk.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

/** Structured JSON events: SLF4J channel + OTel logs channel sharing identical semantics. */
public final class ObservabilityLogs {

    public static final String LOGGER_NAME = "net.openan.a2at.sdk.observability";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LOGGER_NAME);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ObservabilityLogs() {}

    public static void logEvent(String event, Level level, Map<String, Object> fields,
            @Nullable String payload, A2ATObservabilityConfig config) {
        if (!ObservabilityRuntime.isLogEnabled() || !config.logEnabled()) {
            return;
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("event", event);
            if (fields != null) {
                data.putAll(fields);
            }
            var spanContext = io.opentelemetry.api.trace.Span.current().getSpanContext();
            if (spanContext.isValid()) {
                data.put("trace_id", spanContext.getTraceId());
                data.put("span_id", spanContext.getSpanId());
            }
            if (payload != null && config.payloadLogEnabled()) {
                data.put("payload", PayloadSanitizer.prepare(payload, config));
            }
            String json = MAPPER.writeValueAsString(data);
            LOG.atLevel(level).log(json);
            emitOtel(level, data, json);
        } catch (Exception e) {
            LOG.debug("a2at: log_event failed", e);
        }
    }

    private static void emitOtel(Level level, Map<String, Object> data, String json) {
        try {
            Logger logger = GlobalOpenTelemetry.getOrNoop().getLogsBridge()
                    .get(ObservabilityRuntime.SCOPE_NAME);
            var builder = logger.logRecordBuilder()
                    .setSeverity(severity(level))
                    .setSeverityText(level.name())
                    .setBody(json);
            data.forEach((key, value) -> {
                if (!"event".equals(key) && !"trace_id".equals(key) && !"span_id".equals(key)) {
                    builder.setAttribute(key, String.valueOf(value));
                }
            });
            builder.emit();
        } catch (RuntimeException e) {
            LOG.debug("a2at: otel log emit failed", e);
        }
    }

    private static Severity severity(Level level) {
        return switch (level) {
            case DEBUG -> Severity.DEBUG;
            case INFO -> Severity.INFO;
            case WARN -> Severity.WARN;
            case ERROR -> Severity.ERROR;
            default -> Severity.INFO;
        };
    }
}
