package net.openan.a2at.sample.server.flow;

import java.util.Map;
import java.util.function.Consumer;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;

/**
 * Real a2a-java agent executor for the sample server flow.
 *
 * @since 2026-05
 */
public final class ServerSampleAgentExecutor implements AgentExecutor {
    private final PromptComplianceChecker promptChecker;

    private final ServerSleepController sleepController;

    private final Map<String, Object> incidentArtifactData;

    private final Consumer<String> logSink;

    public ServerSampleAgentExecutor(
            PromptComplianceChecker promptChecker,
            ServerSleepController sleepController,
            Map<String, Object> incidentArtifactData,
            Consumer<String> logSink) {
        this.promptChecker = promptChecker;
        this.sleepController = sleepController;
        this.incidentArtifactData = incidentArtifactData;
        this.logSink = logSink;
    }

    @Override
    public void execute(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        ServerSampleFlow.executeServerFlow(
                requestContext,
                promptChecker,
                agentEmitter,
                sleepController,
                incidentArtifactData,
                logSink);
    }

    @Override
    public void cancel(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        agentEmitter.cancel();
    }
}


