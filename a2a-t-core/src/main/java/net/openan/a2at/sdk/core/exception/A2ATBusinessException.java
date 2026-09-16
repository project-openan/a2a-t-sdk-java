package net.openan.a2at.sdk.core.exception;

import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Base type for expected business failures raised by the A2A-T SDK.
 *
 * <p>Business failures carry a machine-readable code from {@link ErrorCatalog} and a message rendered from the code's
 * message template (see {@link ErrorMessages}); the structured fact values that produced the message travel in
 * {@link #getFacts()}. Programming errors (null or malformed arguments) stay {@link NullPointerException} and
 * {@link IllegalArgumentException}; infrastructure failures stay plain {@link A2ATError}.
 *
 * @since 2026-08
 */
public class A2ATBusinessException extends A2ATError {

    private final Map<String, String> facts;

    /**
     * Creates a business failure for one catalog entry with a rendered message.
     *
     * @param entry catalog entry identifying the failure
     * @param message rendered failure message
     * @throws NullPointerException if {@code entry} or {@code message} is null
     */
    public A2ATBusinessException(@NonNull ErrorCatalog entry, @NonNull String message) {
        this(entry.getCode(), message, Map.of());
    }

    /**
     * Creates a business failure for one catalog entry with a rendered message and its fact values.
     *
     * @param entry catalog entry identifying the failure
     * @param message rendered failure message
     * @param facts fact values keyed by fact parameter name, may be null
     * @throws NullPointerException if {@code entry} or {@code message} is null
     */
    public A2ATBusinessException(
            @NonNull ErrorCatalog entry, @NonNull String message, @Nullable Map<String, String> facts) {
        this(entry.getCode(), message, facts);
    }

    /**
     * Creates a business failure for one error code with a rendered message.
     *
     * @param code machine-readable error code from {@link ErrorCatalog}
     * @param message rendered failure message
     * @throws NullPointerException if {@code code} or {@code message} is null
     */
    public A2ATBusinessException(@NonNull String code, @NonNull String message) {
        this(code, message, null);
    }

    /**
     * Creates a business failure for one error code with a rendered message and its fact values.
     *
     * @param code machine-readable error code from {@link ErrorCatalog}
     * @param message rendered failure message
     * @param facts fact values keyed by fact parameter name, may be null
     * @throws NullPointerException if {@code code} or {@code message} is null
     */
    public A2ATBusinessException(@NonNull String code, @NonNull String message, @Nullable Map<String, String> facts) {
        super(code, message);
        this.facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    /**
     * Creates a business failure for one error code with a rendered message, its fact values and a root cause.
     *
     * @param code machine-readable error code from {@link ErrorCatalog}
     * @param message rendered failure message
     * @param facts fact values keyed by fact parameter name, may be null
     * @param cause root cause, may be null
     * @throws NullPointerException if {@code code} or {@code message} is null
     */
    public A2ATBusinessException(
            @NonNull String code,
            @NonNull String message,
            @Nullable Map<String, String> facts,
            @Nullable Throwable cause) {
        super(code, message, cause);
        this.facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    /**
     * Returns the fact values that produced the rendered message.
     *
     * @return facts keyed by fact parameter name, possibly empty, never null
     */
    public @NonNull Map<String, String> getFacts() {
        return facts;
    }

    /**
     * Creates a business failure whose message is rendered from the code's template in one language.
     *
     * @param entry catalog entry identifying the failure
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     * @param facts fact values keyed by fact parameter name, may be null
     * @return business failure with the rendered message
     * @throws NullPointerException if {@code entry} is null
     */
    public static A2ATBusinessException of(
            @NonNull ErrorCatalog entry, @Nullable String language, @Nullable Map<String, String> facts) {
        return new A2ATBusinessException(entry, ErrorMessages.render(entry, language, facts), facts);
    }
}
