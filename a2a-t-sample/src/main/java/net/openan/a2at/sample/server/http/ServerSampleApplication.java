package net.openan.a2at.sample.server.http;

import java.util.Map;
import net.openan.a2at.sample.server.flow.PromptComplianceChecker;

/**
 * Minimal sample-owned application object for the publishable server sample.
 *
 * @since 2026-05
 */
public record ServerSampleApplication(Map<String, Object> agentCard, PromptComplianceChecker promptChecker) {
}


