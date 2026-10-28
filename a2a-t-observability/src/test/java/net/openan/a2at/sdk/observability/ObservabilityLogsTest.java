package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

class ObservabilityLogsTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private SdkTracerProvider tracerProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger("net.openan.a2at.sdk.observability");
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        GlobalOpenTelemetry.resetForTest();
        tracerProvider = SdkTracerProvider.builder().build();
        GlobalOpenTelemetry.set(
                OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build());
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        GlobalOpenTelemetry.resetForTest();
        System.clearProperty("A2AT_LOG_ENABLED");
        System.clearProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED");
    }

    @Test
    void jsonRecordCarriesEventFieldsAndTraceIds() throws Exception {
        try (A2ATSpan span = new A2ATSpan("entry").open()) {
            ObservabilityLogs.logEvent(
                    "task.status_changed",
                    Level.INFO,
                    Map.of("task.id", "t1", "task.status", "working"),
                    null,
                    A2ATObservabilityConfig.fromEnv());
        }
        ILoggingEvent event = appender.list.stream()
                .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.INFO)
                .findFirst()
                .orElseThrow();
        Map<?, ?> json = mapper.readValue(event.getFormattedMessage(), Map.class);
        assertEquals("task.status_changed", json.get("event"));
        assertEquals("t1", json.get("task.id"));
        assertEquals("working", json.get("task.status"));
        assertNotNull(json.get("trace_id"));
        assertNotNull(json.get("span_id"));
    }

    @Test
    void payloadChannelHonorsConfigAndRedaction() throws Exception {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromEnv().toBuilder()
                .payloadLogEnabled(true)
                .payloadRedactor(v -> v.replace("key", "***"))
                .build();
        ObservabilityLogs.logEvent("task.request", Level.DEBUG, Map.of(), "payload: key=1", config);
        Map<?, ?> json = mapper.readValue(appender.list.get(0).getFormattedMessage(), Map.class);
        assertTrue(String.valueOf(json.get("payload")).contains("***"));
        assertFalse(String.valueOf(json.get("payload")).contains("key=1"));
    }

    @Test
    void logSwitchOffEmitsNothing() {
        System.setProperty("A2AT_LOG_ENABLED", "false");
        ObservabilityLogs.logEvent("task.request", Level.DEBUG, Map.of(), null, A2ATObservabilityConfig.fromEnv());
        System.clearProperty("A2AT_LOG_ENABLED");
        assertEquals(0, appender.list.size());
    }
}
