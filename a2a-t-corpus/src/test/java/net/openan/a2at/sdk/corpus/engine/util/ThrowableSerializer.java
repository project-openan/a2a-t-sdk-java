package net.openan.a2at.sdk.corpus.engine.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;

/**
 * Serializes a thrown error into a structured map carrying every inspectable property, per the transcript contract:
 * exception class, machine-readable error code for {@link A2ATError} instances, message, all public getter values and
 * the cause chain.
 */
public final class ThrowableSerializer {

    private static final int MAX_CAUSE_DEPTH = 3;

    private ThrowableSerializer() {}

    public static Map<String, Object> toMap(Throwable error) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        fill(serialized, error, 0);
        return serialized;
    }

    private static void fill(Map<String, Object> target, Throwable error, int depth) {
        target.put("exception", error.getClass().getName());
        target.put("message", error.getMessage());
        if (error instanceof A2ATError a2atError) {
            target.put("errorCode", a2atError.getCode());
        }
        target.put("properties", propertiesOf(error));
        Throwable cause = error.getCause();
        if (cause != null && depth < MAX_CAUSE_DEPTH) {
            Map<String, Object> causeMap = new LinkedHashMap<>();
            fill(causeMap, cause, depth + 1);
            target.put("cause", causeMap);
        }
    }

    private static Map<String, Object> propertiesOf(Throwable error) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Method method : error.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = method.getName();
            if (!name.startsWith("get") && !name.startsWith("is")) {
                continue;
            }
            if (EXCLUDED.contains(name)) {
                continue;
            }
            Object value;
            try {
                value = method.invoke(error);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                continue;
            }
            if (value == null || value instanceof Throwable) {
                continue;
            }
            properties.put(propertyName(name), value);
        }
        return properties;
    }

    private static String propertyName(String getterName) {
        String base = getterName.startsWith("is") ? getterName.substring(2) : getterName.substring(3);
        if (base.isEmpty()) {
            return getterName;
        }
        return Character.toLowerCase(base.charAt(0)) + base.substring(1);
    }

    private static final java.util.Set<String> EXCLUDED = java.util.Set.of(
            "getClass",
            "getMessage",
            "getLocalizedMessage",
            "getCause",
            "getStackTrace",
            "getSuppressed",
            "getCode");
}