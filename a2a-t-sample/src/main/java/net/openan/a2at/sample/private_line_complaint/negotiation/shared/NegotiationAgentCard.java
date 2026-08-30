package net.openan.a2at.sample.private_line_complaint.negotiation.shared;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;

/** AgentCard payload shared by the negotiation sample client and server. */
public final class NegotiationAgentCard {

    private NegotiationAgentCard() {}

    public static Map<String, Object> build(String host, int port) {
        return Map.of(
                "name", "Transmission Workbench Negotiation Agent",
                "description", "Private-line complaint information negotiation sample",
                "version", "1.0.0",
                "defaultInputModes", List.of("text/plain"),
                "defaultOutputModes", List.of("text/plain"),
                "provider", Map.of("organization", "OpenAN", "url", "https://github.com/project-openan"),
                "skills",
                        List.of(Map.of(
                                "id", "private-line-complaint-negotiation",
                                "name", "Private-line complaint negotiation",
                                "description", "Completes an information negotiation with Accept or Reject.",
                                "tags", List.of("negotiation", "private-line"))),
                "capabilities",
                        Map.of(
                                "streaming", true,
                                "pushNotifications", false,
                                "extensions",
                                        List.of(Map.of(
                                                "uri",
                                                ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI,
                                                "description",
                                                "Negotiation-T information negotiation extension."))),
                "supportedInterfaces",
                        List.of(Map.of(
                                "protocolBinding", "HTTP+JSON",
                                "protocolVersion", "1.0",
                                "url", "http://" + host + ":" + port)));
    }
}
