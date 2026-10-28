package net.openan.a2at.sdk.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serializes request/event objects to JSON for payload channels. */
public final class PayloadExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(PayloadExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PayloadExtractor() {}

    public static @Nullable String toJson(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            LOG.debug("a2at: payload extraction failed", e);
            return null;
        }
    }
}
