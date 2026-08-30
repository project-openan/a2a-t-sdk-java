package net.openan.a2at.sample.service_recovery.server.flow;

import java.util.Map;
import java.util.function.Consumer;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;

/**
 * Real a2a-java agent executor for the service-recovery server flow.
 *
 * @since 2026-08
 */
public final class ServerSampleAgentExecutor implements AgentExecutor {
    private final NotificationPromptValidator promptValidator;

    private final ServerSleepController sleepController;

    private final Map<String, Object> recoveryEventArtifactData;

    private final Consumer<String> logSink;

    /**
     * Creates the agent executor.
     *
     * @param promptValidator notification prompt validator backed by the server SDK
     * @param sleepController controllable sleep used by the recurring artifact loop
     * @param recoveryEventArtifactData mock service recovery event payload emitted as artifacts
     * @param logSink log sink, may be null
     */
    public ServerSampleAgentExecutor(
            NotificationPromptValidator promptValidator,
            ServerSleepController sleepController,
            Map<String, Object> recoveryEventArtifactData,
            Consumer<String> logSink) {
        this.promptValidator = promptValidator;
        this.sleepController = sleepController;
        this.recoveryEventArtifactData = recoveryEventArtifactData;
        this.logSink = logSink;
    }

    @Override
    public void execute(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        ServerSampleFlow.executeServerFlow(
                requestContext, promptValidator, agentEmitter, sleepController, recoveryEventArtifactData, logSink);
    }

    @Override
    public void cancel(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        agentEmitter.cancel();
    }
}
