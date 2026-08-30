package net.openan.a2at.sample.service_recovery.server.runtime;

/**
 * Transitional seam for assembling the real a2a-java REST server runtime.
 *
 * @since 2026-05
 */
public interface A2AJavaServerRuntime {

    Object createRestApplication(String host, int port);
}
