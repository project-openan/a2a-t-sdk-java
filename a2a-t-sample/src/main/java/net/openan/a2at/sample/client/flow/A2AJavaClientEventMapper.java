package net.openan.a2at.sample.client.flow;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.shared.error.ValueErrorException;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Converts real a2a-java client events into stable sample payload fragments.
 *
 * @since 2026-05
 */
public final class A2AJavaClientEventMapper {

    private A2AJavaClientEventMapper() {
    }

    public static Map<String, Object> toPayload(ClientEvent event) {
        if (event instanceof TaskEvent taskEvent) {
            return Map.of(
                    "status",
                    Map.of("state", taskEvent.getTask().status().state().name()));
        }
        if (event instanceof TaskUpdateEvent taskUpdateEvent) {
            if (taskUpdateEvent.getUpdateEvent() instanceof TaskStatusUpdateEvent statusUpdateEvent) {
                return Map.of("status", Map.of("state", statusUpdateEvent.status().state().name()));
            }
            if (taskUpdateEvent.getUpdateEvent() instanceof TaskArtifactUpdateEvent artifactUpdateEvent) {
                return Map.of("artifact", toArtifactMap(artifactUpdateEvent.artifact()));
            }
        }
        if (event instanceof MessageEvent messageEvent) {
            return Map.of("message", Map.of("parts", toParts(messageEvent.getMessage())));
        }
        throw new ValueErrorException("Unsupported raw stream response: " + event);
    }

    private static Map<String, Object> toArtifactMap(Artifact artifact) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("artifactId", resolveArtifactId(artifact));
        if (artifact.name() != null) {
            payload.put("name", artifact.name());
        }
        if (artifact.description() != null) {
            payload.put("description", artifact.description());
        }
        payload.put("parts", toParts(artifact.parts()));
        if (artifact.metadata() != null && !artifact.metadata().isEmpty()) {
            payload.put("metadata", artifact.metadata());
        }
        return payload;
    }

    private static String resolveArtifactId(Artifact artifact) {
        if (artifact.metadata() != null) {
            Object metadataArtifactId = artifact.metadata().get("artifactId");
            if (metadataArtifactId != null) {
                String resolved = String.valueOf(metadataArtifactId).trim();
                if (!resolved.isEmpty()) {
                    return resolved;
                }
            }
        }
        return artifact.artifactId();
    }

    private static List<Map<String, Object>> toParts(Message message) {
        return toParts(message.parts());
    }

    private static List<Map<String, Object>> toParts(List<Part<?>> parts) {
        return parts.stream().map(A2AJavaClientEventMapper::toPartMap).toList();
    }

    private static Map<String, Object> toPartMap(Part<?> part) {
        if (part instanceof TextPart textPart) {
            return Map.of("text", textPart.text());
        }
        if (part instanceof DataPart dataPart) {
            return Map.of("data", dataPart.data());
        }
        return Map.of("value", String.valueOf(part));
    }
}


