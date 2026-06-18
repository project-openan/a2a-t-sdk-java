package net.openan.a2at.sample.server.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.shared.error.ValueErrorException;

/**
 * Registry registration support for the server sample.
 *
 * @since 2026-05
 */
public final class ServerRegistryClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ServerRegistryClient() {
    }

    public static String resolveRegistryBaseUrl(Path envPath) {
        Map<String, String> envValues = parseEnvFile(envPath);
        String host = envValues.getOrDefault("REGISTRY_CENTER_HOST", "127.0.0.1");
        String port = envValues.getOrDefault("REGISTRY_CENTER_PORT", "5001");
        return "http://" + host + ":" + Integer.parseInt(port);
    }

    public static Map<String, Object> registerAgentCard(
            Map<String, Object> registrationPayload,
            String registryBaseUrl,
            ServerRegistryTransport transport,
            Consumer<String> logSink) {
        String normalizedBaseUrl = registryBaseUrl.endsWith("/")
                ? registryBaseUrl.substring(0, registryBaseUrl.length() - 1)
                : registryBaseUrl;
        ServerRegistryResponse response = transport.post(
                normalizedBaseUrl + "/rest/v1/registry-center/agent-cards",
                registrationPayload);
        if (response.statusCode() == 201) {
            return Map.of("status", "success", "message", "Agent card registered successfully");
        }
        if (logSink != null) {
            logSink.accept("[registry-client] register failed: status_code=" + response.statusCode());
        }
        return Map.of(
                "status", "failed",
                "message", "Unexpected status code: " + response.statusCode(),
                "error", response.jsonBody().isEmpty() ? response.text() : response.jsonBody());
    }

    public static ServerRegistryTransport httpTransport() {
        return (url, payload) -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                byte[] body = OBJECT_MAPPER.writeValueAsBytes(payload);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(body);
                }
                int statusCode = connection.getResponseCode();
                String text = readResponseBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
                Map<String, Object> jsonBody = parseJsonBody(text);
                return new ServerRegistryResponse(statusCode, text, jsonBody);
            } catch (IOException exception) {
                return new ServerRegistryResponse(599, exception.getMessage(), Map.of());
            }
        };
    }

    private static String readResponseBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> parseJsonBody(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    public static Map<String, String> parseEnvFile(Path envPath) {
        if (envPath == null || !Files.exists(envPath)) {
            return Map.of();
        }
        try {
            List<String> lines = Files.readAllLines(envPath);
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int separatorIndex = trimmed.indexOf('=');
                String key = trimmed.substring(0, separatorIndex).trim();
                String value = trimmed.substring(separatorIndex + 1).trim();
                if (value.isEmpty()) {
                    continue;
                }
                values.put(key, value);
            }
            return values;
        } catch (IOException exception) {
            throw new ValueErrorException("Failed to read env file: " + envPath);
        }
    }
}


