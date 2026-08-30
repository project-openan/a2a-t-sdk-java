package net.openan.a2at.sample.service_recovery.server.registry;

import java.util.Map;

/**
 * Transport abstraction for the server registry client.
 *
 * @since 2026-05
 */
@FunctionalInterface
public interface ServerRegistryTransport {

    ServerRegistryResponse post(String url, Map<String, Object> payload);
}
