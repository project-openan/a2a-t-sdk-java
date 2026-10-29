package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import net.openan.a2at.sdk.core.telemetry.TelemetryHook;
import net.openan.a2at.sdk.core.telemetry.TelemetryOperation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** TelemetryHook backed by OTel spans + negotiation metric; every method absorbs failures silently. */
public final class SdkOperationSpans implements TelemetryHook {

    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityLogs.LOGGER_NAME);

    @Override
    public TelemetryOperation startSdkOperation(String role, String method) {
        if (!ObservabilityRuntime.isTraceEnabled()) {
            return TelemetryOperation.NO_OP;
        }
        try {
            Span span = ObservabilityRuntime.tracer()
                    .spanBuilder("a2at.sdk." + role + "." + method)
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
            return new SpanOperation(span);
        } catch (RuntimeException e) {
            LOG.warn("a2at: failed to start sdk operation span; telemetry disabled for this call", e);
            return TelemetryOperation.NO_OP;
        }
    }

    @Override
    public void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds) {
        try {
            if (totalRounds < 0 || totalRounds > Integer.MAX_VALUE) {
                return;
            }
            NegotiationMetrics.reportRounds(negotiationId, outcome, (int) totalRounds);
        } catch (RuntimeException e) {
            LOG.warn("a2at: failed to report negotiation rounds", e);
        }
    }

    private static void applyAttribute(Span target, String key, Object value) {
        if (value instanceof String s) {
            target.setAttribute(key, s);
        } else if (value instanceof Integer i) {
            target.setAttribute(key, i);
        } else if (value instanceof Long l) {
            target.setAttribute(key, l);
        } else if (value instanceof Boolean b) {
            target.setAttribute(key, b);
        } else if (value instanceof Double d) {
            target.setAttribute(key, d);
        } else {
            target.setAttribute(key, String.valueOf(value));
        }
    }

    private static final class SpanOperation implements TelemetryOperation {

        private final @Nullable Span span;
        private final @Nullable Scope scope;
        private boolean errorRecorded;
        private boolean closed;

        SpanOperation(Span span) {
            this.span = span;
            this.scope = span.makeCurrent();
        }

        @Override
        public void setAttribute(String key, Object value) {
            try {
                if (span != null && span.isRecording()) {
                    applyAttribute(span, key, value);
                }
            } catch (RuntimeException e) {
                LOG.warn("a2at: failed to set sdk operation span attribute", e);
            }
        }

        @Override
        public void markSuccess() {}

        @Override
        public void markError(Throwable error) {
            try {
                if (span == null || error == null || !span.isRecording()) {
                    return;
                }
                errorRecorded = true;
                span.recordException(error);
                span.setStatus(StatusCode.ERROR, String.valueOf(error.getMessage()));
            } catch (RuntimeException e) {
                LOG.warn("a2at: failed to record sdk operation span error", e);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                try (Scope restore = scope) {
                    if (span != null && span.isRecording()) {
                        if (!errorRecorded) {
                            span.setStatus(StatusCode.OK);
                        }
                        span.end();
                    }
                }
            } catch (RuntimeException e) {
                LOG.warn("a2at: failed to close sdk operation span", e);
            }
        }
    }
}
