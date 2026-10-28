package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.trace.Span;

/** Read/append view of the active span. */
public final class A2ATCurrentSpan {
    private final Span span;

    A2ATCurrentSpan(Span span) {
        this.span = span;
    }

    public String traceId() {
        return span.getSpanContext().getTraceId();
    }

    public String spanId() {
        return span.getSpanContext().getSpanId();
    }

    public boolean isValid() {
        return span.getSpanContext().isValid();
    }

    public void setAttribute(String key, Object value) {
        span.setAttribute(key, String.valueOf(value));
    }
}
