package net.openan.a2at.sample.shared.endpoint;

import net.openan.a2at.sample.shared.error.ValueErrorException;

/**
 * Keeps the current resolved endpoint for the active sample run.
 *
 * @since 2026-05
 */
public final class AgentEndpointCache {
    private ResolvedAgentEndpoint current;

    public void setResolved(ResolvedAgentEndpoint endpoint) {
        this.current = endpoint;
    }

    public ResolvedAgentEndpoint getCurrent() {
        if (current == null) {
            throw new ValueErrorException("Agent endpoint cache is empty");
        }
        return current;
    }
}


