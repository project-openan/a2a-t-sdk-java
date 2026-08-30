package net.openan.a2at.sample.subscribe_incident.client.runtime;

import net.openan.a2at.sample.subscribe_incident.client.prompt.SamplePromptClient;
import net.openan.a2at.sample.subscribe_incident.client.registry.SampleRegistryClient;
import net.openan.a2at.sample.subscribe_incident.shared.endpoint.AgentEndpointCache;

/**
 * Runtime bundle required by the client sample main flow.
 *
 * @since 2026-05
 */
public interface SampleClientRuntime extends AutoCloseable {

    SampleRegistryClient registryClient();

    SamplePromptClient promptClient();

    AgentEndpointCache endpointCache();

    @Override
    void close();
}
