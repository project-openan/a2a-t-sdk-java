package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

/** Accessor for the active A2A-T span. */
public final class CurrentSpans {

    private CurrentSpans() {}

    public static @Nullable A2ATCurrentSpan current() {
        Span span = Span.current();
        if (!span.getSpanContext().isValid() || !span.isRecording()) {
            return null;
        }
        return new A2ATCurrentSpan(span);
    }
}
