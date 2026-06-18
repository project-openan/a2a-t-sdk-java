package net.openan.a2at.sample.client.registry;

import java.util.Map;

/**
 * Registry lookup contract used by the client sample flow.
 *
 * @since 2026-05
 */
public interface SampleRegistryClient {

    Map<String, Object> queryAgentCardByName(String name, String organization);
}


