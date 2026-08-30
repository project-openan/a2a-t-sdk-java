package net.openan.a2at.sample.service_recovery.client.flow;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.service_recovery.VerificationCheck;

/**
 * Outcome of one client sample run.
 *
 * @param checks verification checks collected from both generation steps and the request build
 * @param events normalized stream events received from the server, in arrival order
 * @since 2026-08
 */
public record ClientFlowOutcome(List<VerificationCheck> checks, List<Map<String, Object>> events) {}
