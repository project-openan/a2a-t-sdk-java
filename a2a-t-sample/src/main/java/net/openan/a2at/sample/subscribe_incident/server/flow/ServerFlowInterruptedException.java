package net.openan.a2at.sample.subscribe_incident.server.flow;

/**
 * Internal control exception used by tests to stop the recurring artifact loop.
 *
 * @since 2026-05
 */
public final class ServerFlowInterruptedException extends RuntimeException {

    public ServerFlowInterruptedException(String message) {
        super(message);
    }
}
