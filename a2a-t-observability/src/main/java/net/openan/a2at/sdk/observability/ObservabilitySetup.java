package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lazy auto-config of all three signals; user-registered providers are never overridden. */
public final class ObservabilitySetup {

    private static final Logger LOG = LoggerFactory.getLogger(ObservabilitySetup.class);
    private static final AtomicBoolean CONFIGURED = new AtomicBoolean(false);

    private ObservabilitySetup() {}

    public static boolean isConfigured() {
        return CONFIGURED.get();
    }

    /** Test-only: clears the configured flag (mirrors GlobalOpenTelemetry.resetForTest). */
    static void resetForTest() {
        CONFIGURED.set(false);
    }

    public static void ensureConfigured() {
        ensureConfigured(A2ATObservabilityConfig.fromEnv());
    }

    public static void ensureConfigured(A2ATObservabilityConfig config) {
        if (CONFIGURED.get() || !ObservabilityRuntime.isEnabled() || !config.enabled()) {
            return;
        }
        if (!sdkAvailable()) {
            return;
        }
        try {
            configure(config);
            CONFIGURED.set(true);
        } catch (IllegalStateException alreadySet) {
            LOG.debug("a2at: global OTel already registered; skipping auto-config");
        } catch (RuntimeException e) {
            LOG.warn("a2at: otel auto-config failed", e);
        }
    }

    private static boolean sdkAvailable() {
        try {
            Class.forName("io.opentelemetry.sdk.OpenTelemetrySdk");
            return true;
        } catch (ClassNotFoundException e) {
            LOG.warn("a2at: opentelemetry-sdk not on classpath; auto-config skipped");
            return false;
        }
    }

    private static void configure(A2ATObservabilityConfig config) {
        Resource resource = buildResource(config);
        Exporters exporters = buildExporters(config);
        SdkTracerProvider tracerProvider = null;
        SdkMeterProvider meterProvider = null;
        SdkLoggerProvider loggerProvider = null;
        if (exporters.span != null && config.traceEnabled() && ObservabilityRuntime.isTraceEnabled()) {
            tracerProvider = SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(BatchSpanProcessor.builder(exporters.span)
                            .setExporterTimeout(2, TimeUnit.SECONDS).build())
                    .build();
        }
        if (exporters.metric != null && config.metricEnabled() && ObservabilityRuntime.isMetricEnabled()) {
            meterProvider = SdkMeterProvider.builder()
                    .setResource(resource)
                    .registerMetricReader(PeriodicMetricReader.builder(exporters.metric)
                            .setInterval(30, TimeUnit.SECONDS).build())
                    .build();
        }
        if (exporters.log != null && config.logEnabled() && ObservabilityRuntime.isLogEnabled()) {
            loggerProvider = SdkLoggerProvider.builder()
                    .setResource(resource)
                    .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporters.log).build())
                    .build();
        }
        OpenTelemetrySdkBuilder builder = OpenTelemetrySdk.builder();
        if (tracerProvider != null) {
            builder.setTracerProvider(tracerProvider);
        }
        if (meterProvider != null) {
            builder.setMeterProvider(meterProvider);
        }
        if (loggerProvider != null) {
            builder.setLoggerProvider(loggerProvider);
        }
        GlobalOpenTelemetry.set(builder.build());
        if (exporters.console) {
            LOG.warn("a2at: no OTLP endpoint configured; using Console exporters (dev mode)");
        }
    }

    private static Resource buildResource(A2ATObservabilityConfig config) {
        AttributesBuilder attributes = Attributes.builder();
        attributes.put("service.name", config.serviceName() == null
                ? A2ATObservabilityConfig.DEFAULT_SERVICE_NAME : config.serviceName());
        String version = ObservabilitySetup.class.getPackage() == null
                ? null : ObservabilitySetup.class.getPackage().getImplementationVersion();
        attributes.put("service.version", version == null ? "dev" : version);
        if (config.resolveAgentRole() != null) {
            attributes.put("agent.role", config.resolveAgentRole());
        }
        return Resource.getDefault().merge(Resource.create(attributes.build()));
    }

    private static Exporters buildExporters(A2ATObservabilityConfig config) {
        Exporters exporters = new Exporters();
        if (config.endpoint() == null) {
            boolean consoleEnabled = switchOn(A2ATObservabilityConfig.ENV_CONSOLE_EXPORTER);
            if (!consoleEnabled) {
                LOG.info("a2at: console exporter disabled and no OTLP endpoint; signals stay no-op");
                return exporters;
            }
            exporters.span = ConsoleExporters.spanExporter();
            exporters.metric = ConsoleExporters.metricExporter();
            exporters.log = ConsoleExporters.logRecordExporter();
            exporters.console = true;
            return exporters;
        }
        exporters.console = false;
        if ("http/protobuf".equals(config.protocol())) {
            String base = config.endpoint().replaceAll("/$", "");
            exporters.span = OtlpHttpSpanExporter.builder()
                    .setEndpoint(base + "/v1/traces").build();
            exporters.metric = OtlpHttpMetricExporter.builder()
                    .setEndpoint(base + "/v1/metrics").build();
            exporters.log = OtlpHttpLogRecordExporter.builder()
                    .setEndpoint(base + "/v1/logs").build();
        } else {
            exporters.span = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(config.endpoint()).build();
            exporters.metric = OtlpGrpcMetricExporter.builder()
                    .setEndpoint(config.endpoint()).build();
            exporters.log = OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(config.endpoint()).build();
        }
        return exporters;
    }

    private static boolean switchOn(String key) {
        String raw = System.getProperty(key, System.getenv(key));
        if (raw == null) {
            return true;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    private static final class Exporters {
        @Nullable SpanExporter span;
        @Nullable MetricExporter metric;
        @Nullable LogRecordExporter log;
        boolean console;
    }
}
