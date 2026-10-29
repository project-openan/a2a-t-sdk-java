package net.openan.a2at.sdk.observability;

import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared payload preparation: redact first, then truncate (security order). */
public final class PayloadSanitizer {

    private static final Logger LOG = LoggerFactory.getLogger(PayloadSanitizer.class);

    private PayloadSanitizer() {}

    public static String prepare(String payload, A2ATObservabilityConfig config) {
        String value = payload;
        UnaryOperator<String> redactor = config.payloadRedactor();
        if (redactor != null) {
            try {
                value = redactor.apply(value);
            } catch (RuntimeException e) {
                LOG.warn("a2at: payload redactor failed; using unredacted value", e);
            }
        }
        int max = config.payloadLogMaxLength();
        if (value.length() > max) {
            return value.substring(0, max) + "[truncated]";
        }
        return value;
    }
}
