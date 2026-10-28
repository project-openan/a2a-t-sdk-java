package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Manual A2A-T span; try-with-resources + open(). Absorbs everything when trace is disabled. */
public final class A2ATSpan implements AutoCloseable {

    private final String name;
    private final SpanKind kind;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<SpanContext> pendingLinks = new ArrayList<>();
    private @Nullable Span span;
    private @Nullable Scope scope;
    private boolean opened;
    private boolean closed;
    private boolean errorRecorded;

    public A2ATSpan(String name) {
        this(name, SpanKind.INTERNAL);
    }

    public A2ATSpan(String name, SpanKind kind) {
        this.name = name;
        this.kind = kind;
    }

    public A2ATSpan setAttribute(String key, Object value) {
        if (span != null) {
            applyAttribute(span, key, value);
        } else {
            attributes.put(key, value);
        }
        return this;
    }

    public A2ATSpan addLink(A2ATSpan other) {
        SpanContext context = other == null ? null : other.spanContext();
        if (context == null || !context.isValid()) {
            return this;
        }
        if (span != null) {
            span.addLink(context);
        } else {
            pendingLinks.add(context);
        }
        return this;
    }

    /** Starts the span and makes it current; idempotent-guarded, absorbs when trace is off. */
    public A2ATSpan open() {
        if (opened) {
            throw new IllegalStateException("A2ATSpan is single-use; create a new instance for each span");
        }
        opened = true;
        if (!ObservabilityRuntime.isTraceEnabled()) {
            return this;
        }
        SpanBuilder builder = ObservabilityRuntime.tracer().spanBuilder(name).setSpanKind(kind);
        for (SpanContext link : pendingLinks) {
            builder.addLink(link);
        }
        span = builder.startSpan();
        attributes.forEach(this::applyOnSpan);
        scope = span.makeCurrent();
        return this;
    }

    private void applyOnSpan(String key, Object value) {
        if (span != null) {
            applyAttribute(span, key, value);
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

    public void recordError(Throwable error) {
        if (span == null || error == null) {
            return;
        }
        errorRecorded = true;
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, String.valueOf(error.getMessage()));
    }

    public SpanContext spanContext() {
        return span != null ? span.getSpanContext() : SpanContext.getInvalid();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (Scope restore = scope) {
            if (span != null && span.isRecording()) {
                if (!errorRecorded) {
                    span.setStatus(StatusCode.OK);
                }
                span.end();
            }
        }
    }
}
