package net.openan.a2at.sample.server.runtime;

import java.util.Map;

/**
 * Result of one server-sample bootstrap sequence.
 *
 * @since 2026-05
 */
public record ServerBootstrapResult(
        String host,
        int port,
        Object app,
        AutoCloseable serverHandle,
        Map<String, Object> registrationResult) {
}


