package net.openan.a2at.sample.service_recovery.client.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.service_recovery.ServiceRecoverySampleInputs;
import net.openan.a2at.sample.service_recovery.VerificationCheck;
import net.openan.a2at.sample.service_recovery.client.prompt.SamplePromptClient;
import net.openan.a2at.sample.service_recovery.client.registry.SampleRegistryClient;
import net.openan.a2at.sample.service_recovery.client.request.A2AJavaRequestBuilder;
import net.openan.a2at.sample.service_recovery.client.request.BuiltA2AJavaRequest;
import net.openan.a2at.sample.service_recovery.client.runtime.A2AJavaClientRuntime;
import net.openan.a2at.sample.service_recovery.shared.endpoint.AgentEndpointResolver;
import net.openan.a2at.sample.service_recovery.shared.endpoint.ResolvedAgentEndpoint;
import net.openan.a2at.sample.service_recovery.shared.logging.SampleLoggingFormatter;
import net.openan.a2at.sample.service_recovery.shared.stream.SampleStreamEventNormalizer;
import net.openan.a2at.sdk.core.model.MetadataContent;
import org.a2aproject.sdk.client.ClientEvent;

/**
 * Core client-side orchestration for the service-recovery e2e sample.
 *
 * <p>One client process runs two independent subscription rounds, so each generated prompt is validated by the server
 * on its own merits — one API producing a usable prompt can never mask the other producing a broken one:
 *
 * <ol>
 *   <li>{@code generateNotificationPromptFromText} — natural-language subscription request to a rendered prompt, sent
 *       to the server over the real a2a-java transport;
 *   <li>{@code generateNotificationPromptFromDataWithSchema} — structured input plus data schema to a rendered prompt,
 *       sent through the same chain.
 *       <p>Each round's server task reports {@link ServiceRecoverySampleInputs#NOTIFICATION_REPORT_COUNT} notification
 *       artifacts and then reaches its terminal {@code TASK_STATE_COMPLETED} state, so every stream ends on its own;
 *       the client only stops earlier when {@code maxArtifacts} is positive.
 *       <p>Console logging is trimmed to the essentials: the API inputs and outputs, the LLM requests and responses
 *       (logged by the shared LLM wrapper), and the received notification artifacts in full detail. Stage lines carry a
 *       small set of markers for fast visual scanning.
 *
 * @since 2026-08
 */
public final class ClientSampleFlow {

    private ClientSampleFlow() {}

    /**
     * Runs the client flow.
     *
     * @param registryClient registry client used to resolve the server AgentCard
     * @param promptClient prompt-generation bridge
     * @param a2aRuntime a2a-java client runtime
     * @param logSink log sink, may be null
     * @param maxArtifacts stop each round after so many artifacts when positive
     * @return client flow outcome with verification checks and received events
     */
    public static ClientFlowOutcome runClientFlow(
            SampleRegistryClient registryClient,
            SamplePromptClient promptClient,
            A2AJavaClientRuntime a2aRuntime,
            Consumer<String> logSink,
            int maxArtifacts) {
        List<VerificationCheck> checks = new ArrayList<>();
        Map<String, Object> agentCard = registryClient.queryAgentCardByName(
                ServiceRecoverySampleInputs.AGENT_NAME, ServiceRecoverySampleInputs.AGENT_ORGANIZATION);
        ResolvedAgentEndpoint endpoint = AgentEndpointResolver.resolvePreferredInterface(agentCard);
        emit(logSink, "> endpoint: " + endpoint.url() + " (" + endpoint.protocolBinding() + ")");

        List<Map<String, Object>> normalizedEvents = new ArrayList<>();

        MetadataContent fromText = verifyGenerateFromText(promptClient, logSink, checks);
        if (fromText != null) {
            runSubscriptionRound(
                    "fromText", fromText, a2aRuntime, agentCard, logSink, checks, maxArtifacts, normalizedEvents);
        } else {
            checks.add(VerificationCheck.failed(
                    "fromText: a2a request sent",
                    "skipped, the from-text generation step produced no rendered prompt"));
        }

        MetadataContent fromData = verifyGenerateFromData(promptClient, logSink, checks);
        if (fromData != null) {
            runSubscriptionRound(
                    "fromData", fromData, a2aRuntime, agentCard, logSink, checks, maxArtifacts, normalizedEvents);
        } else {
            checks.add(VerificationCheck.failed(
                    "fromData: a2a request sent",
                    "skipped, the from-data generation step produced no rendered prompt"));
        }

        return new ClientFlowOutcome(checks, normalizedEvents);
    }

    /**
     * Runs one subscription round: sends the rendered prompt as an A2A message:stream request and collects the server
     * events until the task completes (or the artifact budget is reached).
     *
     * @param label round label used in check names and logs
     * @param content rendered prompt of this round
     * @param a2aRuntime a2a-java client runtime
     * @param agentCard resolved registry AgentCard payload
     * @param logSink log sink, may be null
     * @param checks verification checks collected so far
     * @param maxArtifacts stop this round after so many artifacts when positive
     * @param normalizedEvents collected normalized events across rounds
     */
    private static void runSubscriptionRound(
            String label,
            MetadataContent content,
            A2AJavaClientRuntime a2aRuntime,
            Map<String, Object> agentCard,
            Consumer<String> logSink,
            List<VerificationCheck> checks,
            int maxArtifacts,
            List<Map<String, Object>> normalizedEvents) {
        emit(logSink, ">> " + label + " subscription request sent (A2A message:stream)");
        BuiltA2AJavaRequest builtRequest = A2AJavaRequestBuilder.buildStreamRequest(
                content.promptText(), content.extensionUri(), "create service recovery subscription");
        checks.add(VerificationCheck.passed(label + ": a2a request sent", "extension=" + content.extensionUri()));

        int artifactCount = 0;
        int eventCount = 0;
        String terminalState = "";
        try {
            for (ClientEvent rawEvent :
                    a2aRuntime.sendMessage(agentCard, builtRequest.request(), builtRequest.callContext(), logSink)) {
                Map<String, Object> payload = A2AJavaClientEventMapper.toPayload(rawEvent);
                Map<String, Object> normalizedEvent = SampleStreamEventNormalizer.normalize(payload);
                normalizedEvents.add(normalizedEvent);
                eventCount++;
                if ("status".equals(normalizedEvent.get("kind"))) {
                    terminalState = String.valueOf(normalizedEvent.get("state"));
                    emit(logSink, "- task-status: " + terminalState);
                } else if ("artifact".equals(normalizedEvent.get("kind"))) {
                    // The received notification is the sample's deliverable: log it in full detail.
                    emit(logSink, "* notification received (artifact " + (artifactCount + 1) + "):");
                    emit(logSink, formatArtifact(normalizedEvent.get("artifact")));
                    artifactCount++;
                    if (maxArtifacts > 0 && artifactCount >= maxArtifacts) {
                        emit(logSink, "- reached max-artifacts (" + maxArtifacts + "), stopping this round");
                        break;
                    }
                }
            }
        } catch (RuntimeException error) {
            checks.add(VerificationCheck.failed(label + ": a2a response received", describe(error)));
            return;
        }
        int expectedArtifacts = maxArtifacts > 0 ? maxArtifacts : ServiceRecoverySampleInputs.NOTIFICATION_REPORT_COUNT;
        checks.add(VerificationCheck.passed(
                label + ": a2a response received", "events=" + eventCount + " artifacts=" + artifactCount));
        checks.add(expectation(
                label + ": notification artifacts collected",
                artifactCount >= expectedArtifacts,
                "expected " + expectedArtifacts + ", got " + artifactCount));
        if (maxArtifacts > 0) {
            checks.add(VerificationCheck.passed(
                    label + ": subscription task completed",
                    "skipped, client stopped early after " + maxArtifacts + " artifact(s); the server task "
                            + "self-completes after " + ServiceRecoverySampleInputs.NOTIFICATION_REPORT_COUNT
                            + " reports"));
        } else {
            checks.add(expectation(
                    label + ": subscription task completed",
                    "TASK_STATE_COMPLETED".equals(terminalState),
                    "terminal task state was " + terminalState));
        }
    }

    private static MetadataContent verifyGenerateFromText(
            SamplePromptClient promptClient, Consumer<String> logSink, List<VerificationCheck> checks) {
        String promptInput = ServiceRecoverySampleInputs.naturalLanguageInput();
        emit(logSink, "==================================================================");
        emit(logSink, "== API 1 generateNotificationPromptFromText");
        emit(logSink, "==================================================================");
        emit(logSink, "-- input (natural language):");
        emit(logSink, promptInput);
        try {
            MetadataContent content = promptClient.generateNotificationPromptFromText(
                    promptInput, ServiceRecoverySampleInputs.TEMPLATE_URI);
            emit(logSink, "-- output (rendered subscription prompt):");
            emit(logSink, content.promptText());
            emit(logSink, "-- templateUri=" + content.templateUri() + "  extensionUri=" + content.extensionUri());

            checks.add(VerificationCheck.passed("fromText: generation returned", "no exception"));
            addGenerationChecks(checks, "fromText", content);
            return content;
        } catch (RuntimeException error) {
            checks.add(VerificationCheck.failed("fromText: generation returned", describe(error)));
            return null;
        }
    }

    private static MetadataContent verifyGenerateFromData(
            SamplePromptClient promptClient, Consumer<String> logSink, List<VerificationCheck> checks) {
        Map<String, Object> data = ServiceRecoverySampleInputs.structuredInput();
        Map<String, Object> schema = ServiceRecoverySampleInputs.dataSchema();
        emit(logSink, "==================================================================");
        emit(logSink, "== API 1' generateNotificationPromptFromDataWithSchema");
        emit(logSink, "==================================================================");
        emit(logSink, "-- input data:");
        emit(logSink, String.valueOf(data));
        emit(logSink, "-- input data schema:");
        emit(logSink, String.valueOf(schema));
        try {
            MetadataContent content = promptClient.generateNotificationPromptFromDataWithSchema(
                    data, schema, ServiceRecoverySampleInputs.TEMPLATE_URI);
            emit(logSink, "-- output (rendered subscription prompt):");
            emit(logSink, content.promptText());
            emit(logSink, "-- templateUri=" + content.templateUri() + "  extensionUri=" + content.extensionUri());

            checks.add(VerificationCheck.passed("fromData: generation returned", "no exception"));
            addGenerationChecks(checks, "fromData", content);
            return content;
        } catch (RuntimeException error) {
            checks.add(VerificationCheck.failed("fromData: generation returned", describe(error)));
            return null;
        }
    }

    private static String formatArtifact(Object artifact) {
        return String.valueOf(artifact);
    }

    private static void addGenerationChecks(List<VerificationCheck> checks, String label, MetadataContent content) {
        checks.add(expectation(
                label + ": templateUri matches",
                ServiceRecoverySampleInputs.TEMPLATE_URI.equals(content.templateUri()),
                "expected " + ServiceRecoverySampleInputs.TEMPLATE_URI + ", got " + content.templateUri()));
        checks.add(expectation(
                label + ": prompt text is non-blank",
                content.promptText() != null && !content.promptText().isBlank(),
                "promptText blank"));
        checks.add(expectation(
                label + ": prompt carries the recovery plan execution status",
                content.promptText() != null && content.promptText().contains("业务抢通方案执行状态"),
                "promptText does not carry the required " + "业务抢通方案执行状态" + " slot"));
    }

    private static VerificationCheck expectation(String name, boolean passed, String failureDetail) {
        return passed ? VerificationCheck.passed(name, "ok") : VerificationCheck.failed(name, failureDetail);
    }

    private static String describe(RuntimeException error) {
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(SampleLoggingFormatter.timestamped(message));
        }
    }
}
