package net.openan.a2at.sample.service_recovery.shared.endpoint;

/**
 * Resolved A2A endpoint details extracted from a registry AgentCard payload.
 *
 * @since 2026-05
 */
public record ResolvedAgentEndpoint(String agentName, String protocolBinding, String protocolVersion, String url) {}
