package net.openan.a2at.sample.shared.error;

/**
 * Stable runtime exception used by the sample support layer for invalid sample inputs.
 *
 * @since 2026-05
 */
public final class ValueErrorException extends RuntimeException {

    public ValueErrorException(String message) {
        super(message);
    }
}


