package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** W3C TraceContext propagation over plain header maps. */
public final class TracePropagation {

    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final TextMapSetter<Map<String, String>> SETTER = (carrier, key, value) -> {
        if (carrier != null) {
            carrier.put(key, value);
        }
    };
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public @Nullable String get(@Nullable Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private TracePropagation() {}

    public static void inject(Map<String, String> headers) {
        if (headers == null || !ObservabilityRuntime.isEnabled()) {
            return;
        }
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), headers, SETTER);
    }

    public static void inject(Map<String, String> headers, Context context) {
        if (headers == null || context == null || !ObservabilityRuntime.isEnabled()) {
            return;
        }
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, SETTER);
    }

    public static @Nullable Context extract(@Nullable Map<String, String> headers) {
        if (headers == null || !ObservabilityRuntime.isEnabled()) {
            return null;
        }
        Map<String, String> lowered = new HashMap<>();
        headers.forEach((key, value) -> lowered.put(key.toLowerCase(Locale.ROOT), value));
        Context extracted = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.root(), lowered, GETTER);
        return Span.fromContext(extracted).getSpanContext().isValid() ? extracted : null;
    }
}
