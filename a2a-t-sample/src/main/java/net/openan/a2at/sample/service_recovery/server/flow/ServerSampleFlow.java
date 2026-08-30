package net.openan.a2at.sample.service_recovery.server.flow;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.openan.a2at.sample.service_recovery.ServiceRecoverySampleInputs;
import net.openan.a2at.sample.service_recovery.shared.error.ValueErrorException;
import net.openan.a2at.sample.service_recovery.shared.logging.SampleLoggingFormatter;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;

/**
 * Mock recurring service-recovery-event reporting flow for the server sample.
 *
 * <p>Each received subscription request is validated with the server SDK's {@code validateAndFillingNotificationData}
 * API (extracting the subscription parameters), then the task reports mock service recovery events as artifacts. After
 * {@link ServiceRecoverySampleInputs#NOTIFICATION_REPORT_COUNT} reports the task reaches its terminal
 * {@code TASK_STATE_COMPLETED} state, so every subscription task — and with it every client stream — ends on its own
 * instead of reporting forever.
 *
 * <p>Console logging is trimmed to the essentials: the validation API output (the filled parameters) and the task
 * status transitions. The validation LLM request and response are logged by the shared LLM wrapper.
 *
 * @since 2026-08
 */
public final class ServerSampleFlow {
    static final String NOTIFICATION_T_EXTENSION_URI_NL =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/NL/v1";

    static final String NOTIFICATION_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1";

    private static final String SUBMITTED_MESSAGE =
            "Subscription accepted, starting service recovery event reporting task";

    private static final String WORKING_MESSAGE = "Service recovery event reporting task in progress";

    private static final String COMPLETE_MESSAGE =
            "Service recovery notification reporting finished, subscription task completed";

    private ServerSampleFlow() {}

    /**
     * Executes the server-side flow for one subscription request.
     *
     * @param requestContext a2a-java request context
     * @param promptValidator notification prompt validator backed by the server SDK
     * @param agentEmitter a2a-java emitter for status, message and artifact events
     * @param sleepController controllable sleep used by the recurring artifact loop
     * @param recoveryEventArtifactData mock service recovery event payload emitted as artifacts
     * @param logSink log sink, may be null
     */
    public static void executeServerFlow(
            RequestContext requestContext,
            NotificationPromptValidator promptValidator,
            AgentEmitter agentEmitter,
            ServerSleepController sleepController,
            Map<String, Object> recoveryEventArtifactData,
            Consumer<String> logSink) {
        requireNotificationExtension(requestContext.getCallContext());
        String taskId = requestContext.getTaskId();
        String contextId = requestContext.getContextId();
        String promptText = extractPromptText(requestContext);
        emit(logSink, "==================================================================");
        emit(logSink, "== API 2 validateAndFillingNotificationData");
        emit(logSink, "==================================================================");
        emit(logSink, "-- input prompt: <" + promptText.length() + " chars, see client-side logs>");

        FilledParamData filledParams;
        try {
            filledParams = promptValidator.validateNotificationPrompt(promptText);
            emit(logSink, "-- output (filled params):");
            emit(logSink, String.valueOf(filledParams.data()));
        } catch (ContentValidationException error) {
            emit(logSink, "!! validation failed: code=" + error.getCode() + " errors=" + error.errors());
            agentEmitter.submit(buildStatusMessage(contextId, taskId, SUBMITTED_MESSAGE));
            agentEmitter.reject(buildStatusMessage(
                    contextId,
                    taskId,
                    "Notification validation failed: " + error.getCode() + ": " + error.getMessage()));
            return;
        } catch (RuntimeException error) {
            emit(
                    logSink,
                    "!! validation failed unexpectedly: " + error.getClass().getName() + ": " + error.getMessage());
            agentEmitter.submit(buildStatusMessage(contextId, taskId, SUBMITTED_MESSAGE));
            agentEmitter.reject(buildStatusMessage(
                    contextId,
                    taskId,
                    "Notification validation failed unexpectedly: "
                            + error.getClass().getName() + ": " + error.getMessage()));
            return;
        }

        agentEmitter.submit(buildStatusMessage(contextId, taskId, SUBMITTED_MESSAGE));
        agentEmitter.startWork(buildStatusMessage(contextId, taskId, WORKING_MESSAGE));
        emit(logSink, ">> subscription accepted, reporting service recovery events");

        try {
            for (int reportIndex = 1;
                    reportIndex <= ServiceRecoverySampleInputs.NOTIFICATION_REPORT_COUNT;
                    reportIndex++) {
                String artifactId = UUID.randomUUID().toString();
                agentEmitter.addArtifact(
                        List.<Part<?>>of(new DataPart(recoveryEventArtifactData)),
                        "serviceManagement.ServiceRecoveryEvent",
                        "Mock service recovery event artifact",
                        Map.of("artifactId", artifactId),
                        false,
                        true);
                emit(
                        logSink,
                        "- artifact " + reportIndex + "/" + ServiceRecoverySampleInputs.NOTIFICATION_REPORT_COUNT
                                + " emitted (" + artifactId + ")");
                if (reportIndex < ServiceRecoverySampleInputs.NOTIFICATION_REPORT_COUNT) {
                    sleepController.sleepSeconds(ServiceRecoverySampleInputs.NOTIFICATION_REPORT_INTERVAL_SECONDS);
                }
            }
            agentEmitter.complete(buildStatusMessage(contextId, taskId, COMPLETE_MESSAGE));
            emit(
                    logSink,
                    "<< subscription task completed after " + ServiceRecoverySampleInputs.NOTIFICATION_REPORT_COUNT
                            + " reports");
        } catch (ServerFlowInterruptedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            agentEmitter.fail(buildStatusMessage(
                    contextId, taskId, "Mock service recovery stream failed: " + exception.getMessage()));
        }
    }

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(SampleLoggingFormatter.timestamped(message));
        }
    }

    private static void requireNotificationExtension(ServerCallContext callContext) {
        Map<String, String> headers = extractHeaders(callContext);
        String modernValue = headers.get("A2A-Extensions");
        String legacyValue = headers.get("X-A2A-Extensions");
        String extensionValue = modernValue != null ? modernValue : legacyValue;
        if (!NOTIFICATION_T_EXTENSION_URI_NL.equals(extensionValue)
                && !NOTIFICATION_T_EXTENSION_URI.equals(extensionValue)) {
            throw new ValueErrorException("a2a client extensions is not exist.");
        }
    }

    private static String extractPromptText(RequestContext requestContext) {
        Message message = requestContext.getMessage();
        if (message == null || message.metadata() == null) {
            throw new ValueErrorException("Expected message metadata for Notification-T prompt");
        }
        String promptText = stringValue(message.metadata().get(NOTIFICATION_T_EXTENSION_URI_NL));
        if (promptText.isEmpty()) {
            promptText = stringValue(message.metadata().get(NOTIFICATION_T_EXTENSION_URI));
        }
        return promptText;
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
}
