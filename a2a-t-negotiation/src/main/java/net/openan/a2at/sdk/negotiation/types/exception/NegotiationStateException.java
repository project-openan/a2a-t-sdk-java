package net.openan.a2at.sdk.negotiation.types.exception;

import net.openan.a2at.sdk.core.exception.SdkException;

/**
 * Raised when incoming negotiation state skips or contradicts local progress.
 *
 * @since 2026-06
 */
public final class NegotiationStateException extends SdkException {

    public NegotiationStateException(String message) {
        super(message);
    }
}
