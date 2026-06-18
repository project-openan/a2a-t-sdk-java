package net.openan.a2at.sample.server.runtime;

import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sample.server.flow.PromptComplianceChecker;

/**
 * Runtime assembly abstraction for the server sample bootstrap flow.
 *
 * @since 2026-05
 */
public interface SampleServerRuntime {

    ServerBind resolveBind();

    Map<String, Object> buildAgentCard(String host, int port);

    PromptComplianceChecker buildPromptChecker(Path envPath);

    Object buildApp(Map<String, Object> agentCard, PromptComplianceChecker promptChecker);

    Map<String, Object> registerAgentCard(Map<String, Object> registrationPayload, Path envPath);
}


