package net.openan.a2at.sdk.observability;

import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Observability configuration: explicit builder > .env map > system env > spec defaults. */
public record A2ATObservabilityConfig(
        boolean enabled,
        boolean traceEnabled,
        boolean metricEnabled,
        boolean logEnabled,
        @Nullable String endpoint,
        String protocol,
        @Nullable String serviceName,
        @Nullable String agentRole,
        @Nullable String taskTypeRegex,
        @Nullable String notificationTopicRegex,
        @Nullable String authorizationPolicyOperationTypeRegex,
        boolean extractRequest,
        boolean extractResponse,
        boolean payloadLogEnabled,
        int payloadLogMaxLength,
        @Nullable UnaryOperator<String> payloadRedactor) {

    private static final Logger LOG = LoggerFactory.getLogger(A2ATObservabilityConfig.class);

    public static final String ENV_ENABLED = "A2AT_OBSERVABILITY_ENABLED";
    public static final String ENV_TRACE = "A2AT_TRACE_ENABLED";
    public static final String ENV_METRIC = "A2AT_METRIC_ENABLED";
    public static final String ENV_LOG = "A2AT_LOG_ENABLED";
    public static final String ENV_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";
    public static final String ENV_PROTOCOL = "A2AT_EXPORTER_PROTOCOL";
    public static final String ENV_SERVICE_NAME = "A2AT_SERVICE_NAME";
    public static final String ENV_OTEL_SERVICE_NAME = "OTEL_SERVICE_NAME";
    public static final String ENV_AGENT_ROLE = "A2AT_AGENT_ROLE";
    public static final String ENV_EXTRACT_REQUEST = "A2AT_EXTRACT_REQUEST";
    public static final String ENV_EXTRACT_RESPONSE = "A2AT_EXTRACT_RESPONSE";
    public static final String ENV_PAYLOAD_LOG_ENABLED = "A2AT_LOGS_PAYLOAD_ENABLED";
    public static final String ENV_PAYLOAD_LOG_MAX_LENGTH = "A2AT_LOGS_PAYLOAD_MAX_LENGTH";
    public static final String ENV_CONSOLE_EXPORTER = "A2AT_CONSOLE_EXPORTER";

    public static final String DEFAULT_PROTOCOL = "grpc";
    public static final int DEFAULT_PAYLOAD_LOG_MAX_LENGTH = 4096;
    public static final String DEFAULT_SERVICE_NAME = "a2a-t-agent";

    public static A2ATObservabilityConfig fromEnv() {
        return fromMap(Map.of());
    }

    /** @param values .env file values (take precedence over system env); missing keys fall back to env then defaults */
    public static A2ATObservabilityConfig fromMap(Map<String, String> values) {
        Builder b = new Builder();
        b.enabled = bool(values, ENV_ENABLED, true);
        b.traceEnabled = bool(values, ENV_TRACE, true);
        b.metricEnabled = bool(values, ENV_METRIC, true);
        b.logEnabled = bool(values, ENV_LOG, true);
        b.endpoint = str(values, ENV_ENDPOINT);
        b.protocol = normalizeProtocol(str(values, ENV_PROTOCOL));
        String fromSdkKey = str(values, ENV_SERVICE_NAME);
        b.serviceName = fromSdkKey != null ? fromSdkKey : str(values, ENV_OTEL_SERVICE_NAME);
        b.extractRequest = bool(values, ENV_EXTRACT_REQUEST, false);
        b.extractResponse = bool(values, ENV_EXTRACT_RESPONSE, false);
        b.payloadLogEnabled = bool(values, ENV_PAYLOAD_LOG_ENABLED, false);
        b.payloadLogMaxLength = intOr(values, ENV_PAYLOAD_LOG_MAX_LENGTH, DEFAULT_PAYLOAD_LOG_MAX_LENGTH);
        return b.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.enabled = enabled;
        b.traceEnabled = traceEnabled;
        b.metricEnabled = metricEnabled;
        b.logEnabled = logEnabled;
        b.endpoint = endpoint;
        b.protocol = protocol;
        b.serviceName = serviceName;
        b.agentRole = resolveAgentRole();
        b.taskTypeRegex = taskTypeRegex;
        b.notificationTopicRegex = notificationTopicRegex;
        b.authorizationPolicyOperationTypeRegex = authorizationPolicyOperationTypeRegex;
        b.extractRequest = extractRequest;
        b.extractResponse = extractResponse;
        b.payloadLogEnabled = payloadLogEnabled;
        b.payloadLogMaxLength = payloadLogMaxLength;
        b.payloadRedactor = payloadRedactor;
        return b;
    }

    /** Agent role: env-only opt-in (no builder default). */
    public @Nullable String resolveAgentRole() {
        String role = System.getProperty(ENV_AGENT_ROLE, System.getenv(ENV_AGENT_ROLE));
        return role == null || role.isBlank() ? null : role.trim();
    }

    static String normalizeProtocol(@Nullable String raw) {
        String value = (raw == null ? DEFAULT_PROTOCOL : raw).trim().toLowerCase(Locale.ROOT);
        if (value.equals("grpc")) {
            return "grpc";
        }
        if (value.equals("http") || value.equals("http/protobuf")) {
            return "http/protobuf";
        }
        LOG.warn("a2at: invalid exporter protocol {}; falling back to grpc", raw);
        return DEFAULT_PROTOCOL;
    }

    private static @Nullable String str(Map<String, String> values, String key) {
        String v = values.get(key);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        String env = System.getenv(key);
        return env == null || env.isBlank() ? null : env.trim();
    }

    private static boolean bool(Map<String, String> values, String key, boolean def) {
        String v = str(values, key);
        if (v == null) {
            return def;
        }
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    private static int intOr(Map<String, String> values, String key, int def) {
        String v = str(values, key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static final class Builder {
        private boolean enabled = true;
        private boolean traceEnabled = true;
        private boolean metricEnabled = true;
        private boolean logEnabled = true;
        private @Nullable String endpoint;
        private String protocol = DEFAULT_PROTOCOL;
        private @Nullable String serviceName;
        private @Nullable String agentRole;
        private @Nullable String taskTypeRegex;
        private @Nullable String notificationTopicRegex;
        private @Nullable String authorizationPolicyOperationTypeRegex;
        private boolean extractRequest;
        private boolean extractResponse;
        private boolean payloadLogEnabled;
        private int payloadLogMaxLength = DEFAULT_PAYLOAD_LOG_MAX_LENGTH;
        private @Nullable UnaryOperator<String> payloadRedactor;

        public Builder enabled(boolean v) {
            this.enabled = v;
            return this;
        }

        public Builder traceEnabled(boolean v) {
            this.traceEnabled = v;
            return this;
        }

        public Builder metricEnabled(boolean v) {
            this.metricEnabled = v;
            return this;
        }

        public Builder logEnabled(boolean v) {
            this.logEnabled = v;
            return this;
        }

        public Builder endpoint(@Nullable String v) {
            this.endpoint = v;
            return this;
        }

        public Builder protocol(String v) {
            this.protocol = normalizeProtocol(v);
            return this;
        }

        public Builder serviceName(@Nullable String v) {
            this.serviceName = v;
            return this;
        }

        public Builder agentRole(@Nullable String v) {
            this.agentRole = v;
            return this;
        }

        public Builder taskTypeRegex(@Nullable String v) {
            this.taskTypeRegex = v;
            return this;
        }

        public Builder notificationTopicRegex(@Nullable String v) {
            this.notificationTopicRegex = v;
            return this;
        }

        public Builder authorizationPolicyOperationTypeRegex(@Nullable String v) {
            this.authorizationPolicyOperationTypeRegex = v;
            return this;
        }

        public Builder extractRequest(boolean v) {
            this.extractRequest = v;
            return this;
        }

        public Builder extractResponse(boolean v) {
            this.extractResponse = v;
            return this;
        }

        public Builder payloadLogEnabled(boolean v) {
            this.payloadLogEnabled = v;
            return this;
        }

        public Builder payloadLogMaxLength(int v) {
            this.payloadLogMaxLength = v;
            return this;
        }

        public Builder payloadRedactor(@Nullable UnaryOperator<String> v) {
            this.payloadRedactor = v;
            return this;
        }

        public A2ATObservabilityConfig build() {
            if (payloadLogMaxLength <= 0) {
                throw new IllegalArgumentException("payloadLogMaxLength must be positive");
            }
            String name = serviceName != null ? serviceName : System.getenv(ENV_SERVICE_NAME);
            if (name == null || name.isBlank()) {
                name = System.getenv(ENV_OTEL_SERVICE_NAME);
            }
            return new A2ATObservabilityConfig(
                    enabled, traceEnabled, metricEnabled, logEnabled, endpoint, protocol,
                    name == null || name.isBlank() ? DEFAULT_SERVICE_NAME : name.trim(),
                    agentRole, taskTypeRegex, notificationTopicRegex, authorizationPolicyOperationTypeRegex,
                    extractRequest, extractResponse, payloadLogEnabled, payloadLogMaxLength, payloadRedactor);
        }
    }
}
