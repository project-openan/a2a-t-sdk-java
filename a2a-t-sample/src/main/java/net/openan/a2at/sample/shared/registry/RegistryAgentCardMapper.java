package net.openan.a2at.sample.shared.registry;

import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentExtension;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;

/**
 * Maps registry-center AgentCard payloads to and from real a2a-java models.
 *
 * @since 2026-05
 */
public final class RegistryAgentCardMapper {

    private RegistryAgentCardMapper() {
    }

    public static AgentCard toA2AJavaAgentCard(Map<String, Object> registryAgentCard) {
        @SuppressWarnings("unchecked")
        Map<String, Object> provider = (Map<String, Object>) registryAgentCard.get("provider");
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) registryAgentCard.get("capabilities");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extensionMaps = (List<Map<String, Object>>) capabilities.getOrDefault("extensions", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skillMaps = (List<Map<String, Object>>) registryAgentCard.getOrDefault("skills", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> interfaceMaps =
                (List<Map<String, Object>>) registryAgentCard.getOrDefault("supportedInterfaces", List.of());
        return new AgentCard(
                stringValue(registryAgentCard.get("name")),
                stringValue(registryAgentCard.get("description")),
                provider == null ? null : new AgentProvider(stringValue(provider.get("organization")), stringValue(provider.get("url"))),
                stringValue(registryAgentCard.get("version")),
                null,
                new AgentCapabilities(
                        Boolean.TRUE.equals(capabilities.get("streaming")),
                        Boolean.TRUE.equals(capabilities.get("pushNotifications")),
                        Boolean.TRUE.equals(capabilities.get("extendedAgentCard")),
                        extensionMaps.stream()
                                .map(extension -> new AgentExtension(
                                        stringValue(extension.get("description")),
                                        Map.of(),
                                        false,
                                        stringValue(extension.get("uri"))))
                                .toList()),
                stringList(registryAgentCard.get("defaultInputModes")),
                stringList(registryAgentCard.get("defaultOutputModes")),
                skillMaps.stream()
                        .map(skill -> new AgentSkill(
                                stringValue(skill.get("id")),
                                stringValue(skill.get("name")),
                                stringValue(skill.get("description")),
                                stringList(skill.get("tags")),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()))
                        .toList(),
                Map.of(),
                List.of(),
                null,
                interfaceMaps.stream()
                        .map(serverInterface -> new AgentInterface(
                                stringValue(serverInterface.get("protocolBinding")),
                                stringValue(serverInterface.get("url")),
                                "",
                                stringValue(serverInterface.get("protocolVersion"))))
                        .toList(),
                List.of());
    }

    public static Map<String, Object> toRegistryRegistrationPayload(AgentCard agentCard) {
        return Map.of("agentCards", List.of(toRegistryAgentCard(agentCard)));
    }

    public static Map<String, Object> toRegistryAgentCard(AgentCard agentCard) {
        return Map.of(
                "name", agentCard.name(),
                "description", agentCard.description(),
                "version", agentCard.version(),
                "defaultInputModes", agentCard.defaultInputModes(),
                "defaultOutputModes", agentCard.defaultOutputModes(),
                "provider", Map.of(
                        "organization", agentCard.provider().organization(),
                        "url", agentCard.provider().url()),
                "skills", agentCard.skills().stream()
                        .map(skill -> Map.of(
                                "id", skill.id(),
                                "name", skill.name(),
                                "description", skill.description(),
                                "tags", skill.tags()))
                        .toList(),
                "capabilities", Map.of(
                        "streaming", agentCard.capabilities().streaming(),
                        "pushNotifications", agentCard.capabilities().pushNotifications(),
                        "extensions", agentCard.capabilities().extensions().stream()
                                .map(extension -> Map.of(
                                        "uri", extension.uri(),
                                        "description", extension.description()))
                                .toList()),
                "supportedInterfaces", agentCard.supportedInterfaces().stream()
                        .map(agentInterface -> Map.of(
                                "protocolBinding", agentInterface.protocolBinding(),
                                "protocolVersion", agentInterface.protocolVersion(),
                                "url", agentInterface.url()))
                        .toList());
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}


