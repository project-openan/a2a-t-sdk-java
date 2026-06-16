package net.openan.a2at.sdk.core.model;

import java.util.Objects;
import net.openan.a2at.sdk.core.exception.SdkException;

/**
 * Minimal structured result wrapper used by higher-level orchestration flows.
 *
 * @param <T> result value type
 * @since 2026-05
 */
public record OperationResult<T>(T value, SdkException error) {

    /**
     * Creates a successful operation result.
     *
     * @param value operation value
     * @param <T> value type
     * @return successful result wrapper
     */
    public static <T> OperationResult<T> success(T value) {
        return new OperationResult<>(value, null);
    }

    /**
     * Creates a failed operation result.
     *
     * @param error SDK error describing the failure
     * @param <T> value type
     * @return failed result wrapper
     */
    public static <T> OperationResult<T> failure(SdkException error) {
        return new OperationResult<>(null, Objects.requireNonNull(error, "error"));
    }

    /**
     * Indicates whether the operation completed without an SDK error.
     *
     * @return {@code true} when {@link #error()} is {@code null}
     */
    public boolean isSuccess() {
        return error == null;
    }
}
