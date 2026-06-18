package net.openan.a2at.sample.server.registry;

import java.util.Map;

/**
 * Simplified response model for registry registration calls.
 *
 * @since 2026-05
 */
public record ServerRegistryResponse(int statusCode, String text, Map<String, Object> jsonBody) {
}


