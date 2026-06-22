package net.openan.a2at.sdk.server.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import net.openan.a2at.sdk.server.compliance.ServerPromptComplianceOrchestrator;
import net.openan.a2at.sdk.server.model.PromptComplianceFailure;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;
import org.junit.jupiter.api.Test;

class ServerNegotiationOrchestratorBuilderTest {

    @Test
    void buildCreatesServerRoleOrchestratorThatUsesPromptComplianceForInformationNegotiation() {
        RecordingPromptComplianceOrchestrator complianceOrchestrator =
                new RecordingPromptComplianceOrchestrator(new PromptComplianceResult(
                        false,
                        new PromptComplianceFailure(
                                "slot_validation_error", "Need more information", "slot_validation")));

        RoleBoundNegotiationOrchestrator orchestrator = new ServerNegotiationOrchestratorBuilder()
                .promptComplianceOrchestrator(complianceOrchestrator)
                .store(new InMemoryNegotiationStore())
                .build();

        Map<String, Object> started =
                orchestrator.startNegotiation(NegotiationType.INFORMATION, "Need full task prompt.", Map.of());
        Map<String, Object> context = cast(started.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));

        Map<String, Object> received = orchestrator.receiveNegotiation("latest full task prompt", context);

        assertEquals("latest full task prompt", complianceOrchestrator.lastProcessedPromptText);
        assertEquals("Need more information", received.get("message"));
        assertTrue(booleanValue(received.get("needResponse")));
    }

    @Test
    void buildUsesServerRoleInStartPayload() {
        RoleBoundNegotiationOrchestrator orchestrator = new ServerNegotiationOrchestratorBuilder()
                .promptComplianceOrchestrator(processedPromptText -> new PromptComplianceResult(true, null))
                .store(new InMemoryNegotiationStore())
                .build();

        Map<String, Object> result = orchestrator.startNegotiation(
                NegotiationType.CLARIFICATION, "Please clarify the target.", Map.of("site", "A"));
        Map<String, Object> context = cast(result.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));

        assertEquals("clarification", context.get("negotiationType"));
        assertEquals("in-progress", context.get("status"));
    }

    private static final class RecordingPromptComplianceOrchestrator implements ServerPromptComplianceOrchestrator {
        private final PromptComplianceResult result;
        private String lastProcessedPromptText;

        private RecordingPromptComplianceOrchestrator(PromptComplianceResult result) {
            this.result = result;
        }

        @Override
        public PromptComplianceResult checkTaskPrompt(String processedPromptText) {
            this.lastProcessedPromptText = processedPromptText;
            return result;
        }
    }

    private static Map<String, Object> cast(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> stringValue(entry.getKey()), Map.Entry::getValue));
        }
        throw new AssertionError("Expected map value but was: " + value);
    }

    private static String stringValue(Object value) {
        if (value instanceof String text) {
            return text;
        }
        throw new AssertionError("Expected string value but was: " + value);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new AssertionError("Expected boolean value but was: " + value);
    }
}
