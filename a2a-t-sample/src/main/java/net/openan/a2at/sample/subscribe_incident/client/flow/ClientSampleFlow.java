package net.openan.a2at.sample.subscribe_incident.client.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.subscribe_incident.client.prompt.SamplePromptClient;
import net.openan.a2at.sample.subscribe_incident.client.registry.SampleRegistryClient;
import net.openan.a2at.sample.subscribe_incident.client.request.A2AJavaRequestBuilder;
import net.openan.a2at.sample.subscribe_incident.client.request.BuiltA2AJavaRequest;
import net.openan.a2at.sample.subscribe_incident.client.runtime.A2AJavaClientRuntime;
import net.openan.a2at.sample.subscribe_incident.shared.endpoint.AgentEndpointCache;
import net.openan.a2at.sample.subscribe_incident.shared.endpoint.AgentEndpointResolver;
import net.openan.a2at.sample.subscribe_incident.shared.endpoint.ResolvedAgentEndpoint;
import net.openan.a2at.sample.subscribe_incident.shared.error.ValueErrorException;
import net.openan.a2at.sample.subscribe_incident.shared.logging.SampleLoggingFormatter;
import net.openan.a2at.sample.subscribe_incident.shared.stream.SampleStreamEventNormalizer;
import net.openan.a2at.sdk.client.model.PromptGenerationFailure;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;
import org.a2aproject.sdk.client.ClientEvent;

/**
 * Core client-side orchestration for the publishable sample module.
 *
 * @since 2026-05
 */
public final class ClientSampleFlow {
    static final String NOTIFICATION_T_EXTENSION_URI_NL =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/NL/v1";

    static final String NATURAL_LANGUAGE_PROMPT_INPUT_ZH =
            "请生成一个Incident事件订阅任务：通知主题为Incident，订阅条件为订阅级别为critical的ETH-LOS的故障，上报通知数据格式为DataPart";

    static final String NATURAL_LANGUAGE_PROMPT_INPUT_EN =
            "Generate an Incident event subscription task: notification topic is Incident, "
                    + "subscription condition is a critical ETH-LOS fault, "
                    + "and the notification data format is DataPart";

    private static final String LANGUAGE_EN_US = "en-US";

    private ClientSampleFlow() {}

    public static List<Map<String, Object>> runClientFlow(
            Map<String, Object> scenarioPayload,
            SampleRegistryClient registryClient,
            SamplePromptClient promptClient,
            A2AJavaClientRuntime a2aRuntime,
            AgentEndpointCache endpointCache,
            Consumer<String> logSink) {
        return runClientFlow(
                scenarioPayload, registryClient, promptClient, a2aRuntime, endpointCache, logSink, 0, "zh-CN");
    }

    public static List<Map<String, Object>> runClientFlow(
            Map<String, Object> scenarioPayload,
            SampleRegistryClient registryClient,
            SamplePromptClient promptClient,
            A2AJavaClientRuntime a2aRuntime,
            AgentEndpointCache endpointCache,
            Consumer<String> logSink,
            int maxArtifacts) {
        return runClientFlow(
                scenarioPayload,
                registryClient,
                promptClient,
                a2aRuntime,
                endpointCache,
                logSink,
                maxArtifacts,
                "zh-CN");
    }

    public static List<Map<String, Object>> runClientFlow(
            Map<String, Object> scenarioPayload,
            SampleRegistryClient registryClient,
            SamplePromptClient promptClient,
            A2AJavaClientRuntime a2aRuntime,
            AgentEndpointCache endpointCache,
            Consumer<String> logSink,
            int maxArtifacts,
            String language) {
        AgentCardQuery query = resolveAgentCardQuery(scenarioPayload);
        Map<String, Object> agentCard = registryClient.queryAgentCardByName(query.name(), query.organization());
        emit(logSink, SampleLoggingFormatter.formatPayloadLog("client", "agentcard-result", agentCard));

        ResolvedAgentEndpoint endpoint = AgentEndpointResolver.resolvePreferredInterface(agentCard);
        endpointCache.setResolved(endpoint);
        emit(
                logSink,
                SampleLoggingFormatter.formatStageLog(
                        "client",
                        "agentcard-resolved",
                        "url=" + endpoint.url() + " binding=" + endpoint.protocolBinding()));

        String promptInput = buildPromptInput(language);
        emit(logSink, SampleLoggingFormatter.formatPayloadLog("client", "prompt-input", promptInput));
        PromptGenerationResult promptResult = promptClient.generateTaskPrompt(promptInput);
        emit(logSink, SampleLoggingFormatter.formatPayloadLog("client", "prompt-generation-result", promptResult));
        String promptText = requirePromptText(promptResult);

        BuiltA2AJavaRequest builtRequest = A2AJavaRequestBuilder.buildStreamRequest(
                promptText, NOTIFICATION_T_EXTENSION_URI_NL, buildRequestMetadata(scenarioPayload));
        emit(logSink, SampleLoggingFormatter.formatPayloadLog("client", "a2a-request-body", builtRequest.request()));
        emit(
                logSink,
                SampleLoggingFormatter.formatPayloadLog(
                        "client", "request-headers", builtRequest.callContext().getHeaders()));
        emit(
                logSink,
                SampleLoggingFormatter.formatStageLog(
                        "client",
                        "request-built",
                        "extension=" + builtRequest.callContext().getHeaders().get("A2A-Extensions")));

        List<Map<String, Object>> normalizedEvents = new ArrayList<>();
        int artifactCount = 0;
        for (ClientEvent rawEvent :
                a2aRuntime.sendMessage(agentCard, builtRequest.request(), builtRequest.callContext(), logSink)) {
            Map<String, Object> payload = A2AJavaClientEventMapper.toPayload(rawEvent);
            Map<String, Object> normalizedEvent = SampleStreamEventNormalizer.normalize(payload);
            normalizedEvents.add(normalizedEvent);
            if ("status".equals(normalizedEvent.get("kind"))) {
                emit(
                        logSink,
                        SampleLoggingFormatter.formatStageLog(
                                "client", "task-status", String.valueOf(normalizedEvent.get("state"))));
            } else if ("message".equals(normalizedEvent.get("kind"))) {
                emit(
                        logSink,
                        SampleLoggingFormatter.formatStageLog(
                                "client", "task-message", String.valueOf(normalizedEvent.get("text"))));
            } else if ("artifact".equals(normalizedEvent.get("kind"))) {
                emit(
                        logSink,
                        SampleLoggingFormatter.formatPayloadLog(
                                "client", "task-artifact", normalizedEvent.get("artifact")));
                artifactCount++;
                if (maxArtifacts > 0 && artifactCount >= maxArtifacts) {
                    emit(logSink, "[client] max-artifacts-reached: " + maxArtifacts);
                    break;
                }
            }
        }
        return normalizedEvents;
    }

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(message);
        }
    }

    private static String requirePromptText(PromptGenerationResult promptResult) {
        if (promptResult.promptText() != null) {
            return promptResult.promptText();
        }
        PromptGenerationFailure failure = promptResult.failure();
        String code = failure == null ? "PROMPT_GENERATION_FAILED" : failure.code();
        String message = failure == null ? "prompt generation did not produce text" : failure.message();
        throw new ValueErrorException(code + ": " + message);
    }

    private static String buildRequestMetadata(Map<String, Object> scenarioPayload) {
        return String.valueOf(scenarioPayload.getOrDefault("scenario", ""));
    }

    private static String buildPromptInput(String language) {
        return LANGUAGE_EN_US.equalsIgnoreCase(language)
                ? NATURAL_LANGUAGE_PROMPT_INPUT_EN
                : NATURAL_LANGUAGE_PROMPT_INPUT_ZH;
    }

    private static AgentCardQuery resolveAgentCardQuery(Map<String, Object> scenarioPayload) {
        Object queryValue = scenarioPayload.get("agent_card_query");
        if (queryValue instanceof Map<?, ?> queryMap) {
            String name = stringOrDefault(queryMap.get("name"), "RAN Energy Saving Agent");
            String organization = stringOrDefault(queryMap.get("organization"), "Huawei");
            if (name.isBlank()) {
                throw new ValueErrorException("AgentCard query name must not be empty");
            }
            return new AgentCardQuery(name, organization.isBlank() ? "Huawei" : organization);
        }

        String name = stringOrDefault(scenarioPayload.get("agent_card_name"), "RAN Energy Saving Agent");
        String organization = stringOrDefault(scenarioPayload.get("organization"), "Huawei");
        if (name.isBlank()) {
            throw new ValueErrorException("AgentCard query name must not be empty");
        }
        return new AgentCardQuery(name, organization.isBlank() ? "Huawei" : organization);
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String resolved = String.valueOf(value).trim();
        return resolved.isEmpty() ? defaultValue : resolved;
    }

    private record AgentCardQuery(String name, String organization) {}
}
