package net.openan.a2at.sample.server.agentcard;

import java.util.List;
import java.util.Map;

/**
 * Builds sample-owned AgentCard payloads for the server demo.
 *
 * @since 2026-05
 */
public final class ServerSampleAgentCardBuilder {
    static final String TASK_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1";

    static final String NOTIFICATION_T_EXTENSION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1";

    private ServerSampleAgentCardBuilder() {
    }

    public static Map<String, Object> buildAgentCard(String host, int port) {
        return Map.of(
                "name", "SPN Domain Agent",
                "description", "SPN Domain Agent",
                "version", "1.0.0",
                "defaultInputModes", List.of("application/json", "text/plain"),
                "defaultOutputModes", List.of("application/json", "text/plain"),
                "provider", Map.of(
                        "organization", "Huawei",
                        "url", "https://www.huawei.com"),
                "skills", List.of(Map.of(
                        "id", "Incident-Subscription",
                        "name", "Incident reporting",
                        "description", "Mock incident reporting sample skill",
                        "tags", List.of("incident", "reporting"))),
                "capabilities", Map.of(
                        "streaming", true,
                        "pushNotifications", false,
                        "extensions", List.of(
                                Map.of(
                                        "uri", TASK_T_EXTENSION_URI,
                                        "description", "Extension of structured prompt Task-T requests."),
                                Map.of(
                                        "uri", NOTIFICATION_T_EXTENSION_URI,
                                        "description", "Extension of structured prompt Notification-T requests."))),
                "supportedInterfaces", List.of(Map.of(
                        "protocolBinding", "HTTP+JSON",
                        "protocolVersion", "1.0",
                        "url", "http://" + host + ":" + port)));
    }

    public static Map<String, Object> buildRegistrationPayload(String host, int port) {
        return Map.of("agentCards", List.of(buildAgentCard(host, port)));
    }
}


