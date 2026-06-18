package net.openan.a2at.sample.shared.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Formatting helpers for sample logs.
 *
 * @since 2026-05
 */
public final class SampleLoggingFormatter {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final List<String> SECRET_KEYWORDS =
            List.of("api_key", "authorization", "token", "secret", "password");

    private SampleLoggingFormatter() {
    }

    public static String formatStageLog(String actor, String stage, String detail) {
        return timestamp() + " [" + actor + "] " + stage + ": " + detail;
    }

    public static String formatPayloadLog(String actor, String stage, Object payload) {
        try {
            Object normalized = normalizePayload(payload, null);
            return timestamp() + " [" + actor + "] " + stage + ": " + OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception exception) {
            return timestamp() + " [" + actor + "] " + stage + ": <payload-format-error: " + exception.getMessage()
                    + ">";
        }
    }

    private static String timestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    private static Object normalizePayload(Object value, String keyName) throws Exception {
        if (keyName != null && isSecretKey(keyName)) {
            return "***";
        }
        if (value == null || value instanceof Boolean || value instanceof Number || value instanceof String) {
            return value;
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> normalized = new TreeMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                String childKey = String.valueOf(entry.getKey());
                normalized.put(childKey, normalizePayload(entry.getValue(), childKey));
            }
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : collection) {
                normalized.add(normalizePayload(item, null));
            }
            return normalized;
        }
        if (value.getClass().isArray()) {
            List<Object> normalized = new ArrayList<>();
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                normalized.add(normalizePayload(Array.get(value, index), null));
            }
            return normalized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : iterable) {
                normalized.add(normalizePayload(item, null));
            }
            return normalized;
        }
        if (value instanceof java.util.Set<?> setValue) {
            TreeSet<String> normalized = new TreeSet<>();
            for (Object item : setValue) {
                normalized.add(String.valueOf(normalizePayload(item, null)));
            }
            return normalized;
        }

        return OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsBytes(value), Object.class);
    }

    private static boolean isSecretKey(String keyName) {
        String normalizedKeyName = keyName.toLowerCase(Locale.ROOT);
        for (String keyword : SECRET_KEYWORDS) {
            if (normalizedKeyName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}


