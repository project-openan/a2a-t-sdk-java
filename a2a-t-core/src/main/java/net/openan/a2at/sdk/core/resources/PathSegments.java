package net.openan.a2at.sdk.core.resources;

import org.jspecify.annotations.Nullable;

/**
 * Validates path segment values used to compose classpath or filesystem resource paths.
 *
 * <p>A simple segment is non-null, non-blank and free of slashes, backslashes and {@code ..} sequences, so that it can
 * never escape the resource root it is resolved against.
 *
 * @since 2026-08
 */
public final class PathSegments {

    private PathSegments() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Checks whether the value is a non-blank simple path segment.
     *
     * @param value candidate path segment such as a language or category identifier
     * @return true when the value is non-null, non-blank and contains no slash, backslash or {@code ..} sequence
     */
    public static boolean isSimpleSegment(@Nullable String value) {
        return value != null
                && !value.isBlank()
                && !value.contains("/")
                && !value.contains("\\")
                && !value.contains("..");
    }

    /**
     * Requires the value to be a non-blank simple path segment, throwing an {@link IllegalArgumentException} otherwise.
     *
     * @param value candidate path segment such as a language or category identifier; may be {@code null}
     * @param label human-readable label describing the segment, used in the failure message
     * @throws IllegalArgumentException when the value is not a simple path segment
     */
    public static void requireSimpleSegment(@Nullable String value, String label) {
        if (!isSimpleSegment(value)) {
            throw new IllegalArgumentException(
                    label + " must be a non-blank simple path segment but was " + value + ".");
        }
    }

    /**
     * Requires the value to be a non-blank relative path whose slash-separated segments are each simple path segments,
     * throwing an {@link IllegalArgumentException} otherwise.
     *
     * @param value candidate relative path such as a scenario code; may be {@code null}
     * @param label human-readable label describing the path, used in the failure message
     * @throws IllegalArgumentException when the value is null, blank or contains a non-simple path segment
     */
    public static void requireSimpleRelativePath(@Nullable String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be a non-blank relative path but was " + value + ".");
        }
        for (String segment : value.split("/", -1)) {
            if (!isSimpleSegment(segment)) {
                throw new IllegalArgumentException(
                        label + " must contain only simple path segments but was " + value + ".");
            }
        }
    }
}
