package net.openan.a2at.sdk.observability;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Classified view of one A2A stream event (protocol-agnostic). */
public record EventInfo(
        String kind,
        @Nullable String taskId,
        @Nullable String taskStatus,
        boolean finalEvent,
        boolean terminal,
        @Nullable Map<String, Object> messageMetadata,
        @Nullable String artifactName) {

    public static final EventInfo UNKNOWN = new EventInfo("unknown", null, null, false, false, null, null);

    public boolean isNegotiationCandidate() {
        return "message".equals(kind);
    }
}
