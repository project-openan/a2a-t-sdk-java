package net.openan.a2at.sdk.core.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelemetryHookTest {

    @Test
    void noOpHookAbsorbsEverything() {
        TelemetryOperation operation = TelemetryHook.NO_OP.startSdkOperation("client", "getPrompts");
        assertSame(TelemetryOperation.NO_OP, operation);
        assertDoesNotThrow(() -> {
            operation.setAttribute("k", "v");
            operation.markSuccess();
            operation.markError(new RuntimeException("x"));
            operation.close();
            TelemetryHook.NO_OP.reportNegotiationRounds("n1", "accept", 3);
        });
    }

    @Test
    void customHookReceivesCalls() {
        List<String> started = new ArrayList<>();
        List<String> rounds = new ArrayList<>();
        TelemetryHook hook = new TelemetryHook() {
            @Override
            public TelemetryOperation startSdkOperation(String role, String method) {
                started.add(role + "." + method);
                return TelemetryOperation.NO_OP;
            }

            @Override
            public void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds) {
                rounds.add(negotiationId + ":" + outcome + ":" + totalRounds);
            }
        };
        hook.startSdkOperation("server", "checkTaskPrompt").close();
        hook.reportNegotiationRounds("N1", "reject", 2);
        assertTrue(started.contains("server.checkTaskPrompt"));
        assertTrue(rounds.contains("N1:reject:2"));
    }
}
