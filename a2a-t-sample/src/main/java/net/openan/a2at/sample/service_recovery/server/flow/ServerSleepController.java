package net.openan.a2at.sample.service_recovery.server.flow;

/**
 * Controllable sleep abstraction used by the recurring mock stream loop.
 *
 * @since 2026-05
 */
@FunctionalInterface
public interface ServerSleepController {

    void sleepSeconds(long delaySeconds);
}
