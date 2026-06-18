package net.openan.a2at.sample.server.flow;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.openan.a2at.sample.shared.logging.SampleLoggingFormatter;
import net.openan.a2at.sample.shared.error.ValueErrorException;
import net.openan.a2at.sdk.server.model.PromptComplianceFailure;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;

/**
 * Mock recurring incident-reporting flow for the server sample.
 *
 * @since 2026-05
 */
public final class ServerSampleFlow {
    static final String NOTIFICATION_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1";

    private static final long ARTIFACT_SEND_INTERVAL_SECONDS = 60L;

    private static final String SUBMITTED_MESSAGE = "Subscription accepted, starting Incident reporting task";

    private static final String WORKING_MESSAGE = "Incident reporting task in progress";

    private ServerSampleFlow() {
    }

    public static void executeServerFlow(
            RequestContext requestContext,
            PromptComplianceChecker promptChecker,
            AgentEmitter agentEmitter,
            ServerSleepController sleepController,
            Map<String, Object> incidentArtifactData,
            Consumer<String> logSink) {
        emit(
                logSink,
                SampleLoggingFormatter.formatPayloadLog(
                        "server",
                        "request-received",
                        Map.of(
                                "taskId", requestContext.getTaskId(),
                                "contextId", requestContext.getContextId(),
                                "message", requestContext.getMessage(),
                                "headers", extractHeaders(requestContext.getCallContext()))));
        requireNotificationExtension(requestContext.getCallContext());
        String taskId = requestContext.getTaskId();
        String contextId = requestContext.getContextId();
        String promptText = extractPromptText(requestContext);
        emit(logSink, SampleLoggingFormatter.formatStageLog("server", "prompt-extracted", promptText));
        PromptComplianceResult complianceResult = promptChecker.checkTaskPrompt(promptText);
        emit(
                logSink,
                SampleLoggingFormatter.formatStageLog(
                        "server",
                        "prompt-validation",
                        complianceResult.success()
                                ? "success"
                                : "failure=" + failureMessage(complianceResult.failure())));
        agentEmitter.submit(buildStatusMessage(contextId, taskId, SUBMITTED_MESSAGE));
        emit(logSink, SampleLoggingFormatter.formatStageLog("server", "task-status", "TASK_STATE_SUBMITTED"));
        if (!complianceResult.success()) {
            agentEmitter.reject(buildStatusMessage(
                    contextId,
                    taskId,
                    "Prompt validation failed: " + failureMessage(complianceResult.failure())));
            emit(logSink, SampleLoggingFormatter.formatStageLog("server", "task-status", "TASK_STATE_REJECTED"));
            return;
        }
        agentEmitter.startWork(buildStatusMessage(contextId, taskId, WORKING_MESSAGE));
        emit(logSink, SampleLoggingFormatter.formatStageLog("server", "task-status", "TASK_STATE_WORKING"));

        try {
            while (true) {
                String artifactId = UUID.randomUUID().toString();
                agentEmitter.addArtifact(
                        List.<Part<?>>of(new DataPart(incidentArtifactData)),
                        "faultManagement.Incident",
                        "Mock incident artifact",
                        Map.of("artifactId", artifactId),
                        false,
                        true);
                emit(logSink, SampleLoggingFormatter.formatStageLog("server", "artifact-emitted", artifactId));
                sleepController.sleepSeconds(ARTIFACT_SEND_INTERVAL_SECONDS);
            }
        } catch (ServerFlowInterruptedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            agentEmitter.fail(buildStatusMessage(contextId, taskId, "Mock incident stream failed: " + exception.getMessage()));
            emit(logSink, SampleLoggingFormatter.formatStageLog("server", "task-status", "TASK_STATE_FAILED"));
        }
    }

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(message);
        }
    }

    private static void requireNotificationExtension(ServerCallContext callContext) {
        Map<String, String> headers = extractHeaders(callContext);
        String modernValue = headers.get("A2A-Extensions");
        String legacyValue = headers.get("X-A2A-Extensions");
        if (!NOTIFICATION_T_EXTENSION_URI.equals(modernValue) && !NOTIFICATION_T_EXTENSION_URI.equals(legacyValue)) {
            throw new ValueErrorException("a2a client extensions is not exist.");
        }
    }

    private static String extractPromptText(RequestContext requestContext) {
        Message message = requestContext.getMessage();
        if (message == null || message.metadata() == null) {
            throw new ValueErrorException("Expected message metadata for Notification-T prompt");
        }
        return stringValue(message.metadata().get(NOTIFICATION_T_EXTENSION_URI));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> extractHeaders(ServerCallContext callContext) {
        Object headersValue = callContext.getState().get("headers");
        if (headersValue instanceof Map<?, ?> headersMap) {
            return (Map<String, String>) headersMap;
        }
        return Map.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Message buildStatusMessage(String contextId, String taskId, String text) {
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .contextId(contextId)
                .taskId(taskId)
                .role(Message.Role.ROLE_AGENT)
                .parts(new org.a2aproject.sdk.spec.TextPart(text))
                .build();
    }

    private static String failureMessage(PromptComplianceFailure failure) {
        return failure == null ? "unknown error" : failure.message();
    }
}


