package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AttributeExtractorsTest {

    private static final String NEGOTIATION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T";

    @Test
    void extensionNameFromUriParsesBase() {
        assertEquals("Negotiation-T", AttributeExtractors.extensionNameFromUri(NEGOTIATION_URI));
        assertEquals("Task-T", AttributeExtractors.extensionNameFromUri(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/sub"));
        assertNull(AttributeExtractors.extensionNameFromUri("https://other.example/ext"));
        assertNull(AttributeExtractors.extensionNameFromUri(null));
    }

    @Test
    void negotiationAttributesExtractedFromMetadata() {
        Map<String, Object> metadata = Map.of(NEGOTIATION_URI, "v1", "negotiationContext",
                Map.of("id", "N001", "round", 3, "maxRounds", 5, "performative", "ACCEPT"));
        Map<String, Object> attrs = AttributeExtractors.negotiationAttributes(metadata);
        assertEquals("N001", attrs.get(A2ATAttributes.ATTR_NEGOTIATION_ID));
        assertEquals(3, attrs.get(A2ATAttributes.ATTR_NEGOTIATION_ROUND));
        assertEquals(5, attrs.get(A2ATAttributes.ATTR_NEGOTIATION_MAX_ROUNDS));
        assertEquals("ACCEPT", attrs.get(A2ATAttributes.ATTR_NEGOTIATION_PERFORMATIVE));
        assertEquals(3, attrs.get(A2ATAttributes.ATTR_NEGOTIATION_TOTAL_ROUNDS));
    }

    @Test
    void nonTerminalPerformativeOmitsTotalRounds() {
        Map<String, Object> attrs = AttributeExtractors.negotiationAttributes(
                Map.of("negotiationContext", Map.of("id", "N1", "round", 1, "performative", "PROPOSE")));
        assertFalse(attrs.containsKey(A2ATAttributes.ATTR_NEGOTIATION_TOTAL_ROUNDS));
        assertTrue(AttributeExtractors.isNegotiationMessage(Map.of(NEGOTIATION_URI, "v1")));
        assertFalse(AttributeExtractors.isNegotiationMessage(Map.of("k", "v")));
    }

    @Test
    void regexAttributesFallBackToDefaultsAndHonorOptOut() {
        Map<String, Object> metadata = Map.of("text", "## 任务类型(Task Type)\n\n配置下发\n## 通知主题\n\nalarm");
        A2ATObservabilityConfig fallback = A2ATObservabilityConfig.fromEnv();
        Map<String, Object> attrs = AttributeExtractors.regexAttributes(metadata, fallback);
        assertEquals("配置下发", attrs.get(A2ATAttributes.ATTR_TASK_TYPE));
        assertEquals("alarm", attrs.get(A2ATAttributes.ATTR_NOTIFICATION_TOPIC));

        Map<String, Object> optedOut = AttributeExtractors.regexAttributes(
                metadata, A2ATObservabilityConfig.fromEnv().toBuilder().taskTypeRegex("").build());
        assertFalse(optedOut.containsKey(A2ATAttributes.ATTR_TASK_TYPE));
    }

    @Test
    void terminalStateNamesNormalize() {
        assertEquals("completed", AttributeExtractors.normalizeTaskStateName("TASK_STATE_COMPLETED"));
        assertEquals("canceled", AttributeExtractors.normalizeTaskStateName("TASK_STATE_CANCELED"));
        assertNull(AttributeExtractors.normalizeTaskStateName(null));
        assertTrue(A2ATAttributes.EVENT_LOG_KINDS.contains("status"));
        assertTrue(A2ATAttributes.EVENT_LOG_KINDS.contains("artifact"));
        assertTrue(A2ATAttributes.EVENT_LOG_KINDS.contains("completed"));
    }
}
