package net.openan.a2at.sample.client.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.client.flow.ClientEventStreamBuffer;
import net.openan.a2at.sample.client.flow.SampleStreamTerminalStateDecider;
import net.openan.a2at.sample.client.prompt.SamplePromptClient;
import net.openan.a2at.sample.client.registry.SampleRegistryClient;
import net.openan.a2at.sample.shared.endpoint.AgentEndpointCache;
import net.openan.a2at.sample.shared.env.SampleEnvironmentPathResolver;
import net.openan.a2at.sample.shared.error.ValueErrorException;
import net.openan.a2at.sample.shared.registry.RegistryAgentCardMapper;
import net.openan.a2at.sdk.client.A2ATClient;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfig;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.MessageSendParams;

/**
 * Default runtime assembly for the client sample entrypoint.
 *
 * @since 2026-05
 */
public final class DefaultSampleClientRuntime implements SampleClientRuntime, A2AJavaClientRuntime {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Path envPath;

    private final HttpClient httpClient;

    private final AgentEndpointCache endpointCache;

    private final SampleRegistryClient registryClient;

    private final SamplePromptClient promptClient;

    public DefaultSampleClientRuntime(Path envPath) {
        this.envPath = envPath;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(resolveTimeoutSeconds(envPath)))
                .build();
        this.endpointCache = new AgentEndpointCache();
        this.registryClient = new DefaultRegistryClient();
        this.promptClient = new A2ATClient(envPath)::generateTaskPrompt;
    }

    public static Path resolveDefaultEnvPath() {
        Path sampleEnvDir = Path.of("src", "main", "resources", "sample", "client");
        return SampleEnvironmentPathResolver.resolve(sampleEnvDir, "client.env", "client.env");
    }

    @Override
    public SampleRegistryClient registryClient() {
        return registryClient;
    }

    @Override
    public SamplePromptClient promptClient() {
        return promptClient;
    }

    @Override
    public AgentEndpointCache endpointCache() {
        return endpointCache;
    }

    @Override
    public void close() {
    }

    @Override
    public Object createStreamingClient(String agentBaseUrl) {
        AgentCard agentCard = new AgentCard(
                "sample-agent",
                "sample-agent",
                new org.a2aproject.sdk.spec.AgentProvider("sample", "https://example.com"),
                "1.0.0",
                null,
                new org.a2aproject.sdk.spec.AgentCapabilities(false, false, false, List.of()),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                null,
                List.of(new AgentInterface("HTTP+JSON", agentBaseUrl, "", "1.0.0")),
                List.of());
        try {
            return Client.builder(agentCard)
                    .withTransport(RestTransport.class, new RestTransportConfig())
                    .build();
        } catch (Exception exception) {
            throw new ValueErrorException("Failed to create a2a-java client: " + exception.getMessage());
        }
    }

    @Override
    public Iterable<ClientEvent> sendMessage(
            Map<String, Object> agentCard,
            MessageSendParams request,
            ClientCallContext callContext,
            Consumer<String> logSink) {
        Object streamingClient = createStreamingClient(agentCard);
        if (!(streamingClient instanceof Client client)) {
            throw new ValueErrorException("Unsupported a2a-java client instance: " + streamingClient);
        }
        ClientEventStreamBuffer eventStreamBuffer = new ClientEventStreamBuffer();
        try {
            client.sendMessage(
                    request,
                    List.of((event, ignored) -> {
                        eventStreamBuffer.append(event);
                        if (SampleStreamTerminalStateDecider.isTerminal(event)) {
                            eventStreamBuffer.close();
                        }
                    }),
                    eventStreamBuffer::fail,
                    callContext);
            return eventStreamBuffer;
        } catch (Exception exception) {
            client.close();
            throw new ValueErrorException("A2A message:stream request failed: " + exception.getMessage());
        }
    }

    private final class DefaultRegistryClient implements SampleRegistryClient {
        @Override
        public Map<String, Object> queryAgentCardByName(String name, String organization) {
            String directAgentCardUrl = resolveDirectAgentCardUrl(envPath);
            if (!directAgentCardUrl.isBlank()) {
                return queryAgentCardFromServerRoot(directAgentCardUrl);
            }
            try {
                String encodedName = encodePathSegment(name);
                String encodedOrganization = encodePathSegment(organization);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(resolveRegistryBaseUrl(envPath)
                                + "/rest/v1/registry-center/agent-cards/"
                                + encodedOrganization
                                + "/"
                                + encodedName))
                        .timeout(Duration.ofSeconds(resolveTimeoutSeconds(envPath)))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new ValueErrorException(
                            "AgentCard query by name failed: status=" + response.statusCode() + " body=" + response.body());
                }
                Map<String, Object> payload = parseObject(response.body());
                Object agentCards = payload.get("agentCards");
                if (agentCards instanceof List<?> cards && !cards.isEmpty() && cards.get(0) instanceof Map<?, ?> firstCard) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> card = (Map<String, Object>) firstCard;
                    return card;
                }
                throw new ValueErrorException("Registry query by name returned no AgentCard entries");
            } catch (IOException | InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ValueErrorException("Registry query by name failed: " + exception.getMessage());
            }
        }

        private Map<String, Object> queryAgentCardFromServerRoot(String agentCardUrl) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(agentCardUrl))
                        .timeout(Duration.ofSeconds(resolveTimeoutSeconds(envPath)))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new ValueErrorException(
                            "AgentCard query from server root failed: status="
                                    + response.statusCode()
                                    + " body="
                                    + response.body());
                }
                return parseObject(response.body());
            } catch (IOException exception) {
                throw new ValueErrorException("AgentCard query from server root failed: " + exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ValueErrorException("AgentCard query from server root failed: " + exception.getMessage());
            }
        }
    }

    static String encodePathSegment(String value) {
        try {
            String encoded = new URI(null, null, "/" + value, null).getRawPath();
            return encoded.startsWith("/") ? encoded.substring(1) : encoded;
        } catch (java.net.URISyntaxException exception) {
            throw new ValueErrorException("Unsupported registry path segment: " + value);
        }
    }

    private Object createStreamingClient(Map<String, Object> agentCard) {
        AgentCard realAgentCard = RegistryAgentCardMapper.toA2AJavaAgentCard(agentCard);
        try {
            return Client.builder(realAgentCard)
                    .withTransport(RestTransport.class, new RestTransportConfig())
                    .build();
        } catch (Exception exception) {
            throw new ValueErrorException("Failed to create a2a-java client: " + exception.getMessage());
        }
    }

    private static String resolveRegistryBaseUrl(Path envPath) {
        Map<String, String> envValues = parseEnvFile(envPath);
        String explicitUrl = envValues.get("A2A_REGISTRY_URL");
        if (explicitUrl != null && !explicitUrl.isBlank()) {
            return explicitUrl;
        }
        String host = envValues.getOrDefault("REGISTRY_CENTER_HOST", "127.0.0.1");
        String port = envValues.getOrDefault("REGISTRY_CENTER_PORT", "5001");
        return "http://" + host + ":" + Integer.parseInt(port);
    }

    private static String resolveDirectAgentCardUrl(Path envPath) {
        Map<String, String> envValues = parseEnvFile(envPath);
        String host = envValues.get("A2AT_SAMPLE_HOST");
        String port = envValues.get("A2AT_SAMPLE_PORT");
        if (host != null && !host.isBlank() && port != null && !port.isBlank()) {
            return "http://" + host + ":" + Integer.parseInt(port) + "/";
        }
        return "";
    }

    private static long resolveTimeoutSeconds(Path envPath) {
        Map<String, String> envValues = parseEnvFile(envPath);
        String rawValue = envValues.getOrDefault("A2AT_LLM_TIMEOUT_SECONDS", "60");
        return Long.parseLong(rawValue);
    }

    private static Map<String, String> parseEnvFile(Path envPath) {
        try {
            List<String> lines = java.nio.file.Files.exists(envPath) ? java.nio.file.Files.readAllLines(envPath) : List.of();
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int separatorIndex = trimmed.indexOf('=');
                values.put(trimmed.substring(0, separatorIndex).trim(), trimmed.substring(separatorIndex + 1).trim());
            }
            return values;
        } catch (IOException exception) {
            throw new ValueErrorException("Failed to read env file: " + envPath);
        }
    }

    private static Map<String, Object> parseObject(String text) throws IOException {
        return OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {
        });
    }
}


